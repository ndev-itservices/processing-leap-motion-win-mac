package com.leapmotion.leap;

/** @author Tiago Martins */
public class CircleGesture extends Gesture {

    private Vector center = Vector.zero(), normal = Vector.zero();
    private float progress, radius;

    public CircleGesture() {
    }

    public CircleGesture(Gesture g) {
        super(g instanceof CircleGesture ? g : null);
        if (g instanceof CircleGesture) {
            CircleGesture c = (CircleGesture) g;
            center = c.center; normal = c.normal; progress = c.progress; radius = c.radius;
        }
    }

    CircleGesture(int id, State state, long duration, Frame frame, Pointable pointable,
                  Vector center, Vector normal, float progress, float radius) {
        super(id, Type.TYPE_CIRCLE, state, duration, frame, pointable);
        this.center = center; this.normal = normal; this.progress = progress; this.radius = radius;
    }

    public static Type classType() { return Type.TYPE_CIRCLE; }

    public Vector center() { return center; }
    /** Normal of the circle plane. Points along the finger direction for a clockwise circle. */
    public Vector normal() { return normal; }
    /** Number of turns completed (1.0 = one full circle). */
    public float progress() { return progress; }
    public float radius() { return radius; }
    public Pointable pointable() { return pointable == null ? Pointable.invalid() : pointable; }
}
