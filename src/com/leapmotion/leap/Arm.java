package com.leapmotion.leap;

/**
 * The forearm of a hand: elbow (prev joint) to wrist (next joint).
 *
 * @author Tiago Martins
 */
public class Arm {

    private static final Arm INVALID = new Arm(Vector.zero(), Vector.zero(), 0, new float[]{0, 0, 0, 1}, false);

    private final Vector elbow, wrist;
    private final float width;
    private final float[] rotation;
    private final boolean valid;

    Arm(Vector elbow, Vector wrist, float width, float[] rotation, boolean valid) {
        this.elbow = elbow; this.wrist = wrist; this.width = width; this.rotation = rotation; this.valid = valid;
    }

    public static Arm invalid() { return INVALID; }

    public Vector elbowPosition() { return elbow; }
    public Vector wristPosition() { return wrist; }
    public Vector center() { return elbow.plus(wrist).times(0.5f); }
    public Vector direction() { return wrist.minus(elbow).normalized(); }
    public float width() { return width; }
    public boolean isValid() { return valid; }

    public Matrix basis() {
        Matrix m = Matrix.fromQuaternion(rotation[0], rotation[1], rotation[2], rotation[3]);
        m.setOrigin(center());
        return m;
    }

    public void delete() { }

    @Override
    public String toString() { return "Arm " + elbow + " -> " + wrist; }
}
