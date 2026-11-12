package com.leapmotion.leap;

import java.util.ArrayList;
import java.util.List;

/**
 * A recognised motion pattern. Ultraleap Gemini/Hyperion removed gesture recognition from
 * the tracking service, so gestures are recognised in Java (see GestureRecognizer) with
 * the same semantics and default thresholds as the V2 SDK.
 *
 * @author Tiago Martins
 */
public class Gesture {

    public enum Type {
        TYPE_INVALID(-1), TYPE_SWIPE(1), TYPE_CIRCLE(4), TYPE_SCREEN_TAP(5), TYPE_KEY_TAP(6);

        private final int value;

        Type(int v) { value = v; }

        public int swigValue() { return value; }

        public static Type swigToEnum(int v) {
            for (Type t : values()) if (t.value == v) return t;
            return TYPE_INVALID;
        }
    }

    public enum State {
        STATE_INVALID(-1), STATE_START(1), STATE_UPDATE(2), STATE_STOP(3);

        private final int value;

        State(int v) { value = v; }

        public int swigValue() { return value; }

        public static State swigToEnum(int v) {
            for (State s : values()) if (s.value == v) return s;
            return STATE_INVALID;
        }
    }

    private static final Gesture INVALID = new Gesture();

    protected int id = -1;
    protected Type type = Type.TYPE_INVALID;
    protected State state = State.STATE_INVALID;
    protected long duration;
    protected Frame frame;
    protected Hand hand;
    protected Pointable pointable;
    protected boolean valid;

    protected Gesture() {
    }

    protected Gesture(Gesture g) {
        if (g == null) return;
        id = g.id; type = g.type; state = g.state; duration = g.duration; frame = g.frame;
        hand = g.hand; pointable = g.pointable; valid = g.valid;
    }

    Gesture(int id, Type type, State state, long durationMicros, Frame frame, Pointable pointable) {
        this.id = id; this.type = type; this.state = state; this.duration = durationMicros;
        this.frame = frame; this.pointable = pointable; this.hand = pointable == null ? null : pointable.hand();
        this.valid = true;
    }

    public static Gesture invalid() { return INVALID; }

    public int id() { return id; }
    public Type type() { return type; }
    public State state() { return state; }
    /** Elapsed time since the gesture started, in microseconds. */
    public long duration() { return duration; }
    public float durationSeconds() { return duration / 1000000f; }
    public Frame frame() { return frame == null ? Frame.invalid() : frame; }
    public boolean isValid() { return valid; }

    public HandList hands() {
        List<Hand> l = new ArrayList<Hand>(1);
        if (hand != null && hand.isValid()) l.add(hand);
        return new HandList(l);
    }

    public PointableList pointables() {
        List<Pointable> l = new ArrayList<Pointable>(1);
        if (pointable != null && pointable.isValid()) l.add(pointable);
        return new PointableList(l);
    }

    public void delete() { }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Gesture) && ((Gesture) o).id == id && ((Gesture) o).valid == valid
                && ((Gesture) o).state == state && ((Gesture) o).frame() == frame();
    }

    @Override
    public int hashCode() { return id * 31 + state.swigValue(); }

    @Override
    public String toString() { return getClass().getSimpleName() + " id:" + id + " " + state + " " + durationSeconds() + "s"; }
}
