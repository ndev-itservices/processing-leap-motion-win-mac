package com.leapmotion.leap;

/**
 * The physical tracking camera.
 *
 * @author Tiago Martins
 */
public class Device {

    public enum Type {
        TYPE_PERIPHERAL(0x0003), TYPE_LAPTOP(0x1102), TYPE_KEYBOARD(0x1201), TYPE_RIGEL(0x1202),
        TYPE_SIR170(0x1203), TYPE_3DI(0x1204), TYPE_LMC2(0x1206), TYPE_UNKNOWN(0);

        private final int value;

        Type(int v) { value = v; }

        public int swigValue() { return value; }

        public static Type swigToEnum(int v) {
            for (Type t : values()) if (t.value == v) return t;
            return TYPE_UNKNOWN;
        }
    }

    private static final Device INVALID = new Device(false, 0, 0, 0, 0, 0, 0, 0, "");

    private final boolean valid;
    private final int status, caps, pid;
    private final float baselineMm, hFov, vFov, rangeMm;
    private final String serial;

    Device(boolean valid, int status, int caps, int pid, float baselineMm, float hFov, float vFov, float rangeMm, String serial) {
        this.valid = valid; this.status = status; this.caps = caps; this.pid = pid; this.baselineMm = baselineMm;
        this.hFov = hFov; this.vFov = vFov; this.rangeMm = rangeMm; this.serial = serial == null ? "" : serial;
    }

    public static Device invalid() { return INVALID; }

    public boolean isValid() { return valid; }
    /** Horizontal field of view in radians. */
    public float horizontalViewAngle() { return hFov; }
    /** Vertical field of view in radians. */
    public float verticalViewAngle() { return vFov; }
    /** Maximum tracking range in millimetres. */
    public float range() { return rangeMm; }
    /** Distance between the two cameras in millimetres. */
    public float baseline() { return baselineMm; }
    public String serialNumber() { return serial; }
    public Type type() { return Type.swigToEnum(pid); }
    public int status() { return status; }
    public int capabilities() { return caps; }
    public boolean isStreaming() { return valid && (status & 0x1) != 0; }
    public boolean isSmudged() { return valid && (status & 0x8) != 0; }
    public boolean isLightingBad() { return false; }
    public boolean isEmbedded() { return type() != Type.TYPE_PERIPHERAL && type() != Type.TYPE_LMC2 && type() != Type.TYPE_UNKNOWN; }

    public float distanceToBoundary(Vector position) {
        if (!valid) return 0;
        float r = range();
        return r - position.magnitude();
    }

    public void delete() { }

    @Override
    public String toString() {
        if (!valid) return "Device (invalid)";
        return "Device " + type() + " serial:" + serial + " hFov:" + hFov + " vFov:" + vFov + " range:" + rangeMm + "mm";
    }
}
