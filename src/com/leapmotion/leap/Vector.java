package com.leapmotion.leap;

/**
 * A three component vector (millimetres for positions, mm/s for velocities),
 * API compatible with the Leap Motion V2 Vector class.
 *
 * @author Tiago Martins
 */
public class Vector {

    private float x, y, z;

    public Vector() {
    }

    public Vector(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector(Vector v) {
        this(v.x, v.y, v.z);
    }

    public static Vector zero() { return new Vector(0, 0, 0); }
    public static Vector xAxis() { return new Vector(1, 0, 0); }
    public static Vector yAxis() { return new Vector(0, 1, 0); }
    public static Vector zAxis() { return new Vector(0, 0, 1); }
    public static Vector left() { return new Vector(-1, 0, 0); }
    public static Vector right() { return new Vector(1, 0, 0); }
    public static Vector down() { return new Vector(0, -1, 0); }
    public static Vector up() { return new Vector(0, 1, 0); }
    public static Vector forward() { return new Vector(0, 0, -1); }
    public static Vector backward() { return new Vector(0, 0, 1); }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public void setX(float v) { x = v; }
    public void setY(float v) { y = v; }
    public void setZ(float v) { z = v; }

    public float get(int index) {
        switch (index) { case 0: return x; case 1: return y; case 2: return z; default: return 0; }
    }

    public float magnitude() { return (float) Math.sqrt(x * x + y * y + z * z); }
    public float magnitudeSquared() { return x * x + y * y + z * z; }

    public float distanceTo(Vector o) {
        float dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public float angleTo(Vector o) {
        float d = magnitudeSquared() * o.magnitudeSquared();
        if (d <= 0) return 0;
        float c = dot(o) / (float) Math.sqrt(d);
        if (c > 1) c = 1; else if (c < -1) c = -1;
        return (float) Math.acos(c);
    }

    /** Pitch: rotation around the x axis, in radians (positive = pointing up). */
    public float pitch() { return (float) Math.atan2(y, -z); }
    /** Yaw: rotation around the y axis, in radians (positive = pointing right). */
    public float yaw() { return (float) Math.atan2(x, -z); }
    /** Roll: rotation around the z axis, in radians. */
    public float roll() { return (float) Math.atan2(x, -y); }

    public float dot(Vector o) { return x * o.x + y * o.y + z * o.z; }

    public Vector cross(Vector o) {
        return new Vector(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public Vector normalized() {
        float m = magnitude();
        if (m <= 0) return new Vector(0, 0, 0);
        return new Vector(x / m, y / m, z / m);
    }

    public Vector plus(Vector o) { return new Vector(x + o.x, y + o.y, z + o.z); }
    public Vector minus(Vector o) { return new Vector(x - o.x, y - o.y, z - o.z); }
    public Vector times(float s) { return new Vector(x * s, y * s, z * s); }
    public Vector divide(float s) { return new Vector(x / s, y / s, z / s); }
    public Vector opposite() { return new Vector(-x, -y, -z); }

    public float[] toFloatArray() { return new float[]{x, y, z}; }

    public boolean isValid() {
        return !(Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)
                || Float.isInfinite(x) || Float.isInfinite(y) || Float.isInfinite(z));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Vector)) return false;
        Vector v = (Vector) o;
        return x == v.x && y == v.y && z == v.z;
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits(x) * 31 * 31 + Float.floatToIntBits(y) * 31 + Float.floatToIntBits(z);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
