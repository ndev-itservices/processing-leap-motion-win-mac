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
     * The ray slopes follow the V2 convention: horizontal = -(x + offset) / y, vertical = z / y,
     * with offset = +cameraOffset for the left image and -cameraOffset for the right one.
     *
     * @return pixel coordinates (x, y, 0), NaN when the point is behind the camera or no image was received
     */
    public Vector project(Vector position) {
        float y = position.getY();
        if (y <= 1f) return new Vector(Float.NaN, Float.NaN, 0);
        float offset = id == 0 ? cameraOffset : -cameraOffset;
        float h = -(position.getX() + offset) / y;
        float v = position.getZ() / y;
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
