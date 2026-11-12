package com.leapmotion.leap;

import java.util.ArrayList;
import java.util.List;

/**
 * A tracked hand.
 *
 * @author Tiago Martins
 */
public class Hand {

    private static final Hand INVALID = new Hand();

    Frame frame;
    private int id = -1;
    private boolean left, valid;
    private float confidence, grabStrength, grabAngle, pinchStrength, pinchDistance, timeVisible, palmWidth;
    private Vector palmPosition = Vector.zero(), stabilizedPalmPosition = Vector.zero(), palmVelocity = Vector.zero(),
            palmNormal = Vector.zero(), direction = Vector.zero();
    private float[] orientation = {0, 0, 0, 1};
    private Arm arm = Arm.invalid();
    private FingerList fingers = new FingerList();
    private PointableList pointables = new PointableList();
    private final ToolList tools = new ToolList();

    Hand() {
    }

    /** Builds a hand from one block of the native float buffer. */
    Hand(Frame frame, float[] d, int o, Frame prev, Controller ctrl) {
        this.frame = frame;
        this.valid = true;
        this.id = (int) d[o + LeapJNI.H_ID];
        this.left = d[o + LeapJNI.H_TYPE] < 0.5f;
        this.confidence = d[o + LeapJNI.H_CONF];
        this.timeVisible = d[o + LeapJNI.H_VISIBLE];
        this.pinchDistance = d[o + LeapJNI.H_PINCH_DIST];
        this.grabAngle = d[o + LeapJNI.H_GRAB_ANGLE];
        this.pinchStrength = d[o + LeapJNI.H_PINCH];
        this.grabStrength = d[o + LeapJNI.H_GRAB];
        this.palmPosition = vec(d, o + LeapJNI.H_PALM_POS);
        this.stabilizedPalmPosition = vec(d, o + LeapJNI.H_PALM_STAB);
        this.palmVelocity = vec(d, o + LeapJNI.H_PALM_VEL);
        this.palmNormal = vec(d, o + LeapJNI.H_PALM_NORMAL);
        this.palmWidth = d[o + LeapJNI.H_PALM_WIDTH];
        this.direction = vec(d, o + LeapJNI.H_PALM_DIR);
        this.orientation = quat(d, o + LeapJNI.H_PALM_QUAT);

        int a = o + LeapJNI.H_ARM;
        this.arm = new Arm(vec(d, a), vec(d, a + 3), d[a + 6], quat(d, a + 7), true);

        Hand prevHand = prev == null ? null : prev.hand(id);
        double dt = 0;
        if (prevHand != null && prevHand.isValid() && prev != null) {
            dt = (frame.timestamp() - prev.timestamp()) / 1000000.0;
            if (dt <= 0 || dt > 0.5) dt = 0;
        }
        List<Finger> fl = new ArrayList<Finger>(5);
        for (int i = 0; i < 5; i++) {
            int b = o + LeapJNI.H_DIGITS + i * LeapJNI.D_STRIDE;
            boolean ext = d[b + LeapJNI.D_EXTENDED] > 0.5f;
            Bone[] bones = new Bone[4];
            for (int k = 0; k < 4; k++) {
                int bo = b + LeapJNI.D_BONES + k * LeapJNI.B_STRIDE;
                bones[k] = new Bone(Bone.Type.swigToEnum(k), vec(d, bo), vec(d, bo + 3), d[bo + 6], quat(d, bo + 7), true);
            }
            int fid = id * 10 + i;
            Vector tip = bones[3].nextJoint();
            Vector vel = Vector.zero();
            Vector stab = tip;
            if (dt > 0) {
                Finger pf = prevHand.fingerOfType(i);
                if (pf != null && pf.isValid()) {
                    vel = tip.minus(pf.tipPosition()).divide((float) dt);
                    float speed = vel.magnitude();
                    float alpha = speed / 400f;
                    if (alpha < 0.15f) alpha = 0.15f; else if (alpha > 1f) alpha = 1f;
                    Vector ps = pf.stabilizedTipPosition();
                    stab = ps.plus(tip.minus(ps).times(alpha));
                }
            }
            fl.add(new Finger(this, fid, Finger.Type.swigToEnum(i), bones, ext, vel, stab, timeVisible));
        }
        this.fingers = new FingerList(fl);
        this.pointables = new PointableList(fl);
    }

    private static Vector vec(float[] d, int o) { return new Vector(d[o], d[o + 1], d[o + 2]); }
    private static float[] quat(float[] d, int o) { return new float[]{d[o], d[o + 1], d[o + 2], d[o + 3]}; }

    Finger fingerOfType(int typeIndex) {
        for (Finger f : fingers) if (f.type().swigValue() == typeIndex) return f;
        return null;
    }

    public static Hand invalid() { return INVALID; }

    public int id() { return id; }
    public Frame frame() { return frame == null ? Frame.invalid() : frame; }
    public boolean isValid() { return valid; }
    public boolean isLeft() { return valid && left; }
    public boolean isRight() { return valid && !left; }
    public float confidence() { return confidence; }
    public float grabStrength() { return grabStrength; }
    public float grabAngle() { return grabAngle; }
    public float pinchStrength() { return pinchStrength; }
    public float pinchDistance() { return pinchDistance; }
    public float timeVisible() { return timeVisible; }
    public float palmWidth() { return palmWidth; }
    public Vector palmPosition() { return palmPosition; }
    public Vector stabilizedPalmPosition() { return stabilizedPalmPosition; }
    public Vector palmVelocity() { return palmVelocity; }
    public Vector palmNormal() { return palmNormal; }
    public Vector direction() { return direction; }
    public Vector wristPosition() { return arm.wristPosition(); }
    public Arm arm() { return arm; }
    public FingerList fingers() { return fingers; }
    public PointableList pointables() { return pointables; }
    public ToolList tools() { return tools; }

    public Finger finger(int fingerId) {
        for (Finger f : fingers) if (f.id() == fingerId) return f;
        return Finger.invalid();
    }

    public Pointable pointable(int pointableId) {
        for (Pointable p : pointables) if (p.id() == pointableId) return p;
        return Pointable.invalid();
    }

    public Tool tool(int toolId) { return Tool.invalid(); }

    /** Palm orientation quaternion (x, y, z, w). */
    public float[] orientation() { return orientation.clone(); }

    public Matrix basis() {
        Matrix m = Matrix.fromQuaternion(orientation[0], orientation[1], orientation[2], orientation[3]);
        m.setOrigin(palmPosition);
        return m;
    }

    /**
     * Radius of the virtual ball held by the hand. Approximated from the grab strength
     * (Ultraleap Gemini/Hyperion no longer report a sphere): open hand 100 mm, fist 30 mm.
     */
    public float sphereRadius() { return 30f + 70f * (1f - grabStrength); }

    public Vector sphereCenter() { return palmPosition.plus(palmNormal.times(sphereRadius())); }

    public Vector translation(Frame sinceFrame) {
        if (sinceFrame == null || !sinceFrame.isValid()) return Vector.zero();
        Hand h = sinceFrame.hand(id);
        if (!h.isValid()) return Vector.zero();
        return palmPosition.minus(h.palmPosition());
    }

    public float translationProbability(Frame sinceFrame) { return sinceFrame != null && sinceFrame.isValid() ? 1f : 0f; }

    public float rotationAngle(Frame sinceFrame) {
        if (sinceFrame == null || !sinceFrame.isValid()) return 0;
        Hand h = sinceFrame.hand(id);
        if (!h.isValid()) return 0;
        return palmNormal.angleTo(h.palmNormal());
    }

    public float rotationAngle(Frame sinceFrame, Vector axis) { return rotationAngle(sinceFrame); }

    public Vector rotationAxis(Frame sinceFrame) {
        if (sinceFrame == null || !sinceFrame.isValid()) return Vector.zero();
        Hand h = sinceFrame.hand(id);
        if (!h.isValid()) return Vector.zero();
        return h.palmNormal().cross(palmNormal).normalized();
    }

    public float rotationProbability(Frame sinceFrame) { return sinceFrame != null && sinceFrame.isValid() ? 1f : 0f; }

    public float scaleFactor(Frame sinceFrame) { return 1f; }

    public float scaleProbability(Frame sinceFrame) { return 0f; }

    public void delete() { }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Hand)) return false;
        Hand h = (Hand) o;
        return valid == h.valid && id == h.id && (frame == null ? h.frame == null : (h.frame != null && frame.id() == h.frame.id()));
    }

    @Override
    public int hashCode() { return id * 31 + (frame == null ? 0 : (int) frame.id()); }

    @Override
    public String toString() { return "Hand id:" + id + (left ? " left" : " right") + " palm:" + palmPosition; }
}
