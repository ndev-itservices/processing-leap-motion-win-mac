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

    private static final Image INVALID = new Image(false, 0, 0, 0, 0, 0, 0, FormatType.INFRARED, new byte[0]);

    private final boolean valid;
    private final int id, width, height, bpp;
    private final long sequenceId, timestamp;
    private final FormatType format;
    private final byte[] data;

    Image(boolean valid, int id, int width, int height, int bpp, long sequenceId, long timestamp, FormatType format, byte[] data) {
        this.valid = valid; this.id = id; this.width = width; this.height = height; this.bpp = bpp;
        this.sequenceId = sequenceId; this.timestamp = timestamp; this.format = format; this.data = data;
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
