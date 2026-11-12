/*
 * leapjni.c - JNI bridge between com.leapmotion.leap.LeapJNI (Java) and the
 * Ultraleap LeapC client library (Gemini 5.x / Hyperion 6.x).
 *
 * Part of "Leap Motion for Processing" (ARM fork) - https://github.com/nok/leap-motion-processing fork
 * Author of the fork: Tiago Martins
 * Licence: MIT (see LICENSE.txt)
 *
 * Design
 * ------
 *  - libLeapC is loaded at runtime with dlopen(), so this library has no
 *    link-time dependency on the Ultraleap installation path.
 *  - A background thread services LeapPollConnection() and caches the latest
 *    tracking frame, camera images, device info and connection state.
 *  - Java threads block in nWait() until something new arrives, then copy the
 *    cached data with nReadFrame()/nReadImages().
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdbool.h>
#include <time.h>
#include <errno.h>

#include "LeapC.h"

/* ------------------------------------------------------------------------- */
/* Portability layer: dynamic loading, threads, mutex/condvar, sleep, clock  */

#ifdef _WIN32
#  define WIN32_LEAN_AND_MEAN
#  include <windows.h>
typedef HMODULE lj_dl_t;
typedef HANDLE lj_thread_t;
typedef CRITICAL_SECTION lj_mutex_t;
typedef CONDITION_VARIABLE lj_cond_t;
static lj_dl_t lj_dlopen(const char* p) { return LoadLibraryA(p); }
static void* lj_dlsym(lj_dl_t h, const char* n) { return (void*)GetProcAddress(h, n); }
static const char* lj_dlerror(void) { static char b[64]; snprintf(b, sizeof b, "LoadLibrary error %lu", (unsigned long)GetLastError()); return b; }
static void lj_mutex_init(lj_mutex_t* m) { InitializeCriticalSection(m); }
static void lj_mutex_destroy(lj_mutex_t* m) { DeleteCriticalSection(m); }
static void lj_lock(lj_mutex_t* m) { EnterCriticalSection(m); }
static void lj_unlock(lj_mutex_t* m) { LeaveCriticalSection(m); }
static void lj_cond_init(lj_cond_t* c) { InitializeConditionVariable(c); }
static void lj_cond_destroy(lj_cond_t* c) { (void)c; }
static void lj_cond_broadcast(lj_cond_t* c) { WakeAllConditionVariable(c); }
static void lj_cond_timedwait_ms(lj_cond_t* c, lj_mutex_t* m, int ms) { SleepConditionVariableCS(c, m, (DWORD)ms); }
static void lj_sleep_ms(int ms) { Sleep((DWORD)ms); }
static int64_t lj_now_us(void) { FILETIME ft; GetSystemTimeAsFileTime(&ft); ULARGE_INTEGER u; u.LowPart = ft.dwLowDateTime; u.HighPart = ft.dwHighDateTime; return (int64_t)(u.QuadPart / 10); }
typedef DWORD (WINAPI *lj_thread_fn)(LPVOID);
static int lj_thread_create(lj_thread_t* t, lj_thread_fn fn, void* arg) { *t = CreateThread(NULL, 0, fn, arg, 0, NULL); return *t ? 0 : -1; }
static void lj_thread_join(lj_thread_t t) { WaitForSingleObject(t, INFINITE); CloseHandle(t); }
#  define LJ_THREAD_RET DWORD WINAPI
#  define LJ_THREAD_RETURN return 0
#  define LJ_PATHSEP "\\"
#else
#  include <dlfcn.h>
#  include <pthread.h>
#  include <unistd.h>
#  include <sys/time.h>
typedef void* lj_dl_t;
typedef pthread_t lj_thread_t;
typedef pthread_mutex_t lj_mutex_t;
typedef pthread_cond_t lj_cond_t;
static lj_dl_t lj_dlopen(const char* p) { return dlopen(p, RTLD_NOW | RTLD_LOCAL); }
static void* lj_dlsym(lj_dl_t h, const char* n) { return dlsym(h, n); }
static const char* lj_dlerror(void) { const char* e = dlerror(); return e ? e : "unknown"; }
static void lj_mutex_init(lj_mutex_t* m) { pthread_mutex_init(m, NULL); }
static void lj_mutex_destroy(lj_mutex_t* m) { pthread_mutex_destroy(m); }
static void lj_lock(lj_mutex_t* m) { pthread_mutex_lock(m); }
static void lj_unlock(lj_mutex_t* m) { pthread_mutex_unlock(m); }
static void lj_cond_init(lj_cond_t* c) { pthread_cond_init(c, NULL); }
static void lj_cond_destroy(lj_cond_t* c) { pthread_cond_destroy(c); }
static void lj_cond_broadcast(lj_cond_t* c) { pthread_cond_broadcast(c); }
static void lj_cond_timedwait_ms(lj_cond_t* c, lj_mutex_t* m, int ms) {
    struct timeval now; gettimeofday(&now, NULL);
    struct timespec ts;
    int64_t ns = (int64_t)now.tv_usec * 1000 + (int64_t)ms * 1000000;
    ts.tv_sec = now.tv_sec + ns / 1000000000; ts.tv_nsec = ns % 1000000000;
    pthread_cond_timedwait(c, m, &ts);
}
static void lj_sleep_ms(int ms) { usleep((useconds_t)ms * 1000); }
static int64_t lj_now_us(void) { struct timeval tv; gettimeofday(&tv, NULL); return (int64_t)tv.tv_sec * 1000000 + tv.tv_usec; }
typedef void* (*lj_thread_fn)(void*);
static int lj_thread_create(lj_thread_t* t, lj_thread_fn fn, void* arg) { return pthread_create(t, NULL, fn, arg); }
static void lj_thread_join(lj_thread_t t) { pthread_join(t, NULL); }
#  define LJ_THREAD_RET void*
#  define LJ_THREAD_RETURN return NULL
#  define LJ_PATHSEP "/"
#endif

/* ------------------------------------------------------------------------- */
/* Layout shared with Java (keep in sync with com.leapmotion.leap.LeapJNI)   */

#define LJ_HAND_STRIDE   272
#define LJ_MAX_HANDS     4
#define LJ_META_LEN      8

/* pending event bits */
#define LJ_EV_FRAME        0x01
#define LJ_EV_CONNECTION   0x02
#define LJ_EV_DEVICE       0x04
#define LJ_EV_IMAGE        0x08
#define LJ_EV_POLICY       0x10
#define LJ_EV_LOG          0x20
#define LJ_EV_TRACKINGMODE 0x40

/* hand block offsets */
#define H_ID 0
#define H_TYPE 1
#define H_CONF 2
#define H_VISIBLE 3
#define H_PINCH_DIST 4
#define H_GRAB_ANGLE 5
#define H_PINCH 6
#define H_GRAB 7
#define H_FLAGS 8
#define H_PALM_POS 9
#define H_PALM_STAB 12
#define H_PALM_VEL 15
#define H_PALM_NORMAL 18
#define H_PALM_WIDTH 21
#define H_PALM_DIR 22
#define H_PALM_QUAT 25
#define H_DIGITS 29
#define D_STRIDE 46
#define D_ID 0
#define D_EXTENDED 1
#define D_BONES 2
#define B_STRIDE 11
#define H_ARM 259   /* 29 + 5*46 */

#define LJ_ALL_POLICY_MASK (eLeapPolicyFlag_BackgroundFrames | eLeapPolicyFlag_Images | \
                            eLeapPolicyFlag_OptimizeHMD | eLeapPolicyFlag_AllowPauseResume | \
                            eLeapPolicyFlag_MapPoints | eLeapPolicyFlag_OptimizeScreenTop)

/* ------------------------------------------------------------------------- */
/* LeapC function table (resolved with dlsym)                                */

typedef eLeapRS (LEAP_CALL *pf_CreateConnection)(const LEAP_CONNECTION_CONFIG*, LEAP_CONNECTION*);
typedef eLeapRS (LEAP_CALL *pf_OpenConnection)(LEAP_CONNECTION);
typedef void    (*pf_CloseConnection)(LEAP_CONNECTION);
typedef void    (*pf_DestroyConnection)(LEAP_CONNECTION);
typedef eLeapRS (LEAP_CALL *pf_PollConnection)(LEAP_CONNECTION, uint32_t, LEAP_CONNECTION_MESSAGE*);
typedef eLeapRS (LEAP_CALL *pf_GetConnectionInfo)(LEAP_CONNECTION, LEAP_CONNECTION_INFO*);
typedef eLeapRS (LEAP_CALL *pf_SetPolicyFlags)(LEAP_CONNECTION, uint64_t, uint64_t);
typedef eLeapRS (LEAP_CALL *pf_SetTrackingMode)(LEAP_CONNECTION, eLeapTrackingMode);
typedef eLeapRS (LEAP_CALL *pf_GetTrackingMode)(LEAP_CONNECTION);
typedef eLeapRS (LEAP_CALL *pf_SetAllocator)(LEAP_CONNECTION, const LEAP_ALLOCATOR*);
typedef eLeapRS (LEAP_CALL *pf_OpenDevice)(LEAP_DEVICE_REF, LEAP_DEVICE*);
typedef void    (*pf_CloseDevice)(LEAP_DEVICE);
typedef eLeapRS (LEAP_CALL *pf_GetDeviceInfo)(LEAP_DEVICE, LEAP_DEVICE_INFO*);
typedef eLeapRS (LEAP_CALL *pf_GetDeviceList)(LEAP_CONNECTION, LEAP_DEVICE_REF*, uint32_t*);
typedef eLeapRS (LEAP_CALL *pf_SaveConfigValue)(LEAP_CONNECTION, const char*, const LEAP_VARIANT*, uint32_t*);
typedef eLeapRS (LEAP_CALL *pf_GetVersion)(LEAP_CONNECTION, eLeapVersionPart, LEAP_VERSION*);
typedef int64_t (LEAP_CALL *pf_GetNow)(void);
typedef LEAP_VECTOR (LEAP_CALL *pf_RectilinearToPixel)(LEAP_CONNECTION, eLeapPerspectiveType, LEAP_VECTOR);
typedef LEAP_VECTOR (LEAP_CALL *pf_PixelToRectilinear)(LEAP_CONNECTION, eLeapPerspectiveType, LEAP_VECTOR);
typedef void (LEAP_CALL *pf_ExtrinsicCameraMatrix)(LEAP_CONNECTION, eLeapPerspectiveType, float*);

typedef struct {
    pf_CreateConnection  CreateConnection;
    pf_OpenConnection    OpenConnection;
    pf_CloseConnection   CloseConnection;
    pf_DestroyConnection DestroyConnection;
    pf_PollConnection    PollConnection;
    pf_GetConnectionInfo GetConnectionInfo;
    pf_SetPolicyFlags    SetPolicyFlags;
    pf_SetTrackingMode   SetTrackingMode;
    pf_GetTrackingMode   GetTrackingMode;
    pf_SetAllocator      SetAllocator;
    pf_OpenDevice        OpenDevice;
    pf_CloseDevice       CloseDevice;
    pf_GetDeviceInfo     GetDeviceInfo;
    pf_GetDeviceList     GetDeviceList;
    pf_SaveConfigValue   SaveConfigValue;
    pf_GetVersion        GetVersion;     /* optional */
    pf_GetNow            GetNow;         /* optional */
    pf_RectilinearToPixel RectilinearToPixel; /* optional */
    pf_PixelToRectilinear PixelToRectilinear; /* optional */
    pf_ExtrinsicCameraMatrix ExtrinsicCameraMatrix; /* optional */
} LeapFns;

/* ------------------------------------------------------------------------- */
/* Context                                                                   */

typedef struct {
    lj_dl_t         dl;
    LeapFns         fn;
    LEAP_CONNECTION conn;
    LEAP_ALLOCATOR  allocator;

    lj_thread_t     thread;
    int             threadStarted;
    lj_mutex_t      mu;
    lj_cond_t       cv;
    volatile int    running;

    /* connection / device state */
    int       connected;
    int       deviceCount;
    uint32_t  deviceStatus;
    LEAP_DEVICE_INFO devInfo;
    char      serial[128];
    int       haveDevInfo;

    uint64_t  policyCurrent;
    uint64_t  policyDesired;
    uint64_t  policyApplied;   /* what we last told the service (set/clear are sent as deltas only) */
    int       policyPending;
    int       trackingMode;

    /* tracking frame cache */
    int64_t   frameId;
    int64_t   frameTs;
    uint32_t  nHands;
    float     framerate;
    float     hands[LJ_MAX_HANDS * LJ_HAND_STRIDE];
    uint64_t  frameSeq;

    /* image cache (left = 0, right = 1) */
    int64_t   imgFrameId;
    int64_t   imgTs;
    uint32_t  imgW, imgH, imgBpp, imgFormat;
    uint8_t*  imgData[2];
    size_t    imgCap[2];
    size_t    imgLen[2];
    uint64_t  imgSeq;

    uint32_t  pending;
    char      lastLog[512];
    char      lastError[512];
} LeapCtx;

static void set_error(LeapCtx* c, const char* fmt, ...) {
    va_list ap; va_start(ap, fmt);
    vsnprintf(c->lastError, sizeof(c->lastError), fmt, ap);
    va_end(ap);
}

static void* lj_alloc(uint32_t size, eLeapAllocatorType typeHint, void* state) {
    (void)typeHint; (void)state;
    return malloc(size);
}
static void lj_free(void* ptr, void* state) {
    (void)state;
    free(ptr);
}

static void put3(float* dst, const LEAP_VECTOR* v) { dst[0] = v->x; dst[1] = v->y; dst[2] = v->z; }
static void put4(float* dst, const LEAP_QUATERNION* q) { dst[0] = q->x; dst[1] = q->y; dst[2] = q->z; dst[3] = q->w; }

static void copy_bone(float* dst, const LEAP_BONE* b) {
    put3(dst + 0, &b->prev_joint);
    put3(dst + 3, &b->next_joint);
    dst[6] = b->width;
    put4(dst + 7, &b->rotation);
}

static void copy_hand(float* dst, const LEAP_HAND* h) {
    memset(dst, 0, sizeof(float) * LJ_HAND_STRIDE);
    dst[H_ID]         = (float)h->id;
    dst[H_TYPE]       = (h->type == eLeapHandType_Left) ? 0.0f : 1.0f;
    dst[H_CONF]       = h->confidence;
    dst[H_VISIBLE]    = (float)((double)h->visible_time / 1000000.0);
    dst[H_PINCH_DIST] = h->pinch_distance;
    dst[H_GRAB_ANGLE] = h->grab_angle;
    dst[H_PINCH]      = h->pinch_strength;
    dst[H_GRAB]       = h->grab_strength;
    dst[H_FLAGS]      = (float)h->flags;
    put3(dst + H_PALM_POS,    &h->palm.position);
    put3(dst + H_PALM_STAB,   &h->palm.stabilized_position);
    put3(dst + H_PALM_VEL,    &h->palm.velocity);
    put3(dst + H_PALM_NORMAL, &h->palm.normal);
    dst[H_PALM_WIDTH] = h->palm.width;
    put3(dst + H_PALM_DIR,    &h->palm.direction);
    put4(dst + H_PALM_QUAT,   &h->palm.orientation);
    for (int d = 0; d < 5; d++) {
        float* dd = dst + H_DIGITS + d * D_STRIDE;
        const LEAP_DIGIT* dg = &h->digits[d];
        dd[D_ID]       = (float)dg->finger_id;
        dd[D_EXTENDED] = dg->is_extended ? 1.0f : 0.0f;
        for (int b = 0; b < 4; b++) copy_bone(dd + D_BONES + b * B_STRIDE, &dg->bones[b]);
    }
    copy_bone(dst + H_ARM, &h->arm);
}

/*
 * Sends only the DIFFERENCE between the flags this client asked for and the flags it already
 * sent. Never clears flags that were not set by us: clearing the tracking-mode flags
 * (OptimizeHMD / OptimizeScreenTop) that were never set makes the Hyperion service stop
 * tracking (observed with Hyperion 6.2.0 on macOS: "PolicyFlagUpdate (tracking mode) ... clear: 399"
 * followed by framerate 0).
 */
static void apply_policy_locked(LeapCtx* c) {
    if (!c->connected || !c->fn.SetPolicyFlags) return;
    uint64_t desired = c->policyDesired & LJ_ALL_POLICY_MASK;
    uint64_t set = desired & ~c->policyApplied;
    uint64_t clear = c->policyApplied & ~desired;
    c->policyPending = 0;
    if (!set && !clear) return;
    c->fn.SetPolicyFlags(c->conn, set, clear);
    c->policyApplied = desired;
}

static void handle_device_event(LeapCtx* c, const LEAP_DEVICE_EVENT* ev) {
    LEAP_DEVICE dev = NULL;
    if (c->fn.OpenDevice(ev->device, &dev) != eLeapRS_Success) {
        lj_lock(&c->mu);
        c->deviceCount++;
        c->deviceStatus = ev->status;
        c->pending |= LJ_EV_DEVICE;
        lj_unlock(&c->mu);
        return;
    }
    LEAP_DEVICE_INFO info; memset(&info, 0, sizeof(info));
    info.size = sizeof(info);
    char serial[128]; memset(serial, 0, sizeof(serial));
    info.serial = serial; info.serial_length = sizeof(serial);
    eLeapRS r = c->fn.GetDeviceInfo(dev, &info);
    lj_lock(&c->mu);
    c->deviceCount++;
    c->deviceStatus = ev->status ? ev->status : info.status;
    if (r == eLeapRS_Success) {
        c->devInfo = info;
        strncpy(c->serial, serial, sizeof(c->serial) - 1);
        c->devInfo.serial = c->serial;
        c->haveDevInfo = 1;
    }
    c->pending |= LJ_EV_DEVICE;
    lj_unlock(&c->mu);
    c->fn.CloseDevice(dev);
}

static void handle_tracking(LeapCtx* c, const LEAP_TRACKING_EVENT* t) {
    lj_lock(&c->mu);
    c->frameId   = t->tracking_frame_id;
    c->frameTs   = t->info.timestamp;
    c->framerate = t->framerate;
    uint32_t n = t->nHands > LJ_MAX_HANDS ? LJ_MAX_HANDS : t->nHands;
    c->nHands = n;
    for (uint32_t i = 0; i < n; i++) copy_hand(c->hands + i * LJ_HAND_STRIDE, &t->pHands[i]);
    c->frameSeq++;
    c->pending |= LJ_EV_FRAME;
    lj_unlock(&c->mu);
}

static void handle_image(LeapCtx* c, const LEAP_IMAGE_EVENT* e) {
    lj_lock(&c->mu);
    c->imgFrameId = e->info.frame_id;
    c->imgTs      = e->info.timestamp;
    const LEAP_IMAGE_PROPERTIES* p = &e->image[0].properties;
    c->imgW = p->width; c->imgH = p->height; c->imgBpp = p->bpp; c->imgFormat = (uint32_t)p->format;
    for (int i = 0; i < 2; i++) {
        const LEAP_IMAGE* im = &e->image[i];
        size_t len = (size_t)im->properties.width * im->properties.height * (im->properties.bpp ? im->properties.bpp : 1);
        if (im->data == NULL || len == 0) { c->imgLen[i] = 0; continue; }
        if (c->imgCap[i] < len) {
            uint8_t* nb = (uint8_t*)realloc(c->imgData[i], len);
            if (!nb) { c->imgLen[i] = 0; continue; }
            c->imgData[i] = nb; c->imgCap[i] = len;
        }
        memcpy(c->imgData[i], (const uint8_t*)im->data + im->offset, len);
        c->imgLen[i] = len;
    }
    c->imgSeq++;
    c->pending |= LJ_EV_IMAGE;
    lj_unlock(&c->mu);
}

static LJ_THREAD_RET poll_thread(void* arg) {
    LeapCtx* c = (LeapCtx*)arg;
    LEAP_CONNECTION_MESSAGE msg;
    while (c->running) {
        memset(&msg, 0, sizeof(msg));
        eLeapRS r = c->fn.PollConnection(c->conn, 100, &msg);
        if (!c->running) break;
        if (r == eLeapRS_Timeout) continue;
        if (r != eLeapRS_Success) {
            /* NotConnected etc.: avoid a hot loop while the service is down */
            lj_sleep_ms(20);
            continue;
        }
        switch (msg.type) {
            case eLeapEventType_Connection:
                lj_lock(&c->mu);
                c->connected = 1;
                c->pending |= LJ_EV_CONNECTION;
                apply_policy_locked(c);
                lj_unlock(&c->mu);
                break;
            case eLeapEventType_ConnectionLost:
                lj_lock(&c->mu);
                c->connected = 0;
                c->deviceCount = 0;
                c->haveDevInfo = 0;
                c->nHands = 0;
                c->policyApplied = 0;   /* re-send our flags after reconnecting */
                c->policyPending = 1;
                c->pending |= LJ_EV_CONNECTION | LJ_EV_DEVICE;
                lj_unlock(&c->mu);
                break;
            case eLeapEventType_Device:
                handle_device_event(c, msg.device_event);
                break;
            case eLeapEventType_DeviceLost:
                lj_lock(&c->mu);
                if (c->deviceCount > 0) c->deviceCount--;
                if (c->deviceCount == 0) { c->haveDevInfo = 0; c->nHands = 0; }
                c->pending |= LJ_EV_DEVICE;
                lj_unlock(&c->mu);
                break;
            case eLeapEventType_DeviceFailure:
                lj_lock(&c->mu);
                c->deviceStatus = msg.device_failure_event ? msg.device_failure_event->status : 0;
                snprintf(c->lastLog, sizeof(c->lastLog), "Device failure, status 0x%08x", c->deviceStatus);
                c->pending |= LJ_EV_DEVICE | LJ_EV_LOG;
                lj_unlock(&c->mu);
                break;
            case eLeapEventType_DeviceStatusChange:
                lj_lock(&c->mu);
                if (msg.device_status_change_event) c->deviceStatus = msg.device_status_change_event->status;
                c->pending |= LJ_EV_DEVICE;
                lj_unlock(&c->mu);
                break;
            case eLeapEventType_Policy:
                lj_lock(&c->mu);
                c->policyCurrent = msg.policy_event ? msg.policy_event->current_policy : 0;
                c->pending |= LJ_EV_POLICY;
                lj_unlock(&c->mu);
                break;
            case eLeapEventType_TrackingMode:
                lj_lock(&c->mu);
                if (msg.tracking_mode_event) c->trackingMode = (int)msg.tracking_mode_event->current_tracking_mode;
                c->pending |= LJ_EV_TRACKINGMODE;
                lj_unlock(&c->mu);
                break;
            case eLeapEventType_Tracking:
                if (msg.tracking_event) handle_tracking(c, msg.tracking_event);
                break;
            case eLeapEventType_Image:
                if (msg.image_event) handle_image(c, msg.image_event);
                break;
            case eLeapEventType_LogEvent:
                if (msg.log_event && msg.log_event->message) {
                    lj_lock(&c->mu);
                    snprintf(c->lastLog, sizeof(c->lastLog), "%s", msg.log_event->message);
                    c->pending |= LJ_EV_LOG;
                    lj_unlock(&c->mu);
                }
                break;
            case eLeapEventType_LogEvents:
                if (msg.log_events && msg.log_events->nEvents > 0) {
                    const LEAP_LOG_EVENT* le = &msg.log_events->events[msg.log_events->nEvents - 1];
                    if (le->message) {
                        lj_lock(&c->mu);
                        snprintf(c->lastLog, sizeof(c->lastLog), "%s", le->message);
                        c->pending |= LJ_EV_LOG;
                        lj_unlock(&c->mu);
                    }
                }
                break;
            default:
                break; /* ConfigResponse, DroppedFrame, IMU, ... ignored */
        }
        lj_lock(&c->mu);
        if (c->pending) lj_cond_broadcast(&c->cv);
        lj_unlock(&c->mu);
    }
    LJ_THREAD_RETURN;
}

/* ------------------------------------------------------------------------- */
/* Helpers                                                                   */

static LeapCtx* ctx_of(jlong h) { return (LeapCtx*)(intptr_t)h; }

#define LOAD_SYM(name) do { \
    c->fn.name = (pf_##name)lj_dlsym(c->dl, "Leap" #name); \
    if (!c->fn.name) { set_error(c, "Symbol Leap" #name " not found in libLeapC"); return -2; } \
} while (0)
#define LOAD_SYM_OPT(name) do { c->fn.name = (pf_##name)lj_dlsym(c->dl, "Leap" #name); } while (0)

static int load_leapc(LeapCtx* c, const char* path) {
    if (c->dl) return 0;
    const char* candidates[] = {
        path,
#ifdef _WIN32
        "LeapC.dll",
        "C:\\Program Files\\Ultraleap\\LeapSDK\\lib\\x64\\LeapC.dll",
        "C:\\Program Files\\Ultraleap\\LeapSDK\\lib\\LeapC.dll",
        "C:\\Program Files\\Ultraleap\\Ultraleap Hand Tracking\\LeapC.dll",
#elif defined(__APPLE__)
        "libLeapC.6.dylib",
        "libLeapC.dylib",
        "libLeapC.5.dylib",
        "/Applications/Ultraleap Hand Tracking.app/Contents/LeapSDK/lib/libLeapC.6.dylib",
        "/Applications/Ultraleap Hand Tracking.app/Contents/LeapSDK/lib/libLeapC.dylib",
        "/Library/Application Support/Ultraleap/LeapSDK/lib/libLeapC.dylib",
        "/Applications/Ultraleap Hand Tracking Service.app/Contents/LeapSDK/lib/libLeapC.dylib",
#else
        "libLeapC.so.6",
        "libLeapC.so",
        "libLeapC.so.5",
        "/usr/lib/ultraleap-hand-tracking-service/libLeapC.so",
        "/usr/lib/ultraleap-hand-tracking-service/libLeapC.so.6",
        "/opt/ultraleap/LeapSDK/lib/libLeapC.so",
#endif
        NULL
    };
    char home[1024]; home[0] = 0;
#ifdef __APPLE__
    const char* h = getenv("HOME");
    if (h) snprintf(home, sizeof(home), "%s/Applications/Ultraleap Hand Tracking.app/Contents/LeapSDK/lib/libLeapC.6.dylib", h);
#endif
    for (int i = 0; candidates[i] != NULL || i == 0; i++) {
        const char* p = candidates[i];
        if (!p || !*p) continue;
        c->dl = lj_dlopen(p);
        if (c->dl) break;
    }
    if (!c->dl && home[0]) c->dl = lj_dlopen(home);
    if (!c->dl) {
        set_error(c, "Could not load the LeapC library (Ultraleap Hand Tracking not installed?): %s", lj_dlerror());
        return -1;
    }
    LOAD_SYM(CreateConnection); LOAD_SYM(OpenConnection); LOAD_SYM(CloseConnection);
    LOAD_SYM(DestroyConnection); LOAD_SYM(PollConnection); LOAD_SYM(GetConnectionInfo);
    LOAD_SYM(SetPolicyFlags); LOAD_SYM(SetAllocator); LOAD_SYM(OpenDevice); LOAD_SYM(CloseDevice);
    LOAD_SYM(GetDeviceInfo); LOAD_SYM(GetDeviceList); LOAD_SYM(SaveConfigValue);
    LOAD_SYM_OPT(SetTrackingMode); LOAD_SYM_OPT(GetTrackingMode); LOAD_SYM_OPT(GetVersion); LOAD_SYM_OPT(GetNow);
    LOAD_SYM_OPT(RectilinearToPixel); LOAD_SYM_OPT(PixelToRectilinear); LOAD_SYM_OPT(ExtrinsicCameraMatrix);
    return 0;
}

static jstring new_string(JNIEnv* env, const char* s) {
    return (*env)->NewStringUTF(env, s ? s : "");
}

/* ------------------------------------------------------------------------- */
/* JNI entry points                                                          */

JNIEXPORT jlong JNICALL Java_com_leapmotion_leap_LeapJNI_nCreate(JNIEnv* env, jclass cls) {
    (void)env; (void)cls;
    LeapCtx* c = (LeapCtx*)calloc(1, sizeof(LeapCtx));
    if (!c) return 0;
    lj_mutex_init(&c->mu);
    lj_cond_init(&c->cv);
    c->allocator.allocate = lj_alloc;
    c->allocator.deallocate = lj_free;
    c->allocator.state = NULL;
    c->trackingMode = -1;
    return (jlong)(intptr_t)c;
}

JNIEXPORT jint JNICALL Java_com_leapmotion_leap_LeapJNI_nOpen(JNIEnv* env, jclass cls, jlong h, jstring jpath) {
    (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c) return -100;
    const char* path = NULL;
    if (jpath) path = (*env)->GetStringUTFChars(env, jpath, NULL);
    int rc = load_leapc(c, path);
    if (jpath && path) (*env)->ReleaseStringUTFChars(env, jpath, path);
    if (rc != 0) return rc;

    if (!c->conn) {
        LEAP_CONNECTION_CONFIG cfg; memset(&cfg, 0, sizeof(cfg));
        cfg.size = sizeof(cfg);
        eLeapRS r = c->fn.CreateConnection(NULL, &c->conn);
        if (r != eLeapRS_Success) { set_error(c, "LeapCreateConnection failed: 0x%08x", (unsigned)r); return -3; }
        c->fn.SetAllocator(c->conn, &c->allocator);
    }
    eLeapRS r = c->fn.OpenConnection(c->conn);
    if (r != eLeapRS_Success) { set_error(c, "LeapOpenConnection failed: 0x%08x", (unsigned)r); return -4; }

    if (!c->threadStarted) {
        c->running = 1;
        if (lj_thread_create(&c->thread, poll_thread, c) != 0) {
            c->running = 0;
            set_error(c, "could not create the polling thread");
            return -5;
        }
        c->threadStarted = 1;
    }
    return 0;
}

JNIEXPORT void JNICALL Java_com_leapmotion_leap_LeapJNI_nClose(JNIEnv* env, jclass cls, jlong h) {
    (void)env; (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c) return;
    if (c->threadStarted) {
        c->running = 0;
        if (c->conn && c->fn.CloseConnection) c->fn.CloseConnection(c->conn);
        lj_thread_join(c->thread);
        c->threadStarted = 0;
    } else if (c->conn && c->fn.CloseConnection) {
        c->fn.CloseConnection(c->conn);
    }
    lj_lock(&c->mu);
    c->connected = 0; c->deviceCount = 0; c->haveDevInfo = 0; c->nHands = 0;
    c->pending |= LJ_EV_CONNECTION;
    lj_cond_broadcast(&c->cv);
    lj_unlock(&c->mu);
}

JNIEXPORT void JNICALL Java_com_leapmotion_leap_LeapJNI_nDestroy(JNIEnv* env, jclass cls, jlong h) {
    LeapCtx* c = ctx_of(h);
    if (!c) return;
    Java_com_leapmotion_leap_LeapJNI_nClose(env, cls, h);
    if (c->conn && c->fn.DestroyConnection) c->fn.DestroyConnection(c->conn);
    c->conn = NULL;
    free(c->imgData[0]); free(c->imgData[1]);
    lj_mutex_destroy(&c->mu);
    lj_cond_destroy(&c->cv);
    /* keep dl handle loaded: dlclose of LeapC is not safe for re-open */
    free(c);
}

JNIEXPORT jint JNICALL Java_com_leapmotion_leap_LeapJNI_nWait(JNIEnv* env, jclass cls, jlong h, jint timeoutMs) {
    (void)env; (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c) return 0;
    lj_lock(&c->mu);
    if (!c->pending && timeoutMs > 0) {
        lj_cond_timedwait_ms(&c->cv, &c->mu, timeoutMs);
    }
    jint p = (jint)c->pending;
    c->pending = 0;
    lj_unlock(&c->mu);
    return p;
}

JNIEXPORT jboolean JNICALL Java_com_leapmotion_leap_LeapJNI_nIsConnected(JNIEnv* env, jclass cls, jlong h) {
    (void)env; (void)cls;
    LeapCtx* c = ctx_of(h);
    return (c && c->connected) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_com_leapmotion_leap_LeapJNI_nConnectionStatus(JNIEnv* env, jclass cls, jlong h) {
    (void)env; (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c || !c->conn || !c->fn.GetConnectionInfo) return 0;
    LEAP_CONNECTION_INFO info; memset(&info, 0, sizeof(info)); info.size = sizeof(info);
    if (c->fn.GetConnectionInfo(c->conn, &info) != eLeapRS_Success) return 0;
    switch (info.status) {
        case eLeapConnectionStatus_Connected: return 1;
        case eLeapConnectionStatus_HandshakeIncomplete: return 2;
        case eLeapConnectionStatus_NotRunning: return 3;
        default: return 0;
    }
}

JNIEXPORT jint JNICALL Java_com_leapmotion_leap_LeapJNI_nDeviceCount(JNIEnv* env, jclass cls, jlong h) {
    (void)env; (void)cls;
    LeapCtx* c = ctx_of(h);
    return c ? c->deviceCount : 0;
}

/* out[0]=status out[1]=caps out[2]=pid out[3]=baseline(um) out[4]=h_fov(rad) out[5]=v_fov(rad) out[6]=range(um) */
JNIEXPORT jboolean JNICALL Java_com_leapmotion_leap_LeapJNI_nDeviceInfo(JNIEnv* env, jclass cls, jlong h, jfloatArray out) {
    (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c || !out || (*env)->GetArrayLength(env, out) < 7) return JNI_FALSE;
    float v[7];
    lj_lock(&c->mu);
    int ok = c->haveDevInfo;
    v[0] = (float)c->deviceStatus; v[1] = (float)c->devInfo.caps; v[2] = (float)c->devInfo.pid;
    v[3] = (float)c->devInfo.baseline; v[4] = c->devInfo.h_fov; v[5] = c->devInfo.v_fov; v[6] = (float)c->devInfo.range;
    lj_unlock(&c->mu);
    (*env)->SetFloatArrayRegion(env, out, 0, 7, v);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL Java_com_leapmotion_leap_LeapJNI_nDeviceSerial(JNIEnv* env, jclass cls, jlong h) {
    (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c) return new_string(env, "");
    char s[128];
    lj_lock(&c->mu);
    strncpy(s, c->haveDevInfo ? c->serial : "", sizeof(s) - 1); s[sizeof(s) - 1] = 0;
    lj_unlock(&c->mu);
    return new_string(env, s);
}

JNIEXPORT void JNICALL Java_com_leapmotion_leap_LeapJNI_nSetPolicy(JNIEnv* env, jclass cls, jlong h, jlong set, jlong clear) {
    (void)env; (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c) return;
    lj_lock(&c->mu);
    uint64_t before = c->policyDesired;
    c->policyDesired = (c->policyDesired | (uint64_t)set) & ~(uint64_t)clear;
    if (c->policyDesired != before || c->policyPending) {
        c->policyPending = 1;
        apply_policy_locked(c);
    }
    lj_unlock(&c->mu);
}

JNIEXPORT jlong JNICALL Java_com_leapmotion_leap_LeapJNI_nGetPolicy(JNIEnv* env, jclass cls, jlong h) {
    (void)env; (void)cls;
    LeapCtx* c = ctx_of(h);
    return c ? (jlong)c->policyCurrent : 0;
}

JNIEXPORT jlong JNICALL Java_com_leapmotion_leap_LeapJNI_nGetDesiredPolicy(JNIEnv* env, jclass cls, jlong h) {
    (void)env; (void)cls;
    LeapCtx* c = ctx_of(h);
    return c ? (jlong)c->policyDesired : 0;
}

/* meta: [0]=frame id [1]=timestamp us [2]=nHands [3]=floatToIntBits(framerate) [4]=seq [5]=policy [6]=connected [7]=deviceStatus */
JNIEXPORT jint JNICALL Java_com_leapmotion_leap_LeapJNI_nReadFrame(JNIEnv* env, jclass cls, jlong h, jlongArray meta, jfloatArray data) {
    (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c || !meta || !data) return -1;
    if ((*env)->GetArrayLength(env, meta) < LJ_META_LEN) return -1;
    jsize cap = (*env)->GetArrayLength(env, data);
    jlong m[LJ_META_LEN];
    lj_lock(&c->mu);
    if (c->frameSeq == 0) { lj_unlock(&c->mu); return -1; }
    uint32_t n = c->nHands;
    if ((jsize)(n * LJ_HAND_STRIDE) > cap) n = (uint32_t)(cap / LJ_HAND_STRIDE);
    union { float f; int32_t i; } fr; fr.f = c->framerate;
    m[0] = c->frameId; m[1] = c->frameTs; m[2] = n; m[3] = fr.i; m[4] = (jlong)c->frameSeq;
    m[5] = (jlong)c->policyCurrent; m[6] = c->connected; m[7] = (jlong)c->deviceStatus;
    if (n > 0) (*env)->SetFloatArrayRegion(env, data, 0, (jsize)(n * LJ_HAND_STRIDE), c->hands);
    lj_unlock(&c->mu);
    (*env)->SetLongArrayRegion(env, meta, 0, LJ_META_LEN, m);
    return (jint)n;
}

/* meta: [0]=frame id [1]=timestamp [2]=width [3]=height [4]=bpp [5]=format [6]=seq [7]=bytes per image.
 * Returns number of images copied (0 if none yet). If arrays are null/too small nothing is copied but meta is filled. */
JNIEXPORT jint JNICALL Java_com_leapmotion_leap_LeapJNI_nReadImages(JNIEnv* env, jclass cls, jlong h, jlongArray meta, jbyteArray left, jbyteArray right) {
    (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c || !meta || (*env)->GetArrayLength(env, meta) < LJ_META_LEN) return 0;
    jlong m[LJ_META_LEN];
    int copied = 0;
    lj_lock(&c->mu);
    if (c->imgSeq == 0) { lj_unlock(&c->mu); memset(m, 0, sizeof(m)); (*env)->SetLongArrayRegion(env, meta, 0, LJ_META_LEN, m); return 0; }
    m[0] = c->imgFrameId; m[1] = c->imgTs; m[2] = c->imgW; m[3] = c->imgH; m[4] = c->imgBpp; m[5] = c->imgFormat; m[6] = (jlong)c->imgSeq; m[7] = (jlong)c->imgLen[0];
    jbyteArray arr[2] = { left, right };
    for (int i = 0; i < 2; i++) {
        if (!arr[i] || c->imgLen[i] == 0) continue;
        jsize cap = (*env)->GetArrayLength(env, arr[i]);
        if ((size_t)cap < c->imgLen[i]) continue;
        (*env)->SetByteArrayRegion(env, arr[i], 0, (jsize)c->imgLen[i], (const jbyte*)c->imgData[i]);
        copied++;
    }
    lj_unlock(&c->mu);
    (*env)->SetLongArrayRegion(env, meta, 0, LJ_META_LEN, m);
    return copied;
}

JNIEXPORT jint JNICALL Java_com_leapmotion_leap_LeapJNI_nSetTrackingMode(JNIEnv* env, jclass cls, jlong h, jint mode) {
    (void)env; (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c || !c->conn || !c->fn.SetTrackingMode) return -1;
    return (jint)c->fn.SetTrackingMode(c->conn, (eLeapTrackingMode)mode);
}

JNIEXPORT jint JNICALL Java_com_leapmotion_leap_LeapJNI_nGetTrackingMode(JNIEnv* env, jclass cls, jlong h) {
    (void)env; (void)cls;
    LeapCtx* c = ctx_of(h);
    return c ? c->trackingMode : -1;
}

/* type: 1 boolean, 2 int32, 3 float, 4 string */
JNIEXPORT jboolean JNICALL Java_com_leapmotion_leap_LeapJNI_nSaveConfig(JNIEnv* env, jclass cls, jlong h, jstring jkey, jint type, jboolean b, jint i, jfloat f, jstring js) {
    (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c || !c->conn || !c->connected || !jkey) return JNI_FALSE;
    const char* key = (*env)->GetStringUTFChars(env, jkey, NULL);
    const char* sv = js ? (*env)->GetStringUTFChars(env, js, NULL) : NULL;
    LEAP_VARIANT v; memset(&v, 0, sizeof(v));
    switch (type) {
        case 1: v.type = eLeapValueType_Boolean; v.boolValue = b ? true : false; break;
        case 2: v.type = eLeapValueType_Int32;   v.iValue = i; break;
        case 3: v.type = eLeapValueType_Float;   v.fValue = f; break;
        case 4: v.type = eLeapValueType_String;  v.strValue = sv ? sv : ""; break;
        default: v.type = eLeapValueType_Unknown; break;
    }
    uint32_t req = 0;
    eLeapRS r = c->fn.SaveConfigValue(c->conn, key, &v, &req);
    (*env)->ReleaseStringUTFChars(env, jkey, key);
    if (js && sv) (*env)->ReleaseStringUTFChars(env, js, sv);
    return r == eLeapRS_Success ? JNI_TRUE : JNI_FALSE;
}

/* part: 0 client library, 1 client protocol, 2 server library, 3 server protocol */
JNIEXPORT jboolean JNICALL Java_com_leapmotion_leap_LeapJNI_nVersion(JNIEnv* env, jclass cls, jlong h, jint part, jintArray out) {
    (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c || !c->conn || !c->fn.GetVersion || !out || (*env)->GetArrayLength(env, out) < 3) return JNI_FALSE;
    LEAP_VERSION v; memset(&v, 0, sizeof(v));
    if (c->fn.GetVersion(c->conn, (eLeapVersionPart)part, &v) != eLeapRS_Success) return JNI_FALSE;
    jint o[3] = { v.major, v.minor, v.patch };
    (*env)->SetIntArrayRegion(env, out, 0, 3, o);
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL Java_com_leapmotion_leap_LeapJNI_nNow(JNIEnv* env, jclass cls, jlong h) {
    (void)env; (void)cls;
    LeapCtx* c = ctx_of(h);
    if (c && c->fn.GetNow) return (jlong)c->fn.GetNow();
    return (jlong)lj_now_us();
}

JNIEXPORT jstring JNICALL Java_com_leapmotion_leap_LeapJNI_nLastError(JNIEnv* env, jclass cls, jlong h) {
    (void)cls;
    LeapCtx* c = ctx_of(h);
    return new_string(env, c ? c->lastError : "");
}

JNIEXPORT jstring JNICALL Java_com_leapmotion_leap_LeapJNI_nLastLog(JNIEnv* env, jclass cls, jlong h) {
    (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c) return new_string(env, "");
    char s[512];
    lj_lock(&c->mu);
    strncpy(s, c->lastLog, sizeof(s) - 1); s[sizeof(s) - 1] = 0;
    lj_unlock(&c->mu);
    return new_string(env, s);
}

/* camera: 1 = left, 2 = right (eLeapPerspectiveType). in: ray direction (x, y, 1). out2: pixel x, y. */
JNIEXPORT jboolean JNICALL Java_com_leapmotion_leap_LeapJNI_nRectilinearToPixel(JNIEnv* env, jclass cls, jlong h, jint camera, jfloat x, jfloat y, jfloat z, jfloatArray out) {
    (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c || !c->conn || !c->fn.RectilinearToPixel || !out || (*env)->GetArrayLength(env, out) < 2) return JNI_FALSE;
    LEAP_VECTOR in; in.x = x; in.y = y; in.z = z;
    LEAP_VECTOR r = c->fn.RectilinearToPixel(c->conn, (eLeapPerspectiveType)camera, in);
    if (r.x != r.x || r.y != r.y) return JNI_FALSE; /* NaN: no images received yet */
    float o[2] = { r.x, r.y };
    (*env)->SetFloatArrayRegion(env, out, 0, 2, o);
    return JNI_TRUE;
}

/* camera: 1 = left, 2 = right. in: pixel x, y. out: ray direction x, y (z is always 1). */
JNIEXPORT jboolean JNICALL Java_com_leapmotion_leap_LeapJNI_nPixelToRectilinear(JNIEnv* env, jclass cls, jlong h, jint camera, jfloat px, jfloat py, jfloatArray out) {
    (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c || !c->conn || !c->fn.PixelToRectilinear || !out || (*env)->GetArrayLength(env, out) < 2) return JNI_FALSE;
    LEAP_VECTOR in; in.x = px; in.y = py; in.z = 0;
    LEAP_VECTOR r = c->fn.PixelToRectilinear(c->conn, (eLeapPerspectiveType)camera, in);
    if (r.x != r.x || r.y != r.y) return JNI_FALSE;
    float o[2] = { r.x, r.y };
    (*env)->SetFloatArrayRegion(env, out, 0, 2, o);
    return JNI_TRUE;
}

/* camera: 1 = left, 2 = right. out16: 4x4 camera-to-Leap transform, column major. */
JNIEXPORT jboolean JNICALL Java_com_leapmotion_leap_LeapJNI_nExtrinsicCameraMatrix(JNIEnv* env, jclass cls, jlong h, jint camera, jfloatArray out) {
    (void)cls;
    LeapCtx* c = ctx_of(h);
    if (!c || !c->conn || !c->fn.ExtrinsicCameraMatrix || !out || (*env)->GetArrayLength(env, out) < 16) return JNI_FALSE;
    float m[16];
    { float nanv; uint32_t bits = 0x7FC00000u; memcpy(&nanv, &bits, 4); for (int i = 0; i < 16; i++) m[i] = nanv; } /* NaN sentinel */
    c->fn.ExtrinsicCameraMatrix(c->conn, (eLeapPerspectiveType)camera, m);
    for (int i = 0; i < 16; i++) if (m[i] != m[i]) return JNI_FALSE;
    (*env)->SetFloatArrayRegion(env, out, 0, 16, m);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL Java_com_leapmotion_leap_LeapJNI_nBridgeVersion(JNIEnv* env, jclass cls) {
    (void)env; (void)cls;
    return 3;
}
