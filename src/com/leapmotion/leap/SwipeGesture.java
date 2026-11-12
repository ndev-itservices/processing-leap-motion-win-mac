package com.leapmotion.leap;

/** @author Tiago Martins */
public class SwipeGesture extends Gesture {

    private Vector startPosition = Vector.zero(), position = Vector.zero(), direction = Vector.zero();
    private float speed;

    public SwipeGesture() {
    }

    public SwipeGesture(Gesture g) {
        super(g instanceof SwipeGesture ? g : null);
        if (g instanceof SwipeGesture) {
            SwipeGesture s = (SwipeGesture) g;
            startPosition = s.startPosition; position = s.position; direction = s.direction; speed = s.speed;
        }
    }

    SwipeGesture(int id, State state, long duration, Frame frame, Pointable pointable,
                 Vector startPosition, Vector position, Vector direction, float speed) {
        super(id, Type.TYPE_SWIPE, state, duration, frame, pointable);
        this.startPosition = startPosition; this.position = position; this.direction = direction; this.speed = speed;
    }

    public static Type classType() { return Type.TYPE_SWIPE; }

    public Vector startPosition() { return startPosition; }
    public Vector position() { return position; }
    public Vector direction() { return direction; }
    /** Speed in mm/s. */
    public float speed() { return speed; }
    public Pointable pointable() { return pointable == null ? Pointable.invalid() : pointable; }
}
