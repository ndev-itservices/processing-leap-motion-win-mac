package com.leapmotion.leap;

/**
 * A finger bone (or the arm), defined by its two joints.
 *
 * @author Tiago Martins
 */
public class Bone {

    public enum Type {
        TYPE_METACARPAL(0), TYPE_PROXIMAL(1), TYPE_INTERMEDIATE(2), TYPE_DISTAL(3);

        private final int value;

        Type(int v) { value = v; }

        public int swigValue() { return value; }

        public static Type swigToEnum(int v) {
            for (Type t : values()) if (t.value == v) return t;
            return TYPE_METACARPAL;
        }
    }

    private static final Bone INVALID = new Bone(Type.TYPE_METACARPAL, Vector.zero(), Vector.zero(), 0,
            new float[]{0, 0, 0, 1}, false);

    private final Type type;
    private final Vector prevJoint, nextJoint;
    private final float width;
    private final float[] rotation;
    private final boolean valid;

    Bone(Type type, Vector prev, Vector next, float width, float[] rotation, boolean valid) {
        this.type = type; this.prevJoint = prev; this.nextJoint = next; this.width = width;
        this.rotation = rotation; this.valid = valid;
    }

    public static Bone invalid() { return INVALID; }

    public Type type() { return type; }
    public Vector prevJoint() { return prevJoint; }
    public Vector nextJoint() { return nextJoint; }
    public Vector center() { return prevJoint.plus(nextJoint).times(0.5f); }
    public Vector direction() { return nextJoint.minus(prevJoint).normalized(); }
    public float length() { return prevJoint.distanceTo(nextJoint); }
    public float width() { return width; }
    public boolean isValid() { return valid; }

    /** Rotation quaternion (x, y, z, w) of this bone, as reported by LeapC. */
    public float[] rotation() { return rotation.clone(); }

    public Matrix basis() {
        Matrix m = Matrix.fromQuaternion(rotation[0], rotation[1], rotation[2], rotation[3]);
        m.setOrigin(center());
        return m;
    }

    public void delete() { }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Bone)) return false;
        Bone b = (Bone) o;
        return valid == b.valid && type == b.type && prevJoint.equals(b.prevJoint) && nextJoint.equals(b.nextJoint);
    }

    @Override
    public int hashCode() { return type.hashCode() * 31 + prevJoint.hashCode(); }

    @Override
    public String toString() { return "Bone " + type + " " + prevJoint + " -> " + nextJoint; }
}
