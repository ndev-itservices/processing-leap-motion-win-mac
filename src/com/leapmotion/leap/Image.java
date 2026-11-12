package com.leapmotion.leap;

/**
 * One camera image (left or right infrared sensor).
 *
 * @author Tiago Martins
 */
public class Image {

    public enum FormatType {
        INFRARED(0), IBRG(1);

        private final int value;

        FormatType(int v) { value = v; }

        public int swigValue() { return value; }

        public static FormatType swigToEnum(int v) {
            for (FormatType t : values()) if (t.value == v) return t;
            return INFRARED;
        }
    }

    private static final Image INVALID = new Image(false, 0, 0, 0, 0, 0, 0, FormatType.INFRARED, new byte[0], null);

    private final boolean valid;
    private final int id, width, height, bpp;
    private final long sequenceId, timestamp;
    private final FormatType format;
    private final byte[] data;
    private final Controller controller;

    /** Half of the distance between the two cameras, in mm (original Leap Motion Controller: 20 mm). */
    public static float cameraOffset = 20f;

    Image(boolean valid, int id, int width, int height, int bpp, long sequenceId, long timestamp, FormatType format, byte[] data, Controller controller) {
        this.valid = valid; this.id = id; this.width = width; this.height = height; this.bpp = bpp;
        this.sequenceId = sequenceId; this.timestamp = timestamp; this.format = format; this.data = data;
        this.controller = controller;
    }

    /**
     * Maps a camera ray (slopes: x horizontal, y vertical, z ignored) to a pixel of this image,
     * correcting for lens distortion. Same semantics as Image.warp() in the V2 SDK.
     *
     * @return pixel coordinates (x, y, 0) or an invalid vector (NaN) when not available yet
     */
    public Vector warp(Vector ray) {
        Vector p = controller == null ? null : controller.rectilinearToPixel(id, new Vector(ray.getX(), ray.getY(), 1));
        return p == null ? new Vector(Float.NaN, Float.NaN, 0) : p;
    }

    /** Inverse of {@link #warp(Vector)}: pixel to ray slopes (x, y, 1). Same as Image.rectify() in V2. */
    public Vector rectify(Vector pixel) {
        Vector r = controller == null ? null : controller.pixelToRectilinear(id, pixel);
        return r == null ? new Vector(Float.NaN, Float.NaN, 1) : r;
    }

    /**
     * Projects a 3D tracking position (Leap coordinates, mm) onto this image.
     * <p>
     * When the service provides the camera's extrinsic matrix (Gemini 5.1+ / Hyperion), the point is
     * transformed into the camera frame and the ray (x/z, y/z, 1) is passed to LeapRectilinearToPixel.
     * Otherwise the V2 convention is used: horizontal = -(x + offset) / y, vertical = z / y, with
     * offset = +cameraOffset for the left image and -cameraOffset for the right one.
     *
     * @return pixel coordinates (x, y, 0), NaN when the point is behind the camera or no image was received
     */
    public Vector project(Vector position) {
        return project(position, projectionMode);
    }

    /** Projection mode used by {@link #project(Vector)}: one of the MODE_* constants (default MODE_EXTRINSIC). */
    public static int projectionMode = 0;

    /**
     * Default. Camera position taken from the extrinsic matrix (LeapExtrinsicCameraMatrix), then
     * the Leap Motion ray convention: horizontal slope = -(x - cx) / (y - cy), vertical slope =
     * (z - cz) / (y - cy), depth along the Leap y axis. Verified on an original Leap Motion
     * Controller with Hyperion 6.2.0 (the service reports an identity rotation and +-20 mm offsets).
     */
    public static final int MODE_EXTRINSIC = 0;
    /** Same convention with a fixed camera offset of {@link #cameraOffset} mm (no extrinsic matrix). */
    public static final int MODE_V2 = 1;
    /** Fixed offset, horizontal sign flipped: (x + o) / y, z / y. */
    public static final int MODE_V2_FLIP_H = 2;
    /** Fixed offset, vertical sign flipped: -(x + o) / y, -z / y. */
    public static final int MODE_V2_FLIP_V = 3;
    /** Fixed offset, both signs flipped. */
    public static final int MODE_V2_FLIP_HV = 4;
    /** Extrinsic matrix with depth along the camera z axis (standard pinhole); wrong for Hyperion. */
    public static final int MODE_EXTRINSIC_Z = 5;

    public static String projectionModeName(int mode) {
        switch (mode) {
            case MODE_EXTRINSIC: return "extrinsic offset, slopes -x/y, z/y (default)";
            case MODE_V2: return "fixed 20 mm offset, slopes -x/y, z/y";
            case MODE_V2_FLIP_H: return "fixed offset, slopes x/y, z/y";
            case MODE_V2_FLIP_V: return "fixed offset, slopes -x/y, -z/y";
            case MODE_V2_FLIP_HV: return "fixed offset, slopes x/y, -z/y";
            case MODE_EXTRINSIC_Z: return "extrinsic, depth along camera z";
            default: return "unknown";
        }
    }

    private float[] calibScale;   // cached (x, y) factors from calibration pixels to image pixels

    /**
     * Scale factors from the pixel coordinates returned by LeapRectilinearToPixel to the
     * coordinates of this image, computed from the principal point (pixel of the central ray):
     * if it sits at n times the image centre, coordinates are divided by n. For the original
     * Leap Motion Controller on Hyperion 6.2 the factors are 1 (the calibration matches the
     * delivered 640x240 image).
     *
     * @return {scaleX, scaleY}, or {1, 1} when unknown
     */
    public float[] calibrationScale() {
        if (calibScale != null) return calibScale;
        float[] sc = {1f, 1f};
        if (controller != null && width > 0 && height > 0) {
            Vector pp = controller.rectilinearToPixel(id, new Vector(0, 0, 1));
            if (pp != null && !Float.isNaN(pp.getX()) && !Float.isNaN(pp.getY()) && pp.getX() > 0 && pp.getY() > 0) {
                int nx = Math.round(pp.getX() * 2f / width);
                int ny = Math.round(pp.getY() * 2f / height);
                if (nx >= 2) sc[0] = 1f / nx;
                if (ny >= 2) sc[1] = 1f / ny;
                calibScale = sc;
            }
        }
        return sc;
    }

    /** Same as {@link #project(Vector)} with an explicit projection mode (see MODE_* constants). */
    public Vector project(Vector position, int mode) {
        Vector p = projectRaw(position, mode);
        if (Float.isNaN(p.getX())) return p;
        float[] sc = calibrationScale();
        return new Vector(p.getX() * sc[0], p.getY() * sc[1], 0);
    }

    /** Projection in the pixel frame of the calibration, without {@link #calibrationScale()}. */
    public Vector projectRaw(Vector position, int mode) {
        Vector nan = new Vector(Float.NaN, Float.NaN, 0);
        float x = position.getX(), y = position.getY(), z = position.getZ();
        if (mode == MODE_EXTRINSIC || mode == MODE_EXTRINSIC_Z) {
            float[] m = controller == null ? null : controller.extrinsicCameraMatrix(id);
            if (m == null) {
                mode = MODE_V2;
            } else {
                // p' = R^T (p - t); columns of R are m[0..2], m[4..6], m[8..10], t = m[12..14]
                float dx = x - m[12], dy = y - m[13], dz = z - m[14];
                float cx = m[0] * dx + m[1] * dy + m[2] * dz;
                float cy = m[4] * dx + m[5] * dy + m[6] * dz;
                float cz = m[8] * dx + m[9] * dy + m[10] * dz;
                if (mode == MODE_EXTRINSIC_Z) {
                    if (cz <= 1f) return nan;
                    return warp(new Vector(cx / cz, cy / cz, 0));
                }
                if (cy <= 1f) return nan;
                return warp(new Vector(-cx / cy, cz / cy, 0));
            }
        }
        if (y <= 1f) return nan;
        float offset = id == 0 ? cameraOffset : -cameraOffset;
        float h = -(x + offset) / y;
        float v = z / y;
        if (mode == MODE_V2_FLIP_H || mode == MODE_V2_FLIP_HV) h = -h;
        if (mode == MODE_V2_FLIP_V || mode == MODE_V2_FLIP_HV) v = -v;
        return warp(new Vector(h, v, 0));
    }

    public static Image invalid() { return INVALID; }

    public boolean isValid() { return valid; }
    /** 0 = left camera, 1 = right camera. */
    public int id() { return id; }
    public int width() { return width; }
    public int height() { return height; }
    public int bytesPerPixel() { return bpp; }
    public long sequenceId() { return sequenceId; }
    public long timestamp() { return timestamp; }
    public FormatType format() { return format; }

    /** Raw pixel data (width * height * bytesPerPixel), 8-bit greyscale for infrared images. */
    public byte[] data() { return data; }

    public void data(byte[] dst) {
        System.arraycopy(data, 0, dst, 0, Math.min(dst.length, data.length));
    }

    public int distortionWidth() { return 0; }
    public int distortionHeight() { return 0; }
    public float[] distortion() { return new float[0]; }
    public float rayOffsetX() { return 0.5f; }
    public float rayOffsetY() { return 0.5f; }
    public float rayScaleX() { return 0.125f; }
    public float rayScaleY() { return 0.125f; }

    public void delete() { }

    @Override
    public String toString() { return "Image " + id + " " + width + "x" + height + " seq:" + sequenceId; }
}
