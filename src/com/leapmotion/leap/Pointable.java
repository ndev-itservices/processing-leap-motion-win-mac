package com.leapmotion.leap;

/**
 * Base class of {@link Finger} and {@link Tool}.
 *
 * @author Tiago Martins
 */
public class Pointable {

    public enum Zone {
        ZONE_NONE(0), ZONE_HOVERING(1), ZONE_TOUCHING(2);

        private final int value;

        Zone(int v) { value = v; }

        public int swigValue() { return value; }

        public static Zone swigToEnum(int v) {
            for (Zone z : values()) if (z.value == v) return z;
            return ZONE_NONE;
        }
    }

    /** Depth (mm) of the virtual hovering zone in front of the touch plane (z = 0). */
    static final float TOUCH_ZONE_DEPTH = 100f;

    private static final Pointable INVALID = new Pointable();

    protected int id = -1;
    protected Hand hand;
    protected Frame frame;
    protected Vector tipPosition = Vector.zero();
    protected Vector tipVelocity = Vector.zero();
    protected Vector stabilizedTipPosition = Vector.zero();
    protected Vector direction = Vector.zero();
    protected float width, length, timeVisible;
    protected boolean extended, valid;

    protected Pointable() {
    }

    /** Copy constructor (used by Finger(Pointable) and Tool(Pointable)). */
    protected Pointable(Pointable p) {
        if (p == null) return;
        id = p.id; hand = p.hand; frame = p.frame; tipPosition = p.tipPosition; tipVelocity = p.tipVelocity;
        stabilizedTipPosition = p.stabilizedTipPosition; direction = p.direction; width = p.width;
        length = p.length; timeVisible = p.timeVisible; extended = p.extended; valid = p.valid;
    }

    public static Pointable invalid() { return INVALID; }

    public int id() { return id; }
    public Hand hand() { return hand == null ? Hand.invalid() : hand; }
    public Frame frame() { return frame == null ? Frame.invalid() : frame; }
    public Vector tipPosition() { return tipPosition; }
    public Vector tipVelocity() { return tipVelocity; }
    public Vector stabilizedTipPosition() { return stabilizedTipPosition; }
    public Vector direction() { return direction; }
    public float width() { return width; }
    public float length() { return length; }
    public float timeVisible() { return timeVisible; }
    public boolean isExtended() { return extended; }
    public boolean isValid() { return valid; }
    public boolean isFinger() { return false; }
    public boolean isTool() { return false; }

    /**
     * Distance to the virtual touch plane (z = 0): +1 far away, 0 touching, -1 fully through.
     * Approximation of the V2 behaviour (Hyperion has no touch emulation).
     */
    public float touchDistance() {
        float d = stabilizedTipPosition.getZ() / TOUCH_ZONE_DEPTH;
        if (d > 1) d = 1; else if (d < -1) d = -1;
        return d;
    }

    public Zone touchZone() {
        if (!valid) return Zone.ZONE_NONE;
        float d = touchDistance();
        if (d >= 1f) return Zone.ZONE_NONE;
        if (d > 0f) return Zone.ZONE_HOVERING;
        return Zone.ZONE_TOUCHING;
    }

    public void delete() { }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Pointable)) return false;
        Pointable p = (Pointable) o;
        return valid == p.valid && id == p.id && (frame == null ? p.frame == null : (p.frame != null && frame.id() == p.frame.id()));
    }

    @Override
    public int hashCode() { return id * 31 + (frame == null ? 0 : (int) frame.id()); }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " id:" + id + " tip:" + tipPosition;
    }
}
