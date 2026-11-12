package com.leapmotion.leap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main entry point: connects to the Ultraleap Hand Tracking service through LeapC and
 * exposes the tracking data with the Leap Motion V2 Java API.
 *
 * <pre>
 *   Controller controller = new Controller();
 *   controller.addListener(new Listener() {
 *       public void onFrame(Controller c) { Frame f = c.frame(); ... }
 *   });
 * </pre>
 *
 * @author Tiago Martins
 */
public class Controller {

    public enum PolicyFlag {
        POLICY_DEFAULT(0),
        POLICY_BACKGROUND_FRAMES(0x01),
        POLICY_IMAGES(0x02),
        POLICY_OPTIMIZE_HMD(0x04),
        POLICY_ALLOW_PAUSE_RESUME(0x08),
        POLICY_RAW_IMAGES(0x40),
        POLICY_MAP_POINTS(0x80),
        POLICY_OPTIMIZE_SCREENTOP(0x100);

        private final int value;

        PolicyFlag(int v) { value = v; }

        public int swigValue() { return value; }

        public static PolicyFlag swigToEnum(int v) {
            for (PolicyFlag f : values()) if (f.value == v) return f;
            return POLICY_DEFAULT;
        }
    }

    public static final String VERSION = "4.0.0";
    private static final int HISTORY = 120;
    private static final long NATIVE_MASK = 0x1FF;

    private static boolean verbose = Boolean.getBoolean("leap.verbose");
    private static boolean bannerPrinted = false;

    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private final GestureRecognizer recognizer = new GestureRecognizer();
    private final Config config = new Config(this);

    private long handle;
    private boolean opened;
    private String openError;
    private Thread thread;
    private volatile boolean running;

    private volatile Frame frame = Frame.invalid();
    private final Frame[] history = new Frame[HISTORY];
    private int historyPos = 0;
    private volatile boolean serviceConnected;
    private volatile int deviceCount;
    private volatile boolean connected;
    private volatile ImageList images = new ImageList();
    private volatile Device device = Device.invalid();

    private final long[] meta = new long[LeapJNI.META_LEN];
    private final float[] data = new float[LeapJNI.MAX_HANDS * LeapJNI.HAND_STRIDE];
    private byte[] imgLeft, imgRight;
    private boolean warnedNotRunning;

    public Controller() {
        this(null);
    }

    public Controller(Listener listener) {
        if (!LeapJNI.isLoaded()) {
            openError = "libLeapJava native library could not be loaded:\n  " + LeapJNI.getLoadError();
            System.err.println("# Leap Motion: " + openError);
        } else {
            handle = LeapJNI.nCreate();
            String leapc = LeapJNI.findLeapC();
            int rc = LeapJNI.nOpen(handle, leapc);
            if (rc == 0) {
                opened = true;
            } else {
                openError = LeapJNI.nLastError(handle);
                System.err.println("# Leap Motion: cannot start LeapC (" + rc + "): " + openError);
                if (rc == -1) {
                    System.err.println("# Leap Motion: Ultraleap Hand Tracking is not installed. Run driver/install_driver.sh "
                            + "or install https://www.ultraleap.com/downloads/leap-controller (macOS Apple Silicon).");
                }
            }
        }
        if (listener != null) addListener(listener);
        if (opened) start();
    }

    /* ------------------------------------------------------------- thread */

    private void start() {
        running = true;
        thread = new Thread(new Runnable() {
            public void run() { loop(); }
        }, "LeapMotion-Controller");
        thread.setDaemon(true);
        thread.start();
    }

    private void loop() {
        long lastStatusCheck = 0;
        while (running) {
            int ev;
            try {
                ev = LeapJNI.nWait(handle, 250);
            } catch (Throwable t) {
                break;
            }
            if (!running) break;
            if ((ev & LeapJNI.EV_CONNECTION) != 0) {
                boolean now = LeapJNI.nIsConnected(handle);
                if (now != serviceConnected) {
                    serviceConnected = now;
                    if (now) { log("connected to the Ultraleap tracking service"); fire("serviceConnect"); }
                    else { log("tracking service disconnected"); fire("serviceDisconnect"); }
                }
                updateDevice();
            }
            if ((ev & LeapJNI.EV_DEVICE) != 0) {
                updateDevice();
                fire("deviceChange");
            }
            if ((ev & LeapJNI.EV_POLICY) != 0) {
                log("policy flags now 0x" + Long.toHexString(LeapJNI.nGetPolicy(handle)));
            }
            if ((ev & LeapJNI.EV_LOG) != 0) {
                String m = LeapJNI.nLastLog(handle);
                log("service: " + m);
                for (Listener l : listeners) {
                    try { l.onLogMessage(this, 3, now(), m); } catch (Throwable t) { report(t); }
                }
            }
            if ((ev & LeapJNI.EV_IMAGE) != 0) {
                readImages();
                for (Listener l : listeners) {
                    try { l.onImages(this); } catch (Throwable t) { report(t); }
                }
            }
            if ((ev & LeapJNI.EV_FRAME) != 0) {
                readFrame();
            }
            long t = System.currentTimeMillis();
            if (!serviceConnected && t - lastStatusCheck > 2000) {
                lastStatusCheck = t;
                int st = LeapJNI.nConnectionStatus(handle);
                if (st == 3 && !warnedNotRunning) {
                    warnedNotRunning = true;
                    System.err.println("# Leap Motion: the Ultraleap tracking service is not running. "
                            + "Start 'Ultraleap Hand Tracking' (or run driver/install_driver.sh).");
                }
            }
        }
    }

    private void updateDevice() {
        int count = LeapJNI.nDeviceCount(handle);
        deviceCount = count;
        float[] info = new float[7];
        if (count > 0 && LeapJNI.nDeviceInfo(handle, info)) {
            device = new Device(true, (int) info[0], (int) info[1], (int) info[2], info[3] / 1000f, info[4], info[5],
                    info[6] / 1000f, LeapJNI.nDeviceSerial(handle));
        } else {
            device = Device.invalid();
        }
        boolean c = serviceConnected && count > 0;
        if (c != connected) {
            connected = c;
            if (c) { log("device connected: " + device); fire("connect"); }
            else { log("device disconnected"); fire("disconnect"); }
        }
    }

    private void readFrame() {
        int n = LeapJNI.nReadFrame(handle, meta, data);
        if (n < 0) return;
        float fps = Float.intBitsToFloat((int) meta[3]);
        Frame prev = frame;
        Frame f = new Frame(meta[0], meta[1], fps, data, n, prev.isValid() ? prev : null, this);
        if (recognizer.anyEnabled()) {
            f.setGestures(recognizer.process(f));
        }
        synchronized (history) {
            history[historyPos] = f;
            historyPos = (historyPos + 1) % HISTORY;
        }
        frame = f;
        if (!connected && serviceConnected && deviceCount == 0) {
            // frames imply a device even if no device event was seen yet
            deviceCount = 1;
            updateDevice();
        }
        for (Listener l : listeners) {
            try { l.onFrame(this); } catch (Throwable t) { report(t); }
        }
    }

    private void readImages() {
        long[] m = new long[LeapJNI.META_LEN];
        LeapJNI.nReadImages(handle, m, null, null);
        int bytes = (int) m[7];
        if (bytes <= 0) return;
        if (imgLeft == null || imgLeft.length != bytes) { imgLeft = new byte[bytes]; imgRight = new byte[bytes]; }
        int copied = LeapJNI.nReadImages(handle, m, imgLeft, imgRight);
        if (copied <= 0) return;
        int w = (int) m[2], h = (int) m[3], bpp = (int) m[4];
        Image.FormatType fmt = m[5] == 0x49425247L ? Image.FormatType.IBRG : Image.FormatType.INFRARED;
        List<Image> l = new ArrayList<Image>(2);
        l.add(new Image(true, 0, w, h, bpp, m[6], m[1], fmt, imgLeft.clone(), this));
        if (copied > 1) l.add(new Image(true, 1, w, h, bpp, m[6], m[1], fmt, imgRight.clone(), this));
        images = new ImageList(l);
    }

    private void fire(String what) {
        for (Listener l : listeners) {
            try {
                if ("connect".equals(what)) l.onConnect(this);
                else if ("disconnect".equals(what)) l.onDisconnect(this);
                else if ("serviceConnect".equals(what)) l.onServiceConnect(this);
                else if ("serviceDisconnect".equals(what)) l.onServiceDisconnect(this);
                else if ("deviceChange".equals(what)) l.onDeviceChange(this);
            } catch (Throwable t) {
                report(t);
            }
        }
    }

    private static void report(Throwable t) {
        System.err.println("# Leap Motion: exception in listener: " + t);
        t.printStackTrace();
    }

    private static void log(String msg) {
        if (verbose) System.out.println("# Leap Motion: " + msg);
    }

    public static void setVerbose(boolean v) { verbose = v; }

    /* ---------------------------------------------------------- listeners */

    public boolean addListener(Listener listener) {
        if (listener == null || listeners.contains(listener)) return false;
        listeners.add(listener);
        try {
            listener.onInit(this);
            if (serviceConnected) listener.onServiceConnect(this);
            if (connected) listener.onConnect(this);
        } catch (Throwable t) {
            report(t);
        }
        return true;
    }

    public boolean removeListener(Listener listener) {
        boolean removed = listeners.remove(listener);
        if (removed) {
            try { listener.onExit(this); } catch (Throwable t) { report(t); }
        }
        return removed;
    }

    /* --------------------------------------------------------------- state */

    /** True when connected to the tracking service and a device is plugged in. */
    public boolean isConnected() { return connected; }

    public boolean isServiceConnected() { return serviceConnected; }

    /** The service streams to this application regardless of focus. */
    public boolean hasFocus() { return true; }

    public boolean isOpen() { return opened; }

    public String openError() { return openError; }

    public Frame frame() { return frame; }

    /** @param historyIndex 0 = newest frame, 1 = previous, ... (up to 119). */
    public Frame frame(int historyIndex) {
        if (historyIndex <= 0) return frame;
        if (historyIndex >= HISTORY) return Frame.invalid();
        synchronized (history) {
            int idx = ((historyPos - 1 - historyIndex) % HISTORY + HISTORY) % HISTORY;
            Frame f = history[idx];
            return f == null ? Frame.invalid() : f;
        }
    }

    GestureList gesturesBetween(long sinceIdExclusive, long untilIdInclusive) {
        List<Gesture> out = new ArrayList<Gesture>();
        synchronized (history) {
            for (int i = 1; i <= HISTORY; i++) {
                int idx = ((historyPos - i) % HISTORY + HISTORY) % HISTORY;
                Frame f = history[idx];
                if (f == null) break;
                if (f.id() > untilIdInclusive) continue;
                if (f.id() <= sinceIdExclusive) break;
                out.addAll(0, f.gestures().asList());
            }
        }
        return new GestureList(out);
    }

    public DeviceList devices() {
        List<Device> l = new ArrayList<Device>(1);
        Device d = device;
        if (d.isValid()) l.add(d);
        return new DeviceList(l);
    }

    public ImageList images() { return images; }

    public ImageList rawImages() { return images; }

    public Config config() { return config; }

    GestureRecognizer recognizer() { return recognizer; }

    /** Microseconds since an arbitrary epoch (LeapGetNow). */
    public long now() { return opened ? LeapJNI.nNow(handle) : System.nanoTime() / 1000; }

    /* -------------------------------------------------------------- policy */

    public void setPolicy(PolicyFlag flag) {
        if (!opened || flag == null) return;
        LeapJNI.nSetPolicy(handle, flag.swigValue() & NATIVE_MASK, 0);
    }

    public void clearPolicy(PolicyFlag flag) {
        if (!opened || flag == null) return;
        LeapJNI.nSetPolicy(handle, 0, flag.swigValue() & NATIVE_MASK);
    }

    public boolean isPolicySet(PolicyFlag flag) {
        if (flag == null) return false;
        if (flag == PolicyFlag.POLICY_DEFAULT) return true;
        if (!opened) return false;
        long flags = LeapJNI.nGetPolicy(handle) | LeapJNI.nGetDesiredPolicy(handle);
        return (flags & flag.swigValue()) != 0;
    }

    /** @deprecated use {@link #setPolicy(PolicyFlag)}. */
    @Deprecated
    public void setPolicyFlags(PolicyFlag flags) { setPolicy(flags); }

    /** @deprecated use {@link #isPolicySet(PolicyFlag)}. */
    @Deprecated
    public PolicyFlag policyFlags() {
        if (!opened) return PolicyFlag.POLICY_DEFAULT;
        long f = LeapJNI.nGetPolicy(handle);
        return PolicyFlag.swigToEnum((int) f);
    }

    /** 0 desktop, 1 head mounted, 2 screen top. */
    public void setTrackingMode(int mode) {
        if (opened) LeapJNI.nSetTrackingMode(handle, mode);
    }

    public int trackingMode() { return opened ? LeapJNI.nGetTrackingMode(handle) : -1; }

    /* ------------------------------------------------------------ gestures */

    public void enableGesture(Gesture.Type type) { enableGesture(type, true); }

    public void enableGesture(Gesture.Type type, boolean enable) { recognizer.enable(type, enable); }

    public boolean isGestureEnabled(Gesture.Type type) { return recognizer.isEnabled(type); }

    /* -------------------------------------------------------------- config */

    boolean saveConfigValue(String key, Object value) {
        if (!opened) return false;
        if (value instanceof Boolean) return LeapJNI.nSaveConfig(handle, key, 1, (Boolean) value, 0, 0, null);
        if (value instanceof Integer) return LeapJNI.nSaveConfig(handle, key, 2, false, (Integer) value, 0, null);
        if (value instanceof Number) return LeapJNI.nSaveConfig(handle, key, 3, false, 0, ((Number) value).floatValue(), null);
        return LeapJNI.nSaveConfig(handle, key, 4, false, 0, 0, String.valueOf(value));
    }

    /* ------------------------------------------------------ image mapping */

    /**
     * Maps a camera ray to a pixel of the raw image (LeapRectilinearToPixel).
     *
     * @param camera 0 = left image, 1 = right image
     * @param ray    direction in camera coordinates: x along the image width, y along the image
     *               height, z = 1 (slopes). See {@link Image#warp(Vector)} for the 3D convention.
     * @return pixel (x, y, 0) or null when no image has been received yet
     */
    public Vector rectilinearToPixel(int camera, Vector ray) {
        if (!opened || ray == null) return null;
        float[] o = new float[2];
        float z = ray.getZ() == 0 ? 1f : ray.getZ();
        if (!LeapJNI.nRectilinearToPixel(handle, camera + 1, ray.getX() / z, ray.getY() / z, 1f, o)) return null;
        return new Vector(o[0], o[1], 0);
    }

    /** Inverse of {@link #rectilinearToPixel(int, Vector)}: pixel to ray direction (x, y, 1). */
    public Vector pixelToRectilinear(int camera, Vector pixel) {
        if (!opened || pixel == null) return null;
        float[] o = new float[2];
        if (!LeapJNI.nPixelToRectilinear(handle, camera + 1, pixel.getX(), pixel.getY(), o)) return null;
        return new Vector(o[0], o[1], 1);
    }

    /**
     * Camera-to-Leap rigid transform of a camera (LeapExtrinsicCameraMatrix): 4x4, column major,
     * [R t; 0 1]. Returns null when the service does not provide it (older Gemini versions).
     *
     * @param camera 0 = left, 1 = right
     */
    public float[] extrinsicCameraMatrix(int camera) {
        if (!opened) return null;
        float[] m = new float[16];
        return LeapJNI.nExtrinsicCameraMatrix(handle, camera + 1, m) ? m : null;
    }

    /* ------------------------------------------------------------- version */

    /** Version of the LeapC client library, e.g. "6.2.0", or "" when unavailable. */
    public String clientVersion() { return version(0); }

    /** Version of the running tracking service, or "" when unavailable. */
    public String serviceVersion() { return version(2); }

    private String version(int part) {
        if (!opened) return "";
        int[] v = new int[3];
        if (!LeapJNI.nVersion(handle, part, v)) return "";
        return v[0] + "." + v[1] + "." + v[2];
    }

    /* ------------------------------------------------------------ shutdown */

    /** Stops the controller thread and releases native resources. */
    public synchronized void delete() {
        if (!running && handle == 0) return;
        running = false;
        for (Listener l : listeners) {
            try { l.onExit(this); } catch (Throwable t) { report(t); }
        }
        listeners.clear();
        if (handle != 0) {
            try { LeapJNI.nClose(handle); } catch (Throwable t) { /* ignore */ }
            if (thread != null) {
                try { thread.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            try { LeapJNI.nDestroy(handle); } catch (Throwable t) { /* ignore */ }
            handle = 0;
        }
        opened = false;
    }
}
