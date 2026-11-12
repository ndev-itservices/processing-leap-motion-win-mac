/*
 * leapcheck - tiny LeapC diagnostic. Connects to the Ultraleap tracking service, reports the
 * device and prints tracking frames for N seconds. Loads libLeapC at runtime (dlopen), so it
 * works with both the system and the user installation.
 * Build: ./build_leapcheck.sh      Run: bin/leapcheck [seconds]
 * Author: Tiago Martins
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <dlfcn.h>
#include "../native/LeapC.h"

typedef eLeapRS (*pf_Create)(const LEAP_CONNECTION_CONFIG*, LEAP_CONNECTION*);
typedef eLeapRS (*pf_Open)(LEAP_CONNECTION);
typedef eLeapRS (*pf_Poll)(LEAP_CONNECTION, uint32_t, LEAP_CONNECTION_MESSAGE*);
typedef eLeapRS (*pf_Info)(LEAP_CONNECTION, LEAP_CONNECTION_INFO*);
typedef eLeapRS (*pf_OpenDev)(LEAP_DEVICE_REF, LEAP_DEVICE*);
typedef eLeapRS (*pf_DevInfo)(LEAP_DEVICE, LEAP_DEVICE_INFO*);
typedef void    (*pf_CloseDev)(LEAP_DEVICE);
typedef void    (*pf_Destroy)(LEAP_CONNECTION);
typedef eLeapRS (*pf_Version)(LEAP_CONNECTION, eLeapVersionPart, LEAP_VERSION*);

static void* load(void) {
    const char* home = getenv("HOME");
    char buf[1024];
    const char* c[] = {
        "/Applications/Ultraleap Hand Tracking.app/Contents/LeapSDK/lib/libLeapC.6.dylib",
        "/Applications/Ultraleap Hand Tracking.app/Contents/LeapSDK/lib/libLeapC.dylib",
        "/Library/Application Support/Ultraleap/LeapSDK/lib/libLeapC.dylib", NULL };
    const char* env = getenv("LEAPC_PATH");
    void* h = env ? dlopen(env, RTLD_NOW) : NULL;
    for (int i = 0; !h && c[i]; i++) h = dlopen(c[i], RTLD_NOW);
    if (!h && home) {
        snprintf(buf, sizeof buf, "%s/Applications/Ultraleap Hand Tracking.app/Contents/LeapSDK/lib/libLeapC.6.dylib", home);
        h = dlopen(buf, RTLD_NOW);
    }
    return h;
}

int main(int argc, char** argv) {
    int secs = argc > 1 ? atoi(argv[1]) : 10;
    void* dl = load();
    if (!dl) { printf("libLeapC not found: Ultraleap Hand Tracking is not installed (%s)\n", dlerror()); return 2; }
    pf_Create Create = (pf_Create)dlsym(dl, "LeapCreateConnection");
    pf_Open Open = (pf_Open)dlsym(dl, "LeapOpenConnection");
    pf_Poll Poll = (pf_Poll)dlsym(dl, "LeapPollConnection");
    pf_Info Info = (pf_Info)dlsym(dl, "LeapGetConnectionInfo");
    pf_OpenDev OpenDev = (pf_OpenDev)dlsym(dl, "LeapOpenDevice");
    pf_DevInfo DevInfo = (pf_DevInfo)dlsym(dl, "LeapGetDeviceInfo");
    pf_CloseDev CloseDev = (pf_CloseDev)dlsym(dl, "LeapCloseDevice");
    pf_Destroy Destroy = (pf_Destroy)dlsym(dl, "LeapDestroyConnection");
    pf_Version Version = (pf_Version)dlsym(dl, "LeapGetVersion");
    if (!Create || !Open || !Poll || !Info || !OpenDev || !DevInfo || !CloseDev || !Destroy) { printf("libLeapC symbols missing\n"); return 2; }

    LEAP_CONNECTION conn = NULL;
    if (Create(NULL, &conn) != eLeapRS_Success || Open(conn) != eLeapRS_Success) { printf("cannot open LeapC connection\n"); return 3; }
    LEAP_VERSION v = {0};
    if (Version && Version(conn, eLeapVersionPart_ClientLibrary, &v) == eLeapRS_Success) printf("LeapC client %d.%d.%d\n", v.major, v.minor, v.patch);

    time_t t0 = time(NULL); int connected = 0, devices = 0; long frames = 0; int maxHands = 0; long long lastPrinted = -1;
    while (time(NULL) - t0 < secs) {
        LEAP_CONNECTION_MESSAGE msg; memset(&msg, 0, sizeof msg);
        eLeapRS r = Poll(conn, 200, &msg);
        if (r == eLeapRS_Timeout) {
            LEAP_CONNECTION_INFO ci = { sizeof(ci) };
            if (!connected && Info(conn, &ci) == eLeapRS_Success && ci.status == eLeapConnectionStatus_NotRunning) {
                printf("tracking service NOT RUNNING (start 'Ultraleap Hand Tracking' or leap_service.sh start)\n"); break;
            }
            continue;
        }
        if (r != eLeapRS_Success) { usleep(20000); continue; }
        switch (msg.type) {
            case eLeapEventType_Connection: connected = 1; printf("connected to the tracking service\n"); break;
            case eLeapEventType_ConnectionLost: connected = 0; printf("connection lost\n"); break;
            case eLeapEventType_Device: {
                LEAP_DEVICE d; char serial[128] = {0};
                LEAP_DEVICE_INFO di; memset(&di, 0, sizeof di); di.size = sizeof di; di.serial = serial; di.serial_length = sizeof serial;
                if (OpenDev(msg.device_event->device, &d) == eLeapRS_Success) {
                    if (DevInfo(d, &di) == eLeapRS_Success)
                        printf("device: pid=0x%04x serial=%s status=0x%x hfov=%.1f vfov=%.1f range=%umm baseline=%umm\n",
                               di.pid, serial, di.status, di.h_fov * 57.2958f, di.v_fov * 57.2958f, di.range / 1000, di.baseline / 1000);
                    CloseDev(d);
                }
                devices++; break; }
            case eLeapEventType_DeviceLost: printf("device lost\n"); if (devices > 0) devices--; break;
            case eLeapEventType_DeviceFailure: printf("device failure status=0x%x\n", msg.device_failure_event->status); break;
            case eLeapEventType_Tracking: {
                const LEAP_TRACKING_EVENT* t = msg.tracking_event; frames++;
                if ((int)t->nHands > maxHands) maxHands = t->nHands;
                long long sec = (long long)(time(NULL) - t0);
                if (sec != lastPrinted) { lastPrinted = sec;
                    printf("  frame %lld fps=%.0f hands=%u", (long long)t->tracking_frame_id, t->framerate, t->nHands);
                    for (uint32_t h = 0; h < t->nHands; h++) printf("  [%s palm %.0f,%.0f,%.0f grab %.2f]", t->pHands[h].type == eLeapHandType_Left ? "L" : "R",
                        t->pHands[h].palm.position.x, t->pHands[h].palm.position.y, t->pHands[h].palm.position.z, t->pHands[h].grab_strength);
                    printf("\n"); }
                break; }
            default: break;
        }
    }
    printf("RESULT: service=%s devices=%d frames=%ld maxHands=%d\n", connected ? "connected" : "not connected", devices, frames, maxHands);
    Destroy(conn);
    return connected ? 0 : 1;
}
