package com.leapmotion.leap;

/**
 * Minimal 3x3 rotation + origin matrix, API compatible with the subset of the
 * Leap Motion V2 Matrix class commonly used by sketches.
 *
 * @author Tiago Martins
 */
public class Matrix {

    private Vector xBasis, yBasis, zBasis, origin;

    public Matrix() {
        this(Vector.xAxis(), Vector.yAxis(), Vector.zAxis(), Vector.zero());
    }

    public Matrix(Vector x, Vector y, Vector z) {
        this(x, y, z, Vector.zero());
    }

    public Matrix(Vector x, Vector y, Vector z, Vector origin) {
        this.xBasis = x; this.yBasis = y; this.zBasis = z; this.origin = origin;
    }

    /** Build a rotation matrix from a unit quaternion (x, y, z, w). */
    static Matrix fromQuaternion(float qx, float qy, float qz, float qw) {
        float xx = qx * qx, yy = qy * qy, zz = qz * qz;
        float xy = qx * qy, xz = qx * qz, yz = qy * qz, wx = qw * qx, wy = qw * qy, wz = qw * qz;
        Vector x = new Vector(1 - 2 * (yy + zz), 2 * (xy + wz), 2 * (xz - wy));
        Vector y = new Vector(2 * (xy - wz), 1 - 2 * (xx + zz), 2 * (yz + wx));
        Vector z = new Vector(2 * (xz + wy), 2 * (yz - wx), 1 - 2 * (xx + yy));
        return new Matrix(x, y, z);
    }

    public static Matrix identity() { return new Matrix(); }

    public Vector getXBasis() { return xBasis; }
    public Vector getYBasis() { return yBasis; }
    public Vector getZBasis() { return zBasis; }
    public Vector getOrigin() { return origin; }
    public void setXBasis(Vector v) { xBasis = v; }
    public void setYBasis(Vector v) { yBasis = v; }
    public void setZBasis(Vector v) { zBasis = v; }
    public void setOrigin(Vector v) { origin = v; }

    public Vector transformDirection(Vector in) {
        return xBasis.times(in.getX()).plus(yBasis.times(in.getY())).plus(zBasis.times(in.getZ()));
    }

    public Vector transformPoint(Vector in) {
        return transformDirection(in).plus(origin);
    }

    public Matrix rigidInverse() {
        Matrix r = new Matrix(
                new Vector(xBasis.getX(), yBasis.getX(), zBasis.getX()),
                new Vector(xBasis.getY(), yBasis.getY(), zBasis.getY()),
                new Vector(xBasis.getZ(), yBasis.getZ(), zBasis.getZ()));
        r.origin = r.transformDirection(origin).opposite();
        return r;
    }

    public Matrix times(Matrix o) {
        return new Matrix(transformDirection(o.xBasis), transformDirection(o.yBasis),
                transformDirection(o.zBasis), transformPoint(o.origin));
    }

    public float[] toArray3x3() {
        return new float[]{xBasis.getX(), xBasis.getY(), xBasis.getZ(),
                yBasis.getX(), yBasis.getY(), yBasis.getZ(),
                zBasis.getX(), zBasis.getY(), zBasis.getZ()};
    }

    public float[] toArray4x4() {
        return new float[]{xBasis.getX(), xBasis.getY(), xBasis.getZ(), 0,
                yBasis.getX(), yBasis.getY(), yBasis.getZ(), 0,
                zBasis.getX(), zBasis.getY(), zBasis.getZ(), 0,
                origin.getX(), origin.getY(), origin.getZ(), 1};
    }

    @Override
    public String toString() {
        return "xBasis:" + xBasis + " yBasis:" + yBasis + " zBasis:" + zBasis + " origin:" + origin;
    }
}
