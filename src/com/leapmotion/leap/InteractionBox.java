package com.leapmotion.leap;

/**
 * Axis aligned box in which coordinates can be normalised to [0, 1].
 * Fixed size approximating the V2 defaults for the Leap Motion Controller.
 *
 * @author Tiago Martins
 */
public class InteractionBox {

    private static final InteractionBox STANDARD = new InteractionBox(new Vector(0, 200, 0), 235.247f, 235.247f, 147.751f, true);
    private static final InteractionBox INVALID = new InteractionBox(Vector.zero(), 0, 0, 0, false);

    private final Vector center;
    private final float width, height, depth;
    private final boolean valid;

    InteractionBox(Vector center, float width, float height, float depth, boolean valid) {
        this.center = center; this.width = width; this.height = height; this.depth = depth; this.valid = valid;
    }

    public static InteractionBox standard() { return STANDARD; }
    public static InteractionBox invalid() { return INVALID; }

    public Vector center() { return center; }
    public float width() { return width; }
    public float height() { return height; }
    public float depth() { return depth; }
    public boolean isValid() { return valid; }

    public Vector normalizePoint(Vector p) { return normalizePoint(p, true); }

    public Vector normalizePoint(Vector p, boolean clamp) {
        float x = (p.getX() - center.getX()) / width + 0.5f;
        float y = (p.getY() - center.getY()) / height + 0.5f;
        float z = (p.getZ() - center.getZ()) / depth + 0.5f;
        if (clamp) {
            x = Math.max(0, Math.min(1, x)); y = Math.max(0, Math.min(1, y)); z = Math.max(0, Math.min(1, z));
        }
        return new Vector(x, y, z);
    }

    public Vector denormalizePoint(Vector n) {
        return new Vector((n.getX() - 0.5f) * width + center.getX(),
                (n.getY() - 0.5f) * height + center.getY(),
                (n.getZ() - 0.5f) * depth + center.getZ());
    }

    public void delete() { }

    @Override
    public String toString() { return "InteractionBox center:" + center + " size:(" + width + ", " + height + ", " + depth + ")"; }
}
