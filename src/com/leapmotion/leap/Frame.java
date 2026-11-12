package com.leapmotion.leap;

import java.util.ArrayList;
import java.util.List;

/**
 * A snapshot of tracking data. Immutable (except for the gesture list attached by the controller).
 *
 * @author Tiago Martins
 */
public class Frame {

    private static final Frame INVALID = new Frame();

    private final long id;
    private final long timestamp;
    private final float fps;
    private final boolean valid;
    private HandList hands = new HandList();
    private FingerList fingers = new FingerList();
    private PointableList pointables = new PointableList();
    private final ToolList tools = new ToolList();
    private volatile GestureList gestures = new GestureList();
    Controller controller;

    private Frame() {
        this.id = -1; this.timestamp = 0; this.fps = 0; this.valid = false;
    }

    /** Builds a frame from the native buffer. */
    Frame(long id, long timestamp, float fps, float[] data, int nHands, Frame prev, Controller ctrl) {
        this.id = id; this.timestamp = timestamp; this.fps = fps; this.valid = true; this.controller = ctrl;
        List<Hand> hl = new ArrayList<Hand>(nHands);
        List<Finger> fl = new ArrayList<Finger>(nHands * 5);
        for (int i = 0; i < nHands; i++) {
            Hand h = new Hand(this, data, i * LeapJNI.HAND_STRIDE, prev, ctrl);
            hl.add(h);
            for (Finger f : h.fingers()) fl.add(f);
        }
        this.hands = new HandList(hl);
        this.fingers = new FingerList(fl);
        this.pointables = new PointableList(fl);
    }

    public static Frame invalid() { return INVALID; }

    public long id() { return id; }
    public long timestamp() { return timestamp; }
    public float currentFramesPerSecond() { return fps; }
    public boolean isValid() { return valid; }
    public HandList hands() { return hands; }
    public FingerList fingers() { return fingers; }
    public PointableList pointables() { return pointables; }
    public ToolList tools() { return tools; }

    public Hand hand(int handId) {
        for (Hand h : hands) if (h.id() == handId) return h;
        return Hand.invalid();
    }

    public Finger finger(int fingerId) {
        for (Finger f : fingers) if (f.id() == fingerId) return f;
        return Finger.invalid();
    }

    public Pointable pointable(int pointableId) {
        for (Pointable p : pointables) if (p.id() == pointableId) return p;
        return Pointable.invalid();
    }

    public Tool tool(int toolId) { return Tool.invalid(); }

    void setGestures(List<Gesture> g) { this.gestures = new GestureList(g); }

    /** Gestures recognised in this frame. */
    public GestureList gestures() { return gestures; }

    /** Gestures recognised since {@code sinceFrame} (exclusive) up to this frame (inclusive). */
    public GestureList gestures(Frame sinceFrame) {
        if (sinceFrame == null || !sinceFrame.isValid() || controller == null || sinceFrame.id >= id) return gestures;
        return controller.gesturesBetween(sinceFrame.id, id);
    }

    public Gesture gesture(int gestureId) {
        for (Gesture g : gestures) if (g.id() == gestureId) return g;
        return Gesture.invalid();
    }

    /** Camera images closest to this frame (requires Controller.PolicyFlag.POLICY_IMAGES). */
    public ImageList images() { return controller == null ? new ImageList() : controller.images(); }

    public ImageList rawImages() { return images(); }

    public InteractionBox interactionBox() { return InteractionBox.standard(); }

    public Vector translation(Frame sinceFrame) {
        if (sinceFrame == null || !sinceFrame.isValid() || hands.isEmpty()) return Vector.zero();
        Vector sum = Vector.zero(); int n = 0;
        for (Hand h : hands) { Hand p = sinceFrame.hand(h.id()); if (p.isValid()) { sum = sum.plus(h.palmPosition().minus(p.palmPosition())); n++; } }
        return n == 0 ? Vector.zero() : sum.divide(n);
    }

    public float translationProbability(Frame sinceFrame) { return hands.isEmpty() ? 0f : 1f; }
    public float rotationAngle(Frame sinceFrame) { return hands.isEmpty() ? 0f : hands.get(0).rotationAngle(sinceFrame); }
    public float rotationAngle(Frame sinceFrame, Vector axis) { return rotationAngle(sinceFrame); }
    public Vector rotationAxis(Frame sinceFrame) { return hands.isEmpty() ? Vector.zero() : hands.get(0).rotationAxis(sinceFrame); }
    public float rotationProbability(Frame sinceFrame) { return hands.isEmpty() ? 0f : 1f; }
    public float scaleFactor(Frame sinceFrame) { return 1f; }
    public float scaleProbability(Frame sinceFrame) { return 0f; }

    public void delete() { }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Frame) && ((Frame) o).id == id && ((Frame) o).valid == valid;
    }

    @Override
    public int hashCode() { return (int) id; }

    @Override
    public String toString() { return "Frame id:" + id + " ts:" + timestamp + " hands:" + hands.count(); }
}
