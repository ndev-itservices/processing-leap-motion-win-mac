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

    /** Extrinsic matrix read as camera-to-Leap (documented): p_cam = R^T (p - t). */
    public static final int MODE_EXTRINSIC = 0;
    /** Extrinsic matrix read as Leap-to-camera: p_cam = R p + t. */
    public static final int MODE_EXTRINSIC_INVERSE = 1;
    /** V2 formula: horizontal = -(x + offset) / y, vertical = z / y. */
    public static final int MODE_V2 = 2;
    /** V2 formula with the horizontal sign flipped. */
    public static final int MODE_V2_FLIP_H = 3;
    /** V2 formula with the vertical sign flipped. */
    public static final int MODE_V2_FLIP_V = 4;
    /** V2 formula with both signs flipped. */
    public static final int MODE_V2_FLIP_HV = 5;

    public static String projectionModeName(int mode) {
        switch (mode) {
            case MODE_EXTRINSIC: return "extrinsic (camera to Leap)";
            case MODE_EXTRINSIC_INVERSE: return "extrinsic inverse (Leap to camera)";
            case MODE_V2: return "V2 slopes -(x+o)/y, z/y";
            case MODE_V2_FLIP_H: return "V2 slopes (x+o)/y, z/y";
            case MODE_V2_FLIP_V: return "V2 slopes -(x+o)/y, -z/y";
            case MODE_V2_FLIP_HV: return "V2 slopes (x+o)/y, -z/y";
            default: return "unknown";
        }
    }

    /** Same as {@link #project(Vector)} with an explicit projection mode (see MODE_* constants). */
    public Vector project(Vector position, int mode) {
        Vector nan = new Vector(Float.NaN, Float.NaN, 0);
        if (mode == MODE_EXTRINSIC || mode == MODE_EXTRINSIC_INVERSE) {
            float[] m = controller == null ? null : controller.extrinsicCameraMatrix(id);
            if (m == null) {
                mode = MODE_V2;
            } else {
                float cx, cy, cz;
                if (mode == MODE_EXTRINSIC) {
                    // p_cam = R^T (p - t); columns of R are m[0..2], m[4..6], m[8..10], t = m[12..14]
                    float dx = position.getX() - m[12], dy = position.getY() - m[13], dz = position.getZ() - m[14];
                    cx = m[0] * dx + m[1] * dy + m[2] * dz;
                    cy = m[4] * dx + m[5] * dy + m[6] * dz;
                    cz = m[8] * dx + m[9] * dy + m[10] * dz;
                } else {
                    // p_cam = R p + t
                    float x = position.getX(), y = position.getY(), z = position.getZ();
                    cx = m[0] * x + m[4] * y + m[8] * z + m[12];
                    cy = m[1] * x + m[5] * y + m[9] * z + m[13];
                    cz = m[2] * x + m[6] * y + m[10] * z + m[14];
                }
                if (cz <= 1f) return nan;
                return warp(new Vector(cx / cz, cy / cz, 0));
            }
        }
        float y = position.getY();
        if (y <= 1f) return nan;
        float offset = id == 0 ? cameraOffset : -cameraOffset;
        float h = -(position.getX() + offset) / y;
        float v = position.getZ() / y;
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
