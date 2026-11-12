/*
 * mock_leapc.c - a fake libLeapC used ONLY for testing the JNI bridge without hardware or
 * the Ultraleap service. Implements the subset of LeapC used by leapjni.c and emits a
 * synthetic stream: Connection, Device, Policy, Tracking (one right hand whose index finger
 * draws a circle), Image (two 64x32 IR images).
 * Build: native/mock/build_mock.sh    Use: -Dleap.leapc.path=native/mock/build/libLeapC-mock.dylib
 * Author: Tiago Martins
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <math.h>
#ifdef _WIN32
#  define WIN32_LEAN_AND_MEAN
#  include <windows.h>
#  define LEAP_EXPORT __declspec(dllexport)   /* we ARE the library */
static void mock_sleep_ms(int ms) { Sleep((DWORD)ms); }
static int64_t mock_now_us(void) { FILETIME ft; GetSystemTimeAsFileTime(&ft); ULARGE_INTEGER u; u.LowPart = ft.dwLowDateTime; u.HighPart = ft.dwHighDateTime; return (int64_t)(u.QuadPart / 10); }
#else
#  include <unistd.h>
#  include <sys/time.h>
#  define LEAP_EXPORT __attribute__((visibility("default")))
static void mock_sleep_ms(int ms) { usleep((useconds_t)ms * 1000); }
static int64_t mock_now_us(void) { struct timeval tv; gettimeofday(&tv, NULL); return (int64_t)tv.tv_sec * 1000000 + tv.tv_usec; }
#endif
#ifndef M_PI
#  define M_PI 3.14159265358979323846
#endif
static float mock_nan(void) { float f; unsigned int b = 0x7FC00000u; memcpy(&f, &b, 4); return f; }
#include "../LeapC.h"

#define MOCK_W 64
#define MOCK_H 32

struct _LEAP_CONNECTION {
    int open; int step; int64_t frameId; uint64_t policy; int policyDirty; int tracking_mode; int trackerBroken;
    LEAP_ALLOCATOR alloc; int haveAlloc;
    LEAP_HAND hand; LEAP_TRACKING_EVENT trk;
    LEAP_CONNECTION_EVENT connEv; LEAP_DEVICE_EVENT devEv; LEAP_POLICY_EVENT polEv; LEAP_TRACKING_MODE_EVENT tmEv;
    LEAP_IMAGE_EVENT imgEv; void* imgBuf[2];
    int64_t t0;
};
struct _LEAP_DEVICE { int id; };
static struct _LEAP_DEVICE g_dev = { 1 };

static LEAP_VECTOR V(float x, float y, float z) { LEAP_VECTOR v; v.x = x; v.y = y; v.z = z; return v; }
static LEAP_QUATERNION Q(void) { LEAP_QUATERNION q; q.x = q.y = q.z = 0; q.w = 1; return q; }

LEAP_EXPORT int64_t LEAP_CALL LeapGetNow(void) { return mock_now_us(); }

LEAP_EXPORT eLeapRS LEAP_CALL LeapCreateConnection(const LEAP_CONNECTION_CONFIG* cfg, LEAP_CONNECTION* ph) {
    (void)cfg;
    struct _LEAP_CONNECTION* c = calloc(1, sizeof *c);
    if (!c) return eLeapRS_InsufficientResources;
    c->t0 = mock_now_us();
    *ph = c;
    return eLeapRS_Success;
}
LEAP_EXPORT eLeapRS LEAP_CALL LeapSetAllocator(LEAP_CONNECTION c, const LEAP_ALLOCATOR* a) { c->alloc = *a; c->haveAlloc = 1; return eLeapRS_Success; }
LEAP_EXPORT eLeapRS LEAP_CALL LeapOpenConnection(LEAP_CONNECTION c) { c->open = 1; c->step = 0; return eLeapRS_Success; }
LEAP_EXPORT void LEAP_CALL LeapCloseConnection(LEAP_CONNECTION c) { c->open = 0; }
LEAP_EXPORT void LEAP_CALL LeapDestroyConnection(LEAP_CONNECTION c) {
    for (int i = 0; i < 2; i++) if (c->imgBuf[i]) { if (c->haveAlloc) c->alloc.deallocate(c->imgBuf[i], c->alloc.state); else free(c->imgBuf[i]); }
    free(c);
}
LEAP_EXPORT eLeapRS LEAP_CALL LeapGetConnectionInfo(LEAP_CONNECTION c, LEAP_CONNECTION_INFO* i) { i->status = c->open ? eLeapConnectionStatus_Connected : eLeapConnectionStatus_NotConnected; return eLeapRS_Success; }
/* Mimics Hyperion 6.2.0 on macOS: clearing tracking-mode flags that were never set stops the tracker. */
LEAP_EXPORT eLeapRS LEAP_CALL LeapSetPolicyFlags(LEAP_CONNECTION c, uint64_t set, uint64_t clear) {
    uint64_t modeBits = eLeapPolicyFlag_OptimizeHMD | eLeapPolicyFlag_OptimizeScreenTop;
    if ((clear & modeBits) & ~c->policy) { c->trackerBroken = 1; fprintf(stderr, "[mock] clear of unset tracking-mode flags (set=%llu clear=%llu): tracker stopped\n", (unsigned long long)set, (unsigned long long)clear); }
    c->policy = (c->policy | set) & ~clear; c->policyDirty = 1; return eLeapRS_Success;
}
LEAP_EXPORT eLeapRS LEAP_CALL LeapSetTrackingMode(LEAP_CONNECTION c, eLeapTrackingMode m) { c->tracking_mode = (int)m; return eLeapRS_Success; }
LEAP_EXPORT eLeapRS LEAP_CALL LeapGetTrackingMode(LEAP_CONNECTION c) { (void)c; return eLeapRS_Success; }
LEAP_EXPORT eLeapRS LEAP_CALL LeapSaveConfigValue(LEAP_CONNECTION c, const char* key, const LEAP_VARIANT* v, uint32_t* req) { (void)c; (void)key; (void)v; if (req) *req = 1; return eLeapRS_Success; }
LEAP_EXPORT eLeapRS LEAP_CALL LeapGetVersion(LEAP_CONNECTION c, eLeapVersionPart part, LEAP_VERSION* v) { (void)c; (void)part; v->major = 6; v->minor = 2; v->patch = 0; return eLeapRS_Success; }
LEAP_EXPORT eLeapRS LEAP_CALL LeapGetDeviceList(LEAP_CONNECTION c, LEAP_DEVICE_REF* arr, uint32_t* n) {
    (void)c; if (!arr) { *n = 1; return eLeapRS_Success; }
    if (*n < 1) { *n = 1; return eLeapRS_InsufficientBuffer; }
    arr[0].handle = &g_dev; arr[0].id = 1; *n = 1; return eLeapRS_Success;
}
LEAP_EXPORT eLeapRS LEAP_CALL LeapOpenDevice(LEAP_DEVICE_REF r, LEAP_DEVICE* ph) { (void)r; *ph = &g_dev; return eLeapRS_Success; }
LEAP_EXPORT void LEAP_CALL LeapCloseDevice(LEAP_DEVICE d) { (void)d; }
LEAP_EXPORT eLeapRS LEAP_CALL LeapGetDeviceInfo(LEAP_DEVICE d, LEAP_DEVICE_INFO* info) {
    (void)d;
    const char* serial = "MOCK-LMC-0001";
    uint32_t need = (uint32_t)strlen(serial) + 1;
    info->status = eLeapDeviceStatus_Streaming; info->caps = 0; info->pid = eLeapDevicePID_Peripheral;
    info->baseline = 40000; info->h_fov = 2.303835f; info->v_fov = 2.007129f; info->range = 800000;
    if (info->serial_length < need) { info->serial_length = need; return eLeapRS_InsufficientBuffer; }
    memcpy(info->serial, serial, need); info->serial_length = need;
    return eLeapRS_Success;
}

static void fill_bone(LEAP_BONE* b, LEAP_VECTOR a, LEAP_VECTOR z, float w) { b->prev_joint = a; b->next_joint = z; b->width = w; b->rotation = Q(); }

static void build_hand(struct _LEAP_CONNECTION* c, double t) {
    LEAP_HAND* h = &c->hand; memset(h, 0, sizeof *h);
    h->id = 12; h->type = eLeapHandType_Right; h->confidence = 1.0f; h->visible_time = (uint64_t)(t * 1000000);
    h->pinch_distance = 60; h->grab_angle = 0.4f; h->pinch_strength = 0.25f; h->grab_strength = 0.5f;
    LEAP_VECTOR palm = V(20, 200, 10);
    h->palm.position = palm; h->palm.stabilized_position = palm; h->palm.velocity = V(0, 0, 0);
    h->palm.normal = V(0, -1, 0); h->palm.width = 85; h->palm.direction = V(0, 0, -1); h->palm.orientation = Q();
    /* index tip draws a circle of radius 30 mm around (20, 200, -80) in the XY plane, 1.5 turns per second */
    double a = 2 * M_PI * 1.5 * t;
    LEAP_VECTOR center = V(20, 200, -80);
    LEAP_VECTOR tips[5] = { V(-30, 200, -40), V(center.x + 30 * cos(a), center.y + 30 * sin(a), center.z), V(20, 205, -95), V(40, 205, -90), V(60, 200, -70) };
    for (int d = 0; d < 5; d++) {
        LEAP_DIGIT* dg = &h->digits[d]; dg->finger_id = 120 + d; dg->is_extended = 1;
        LEAP_VECTOR dir = V(tips[d].x - palm.x, tips[d].y - palm.y, tips[d].z - palm.z);
        for (int k = 0; k < 4; k++) {
            LEAP_VECTOR p0 = V(palm.x + dir.x * k * 0.25f, palm.y + dir.y * k * 0.25f, palm.z + dir.z * k * 0.25f);
            LEAP_VECTOR p1 = V(palm.x + dir.x * (k + 1) * 0.25f, palm.y + dir.y * (k + 1) * 0.25f, palm.z + dir.z * (k + 1) * 0.25f);
            fill_bone(&dg->bones[k], p0, p1, 15.0f);
        }
    }
    fill_bone(&h->arm, V(20, 180, 260), V(20, 190, 70), 50);
    c->trk.info.reserved = NULL; c->trk.info.frame_id = c->frameId; c->trk.info.timestamp = mock_now_us();
    c->trk.tracking_frame_id = c->frameId; c->trk.nHands = 1; c->trk.pHands = h; c->trk.framerate = 100.0f;
}

static void build_images(struct _LEAP_CONNECTION* c) {
    size_t len = MOCK_W * MOCK_H;
    for (int i = 0; i < 2; i++) {
        if (!c->imgBuf[i]) c->imgBuf[i] = c->haveAlloc ? c->alloc.allocate((uint32_t)len, eLeapAllocatorType_Uint8, c->alloc.state) : malloc(len);
        unsigned char* p = c->imgBuf[i];
        for (size_t k = 0; k < len; k++) p[k] = (unsigned char)((k + i * 100) & 0xFF);
        LEAP_IMAGE* im = &c->imgEv.image[i];
        im->properties.type = eLeapImageType_Default; im->properties.format = eLeapImageFormat_IR; im->properties.bpp = 1;
        im->properties.width = MOCK_W; im->properties.height = MOCK_H; im->properties.x_scale = im->properties.y_scale = 1;
        im->matrix_version = 1; im->distortion_matrix = NULL; im->data = p; im->offset = 0;
    }
    c->imgEv.info.frame_id = c->frameId; c->imgEv.info.timestamp = mock_now_us(); c->imgEv.calib = NULL;
}

/* Pinhole camera model for tests: 640x240 image, focal 135 px, principal point (320, 120).
 * pixel = (cx + f * ray.x, cy + f * ray.y); the inverse for LeapPixelToRectilinear. Camera 1 = left, 2 = right. */
LEAP_EXPORT LEAP_VECTOR LEAP_CALL LeapRectilinearToPixel(LEAP_CONNECTION c, eLeapPerspectiveType cam, LEAP_VECTOR r) {
    LEAP_VECTOR p; (void)c;
    if (cam != eLeapPerspectiveType_stereo_left && cam != eLeapPerspectiveType_stereo_right) { p.x = p.y = mock_nan(); p.z = 1; return p; }
    p.x = 320.0f + 135.0f * r.x; p.y = 120.0f + 135.0f * r.y; p.z = 1; return p;
}
LEAP_EXPORT LEAP_VECTOR LEAP_CALL LeapPixelToRectilinear(LEAP_CONNECTION c, eLeapPerspectiveType cam, LEAP_VECTOR px) {
    LEAP_VECTOR r; (void)c;
    if (cam != eLeapPerspectiveType_stereo_left && cam != eLeapPerspectiveType_stereo_right) { r.x = r.y = mock_nan(); r.z = 1; return r; }
    r.x = (px.x - 320.0f) / 135.0f; r.y = (px.y - 120.0f) / 135.0f; r.z = 1; return r;
}

/* Camera-to-Leap transform (column major) for the mock: camera z = Leap +y (looking up),
 * camera x = Leap -x (raw images are mirrored), camera y = Leap +z, camera at x = -20 (left) / +20 (right). */
LEAP_EXPORT void LEAP_CALL LeapExtrinsicCameraMatrix(LEAP_CONNECTION c, eLeapPerspectiveType cam, float* m) {
    (void)c;
    if (cam != eLeapPerspectiveType_stereo_left && cam != eLeapPerspectiveType_stereo_right) return;
    float t = cam == eLeapPerspectiveType_stereo_left ? -20.0f : 20.0f;
    float v[16] = { -1, 0, 0, 0,   0, 0, 1, 0,   0, 1, 0, 0,   t, 0, 0, 1 };
    memcpy(m, v, sizeof v);
}

LEAP_EXPORT eLeapRS LEAP_CALL LeapPollConnection(LEAP_CONNECTION c, uint32_t timeout, LEAP_CONNECTION_MESSAGE* msg) {
    (void)timeout;
    if (!c->open) { mock_sleep_ms(50); return eLeapRS_NotConnected; }
    memset(msg, 0, sizeof *msg); msg->size = sizeof *msg; msg->device_id = 1;
    int s = c->step++;
    if (s == 0) { msg->type = eLeapEventType_Connection; c->connEv.flags = 0; msg->connection_event = &c->connEv; return eLeapRS_Success; }
    if (s == 1) { msg->type = eLeapEventType_Device; c->devEv.flags = 0; c->devEv.device.handle = &g_dev; c->devEv.device.id = 1; c->devEv.status = eLeapDeviceStatus_Streaming; msg->device_event = &c->devEv; return eLeapRS_Success; }
    if (s == 2) { msg->type = eLeapEventType_TrackingMode; c->tmEv.current_tracking_mode = eLeapTrackingMode_Desktop; msg->tracking_mode_event = &c->tmEv; return eLeapRS_Success; }
    if (c->policyDirty) { c->policyDirty = 0; msg->type = eLeapEventType_Policy; c->polEv.current_policy = (uint32_t)c->policy; msg->policy_event = &c->polEv; return eLeapRS_Success; }
    mock_sleep_ms(10); /* 100 fps */
    if (c->trackerBroken) return eLeapRS_Timeout;
    c->frameId++;
    if ((c->policy & eLeapPolicyFlag_Images) && (c->frameId % 5) == 0) {
        build_images(c); msg->type = eLeapEventType_Image; msg->image_event = &c->imgEv; return eLeapRS_Success;
    }
    double t = (mock_now_us() - c->t0) / 1000000.0;
    build_hand(c, t);
    msg->type = eLeapEventType_Tracking; msg->tracking_event = &c->trk;
    return eLeapRS_Success;
}
