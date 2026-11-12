package com.leapmotion.leap;

/**
 * A tracked finger with its four bones.
 *
 * @author Tiago Martins
 */
public class Finger extends Pointable {

    public enum Type {
        TYPE_THUMB(0), TYPE_INDEX(1), TYPE_MIDDLE(2), TYPE_RING(3), TYPE_PINKY(4);

        private final int value;

        Type(int v) { value = v; }

        public int swigValue() { return value; }

        public static Type swigToEnum(int v) {
            for (Type t : values()) if (t.value == v) return t;
            return TYPE_THUMB;
        }
    }

    public enum Joint {
        JOINT_MCP(0), JOINT_PIP(1), JOINT_DIP(2), JOINT_TIP(3);

        private final int value;

        Joint(int v) { value = v; }

        public int swigValue() { return value; }

        public static Joint swigToEnum(int v) {
            for (Joint j : values()) if (j.value == v) return j;
            return JOINT_TIP;
        }
    }

    private static final Finger INVALID = new Finger();

    private Type type = Type.TYPE_THUMB;
    private Bone[] bones = {Bone.invalid(), Bone.invalid(), Bone.invalid(), Bone.invalid()};

    Finger() {
    }

    /** V2 style conversion from a generic pointable; yields an invalid finger if it is not one. */
    public Finger(Pointable p) {
        super(p instanceof Finger ? p : null);
        if (p instanceof Finger) {
            Finger f = (Finger) p;
            this.type = f.type;
            this.bones = f.bones;
        }
    }

    /** Package constructor used by the frame builder. */
    Finger(Hand hand, int id, Type type, Bone[] bones, boolean extended, Vector tipVelocity, Vector stabilizedTip, float timeVisible) {
        this.hand = hand;
        this.frame = hand == null ? null : hand.frame;
        this.id = id;
        this.type = type;
        this.bones = bones;
        this.extended = extended;
        this.valid = true;
        Bone distal = bones[3];
        this.tipPosition = distal.nextJoint();
        this.direction = distal.direction();
        this.width = distal.width();
        this.length = bones[1].length() + bones[2].length() + bones[3].length();
        this.tipVelocity = tipVelocity;
        this.stabilizedTipPosition = stabilizedTip;
        this.timeVisible = timeVisible;
    }

    public static Finger invalid() { return INVALID; }

    public Type type() { return type; }

    public Bone bone(Bone.Type t) { return bones[t.swigValue()]; }

    public Vector jointPosition(Joint j) {
        switch (j) {
            case JOINT_MCP: return bones[0].nextJoint();
            case JOINT_PIP: return bones[1].nextJoint();
            case JOINT_DIP: return bones[2].nextJoint();
            default: return bones[3].nextJoint();
        }
    }

    @Override
    public boolean isFinger() { return valid; }

    @Override
    public String toString() { return "Finger " + type + " id:" + id + " tip:" + tipPosition; }
}
