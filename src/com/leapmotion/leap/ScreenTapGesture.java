package com.leapmotion.leap;

/** @author Tiago Martins */
public class ScreenTapGesture extends Gesture {

    private Vector position = Vector.zero(), direction = Vector.zero();

    public ScreenTapGesture() {
    }

    public ScreenTapGesture(Gesture g) {
        super(g instanceof ScreenTapGesture ? g : null);
        if (g instanceof ScreenTapGesture) {
            ScreenTapGesture k = (ScreenTapGesture) g;
            position = k.position; direction = k.direction;
        }
    }

    ScreenTapGesture(int id, long duration, Frame frame, Pointable pointable, Vector position, Vector direction) {
        super(id, Type.TYPE_SCREEN_TAP, State.STATE_STOP, duration, frame, pointable);
        this.position = position; this.direction = direction;
    }

    public static Type classType() { return Type.TYPE_SCREEN_TAP; }

    public Vector position() { return position; }
    public Vector direction() { return direction; }
    public float progress() { return 1f; }
    public Pointable pointable() { return pointable == null ? Pointable.invalid() : pointable; }
}
