package com.leapmotion.leap;

/** @author Tiago Martins */
public class KeyTapGesture extends Gesture {

    private Vector position = Vector.zero(), direction = Vector.zero();

    public KeyTapGesture() {
    }

    public KeyTapGesture(Gesture g) {
        super(g instanceof KeyTapGesture ? g : null);
        if (g instanceof KeyTapGesture) {
            KeyTapGesture k = (KeyTapGesture) g;
            position = k.position; direction = k.direction;
        }
    }

    KeyTapGesture(int id, long duration, Frame frame, Pointable pointable, Vector position, Vector direction) {
        super(id, Type.TYPE_KEY_TAP, State.STATE_STOP, duration, frame, pointable);
        this.position = position; this.direction = direction;
    }

    public static Type classType() { return Type.TYPE_KEY_TAP; }

    public Vector position() { return position; }
    public Vector direction() { return direction; }
    public float progress() { return 1f; }
    public Pointable pointable() { return pointable == null ? Pointable.invalid() : pointable; }
}
