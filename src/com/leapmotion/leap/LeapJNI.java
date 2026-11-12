package com.leapmotion.leap;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Native bridge to the Ultraleap LeapC client library (Gemini 5.x / Hyperion 6.x).
 * <p>
 * This class replaces the SWIG-generated LeapJNI of the Leap Motion V2 SDK with a
 * small hand-written JNI layer (native/leapjni.c). It is package-private by design:
 * sketches should use {@link Controller}, {@link Frame}, {@link Hand} ... exactly as
 * with the original V2 Java API.
 *
 * @author Tiago Martins
 */
final class LeapJNI {

    /* Keep in sync with native/leapjni.c */
    static final int HAND_STRIDE = 272;
    static final int MAX_HANDS = 4;
    static final int META_LEN = 8;

    static final int EV_FRAME = 0x01;
    static final int EV_CONNECTION = 0x02;
    static final int EV_DEVICE = 0x04;
    static final int EV_IMAGE = 0x08;
    static final int EV_POLICY = 0x10;
    static final int EV_LOG = 0x20;
    static final int EV_TRACKINGMODE = 0x40;

    /* hand block offsets */
    static final int H_ID = 0, H_TYPE = 1, H_CONF = 2, H_VISIBLE = 3, H_PINCH_DIST = 4, H_GRAB_ANGLE = 5,
            H_PINCH = 6, H_GRAB = 7, H_FLAGS = 8, H_PALM_POS = 9, H_PALM_STAB = 12, H_PALM_VEL = 15,
            H_PALM_NORMAL = 18, H_PALM_WIDTH = 21, H_PALM_DIR = 22, H_PALM_QUAT = 25, H_DIGITS = 29,
            D_STRIDE = 46, D_ID = 0, D_EXTENDED = 1, D_BONES = 2, B_STRIDE = 11, H_ARM = 259;

    private static boolean loaded = false;
    private static String loadError = null;

    static {
        load();
    }

    private LeapJNI() {
    }

    static boolean isLoaded() {
        return loaded;
    }

    static String getLoadError() {
        return loadError;
    }

    private static synchronized void load() {
        if (loaded) return;
        List<String> errors = new ArrayList<String>();
        String explicit = System.getProperty("leap.native.path");
        if (explicit != null) {
            if (tryLoad(new File(explicit), errors)) return;
        }
        try {
            System.loadLibrary("LeapJava");
            loaded = true;
            return;
        } catch (UnsatisfiedLinkError e) {
            errors.add("System.loadLibrary(LeapJava): " + e.getMessage());
        }
        // Fallback: look next to the jar that contains this class (Processing library layout).
        try {
            URL src = LeapJNI.class.getProtectionDomain().getCodeSource().getLocation();
            File jar = new File(src.toURI());
            File libDir = jar.isDirectory() ? jar : jar.getParentFile();
            String name = System.mapLibraryName("LeapJava");
            String[] subs = {variant(), "macos-aarch64", "macos-x86_64", "macosx", "", "linux-aarch64", "linux-amd64", "windows-amd64"};
            for (String sub : subs) {
                File f = sub.isEmpty() ? new File(libDir, name) : new File(new File(libDir, sub), name);
                if (f.isFile() && tryLoad(f, errors)) return;
            }
        } catch (Exception e) {
            errors.add("code source lookup: " + e);
        }
        loadError = String.join("\n  ", errors);
    }

    private static boolean tryLoad(File f, List<String> errors) {
        try {
            System.load(f.getAbsolutePath());
            loaded = true;
            return true;
        } catch (UnsatisfiedLinkError e) {
            errors.add(f + ": " + e.getMessage());
            return false;
        }
    }

    /** Processing 4 style platform variant, e.g. macos-aarch64. */
    static String variant() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String p = os.contains("mac") ? "macos" : os.contains("win") ? "windows" : "linux";
        String a = (arch.contains("aarch64") || arch.contains("arm64")) ? "aarch64"
                : (arch.contains("64") ? (p.equals("macos") ? "x86_64" : "amd64") : arch);
        return p + "-" + a;
    }

    /** Locate the LeapC client library of the Ultraleap Hand Tracking installation. */
    static String findLeapC() {
        List<String> candidates = new ArrayList<String>();
        String prop = System.getProperty("leap.leapc.path");
        if (prop != null) candidates.add(prop);
        String env = System.getenv("LEAPC_PATH");
        if (env != null) candidates.add(env);
        String home = System.getProperty("user.home", "");
        String[] apps = {
                "/Applications/Ultraleap Hand Tracking.app/Contents/LeapSDK/lib/",
                home + "/Applications/Ultraleap Hand Tracking.app/Contents/LeapSDK/lib/",
                "/Applications/Ultraleap Hand Tracking Service.app/Contents/LeapSDK/lib/",
                "/Library/Application Support/Ultraleap/LeapSDK/lib/",
                home + "/Library/Application Support/Ultraleap/LeapSDK/lib/",
                "/usr/lib/ultraleap-hand-tracking-service/",
                "/opt/ultraleap/LeapSDK/lib/"
        };
        String[] names = {"libLeapC.6.dylib", "libLeapC.dylib", "libLeapC.5.dylib", "libLeapC.so", "libLeapC.so.6", "libLeapC.so.5", "LeapC.dll"};
        for (String a : apps) for (String n : names) candidates.add(a + n);
        for (String c : candidates) {
            if (new File(c).isFile()) return c;
        }
        return null;
    }

    /* ------------------------------------------------------------------ */
    /* Native methods (see native/leapjni.c)                              */

    static native long nCreate();

    /** @return 0 on success; -1 libLeapC not found; -2 symbol missing; -3/-4 connection errors. */
    static native int nOpen(long h, String leapcPath);

    static native void nClose(long h);

    static native void nDestroy(long h);

    /** Blocks until an event is pending or the timeout elapses. Returns and clears the pending bits. */
    static native int nWait(long h, int timeoutMs);

    static native boolean nIsConnected(long h);

    /** 0 not connected, 1 connected, 2 handshake incomplete, 3 service not running. */
    static native int nConnectionStatus(long h);

    static native int nDeviceCount(long h);

    static native boolean nDeviceInfo(long h, float[] out7);

    static native String nDeviceSerial(long h);

    static native void nSetPolicy(long h, long set, long clear);

    static native long nGetPolicy(long h);

    static native long nGetDesiredPolicy(long h);

    static native int nReadFrame(long h, long[] meta, float[] data);

    static native int nReadImages(long h, long[] meta, byte[] left, byte[] right);

    static native int nSetTrackingMode(long h, int mode);

    static native int nGetTrackingMode(long h);

    static native boolean nSaveConfig(long h, String key, int type, boolean b, int i, float f, String s);

    static native boolean nVersion(long h, int part, int[] out3);

    static native long nNow(long h);

    static native String nLastError(long h);

    static native String nLastLog(long h);

    static native int nBridgeVersion();
}
