package com.leapmotion.leap;

import java.util.ArrayList;
import java.util.List;

/** @author Tiago Martins */
public class FingerList extends LeapList<Finger> {

    public FingerList() { super(null); }

    FingerList(List<Finger> f) { super(f); }

    @Override
    protected Finger invalidItem() { return Finger.invalid(); }

    public Finger frontmost() {
        Finger best = null;
        for (Finger p : items) if (best == null || p.tipPosition().getZ() < best.tipPosition().getZ()) best = p;
        return best == null ? Finger.invalid() : best;
    }

    public Finger leftmost() {
        Finger best = null;
        for (Finger p : items) if (best == null || p.tipPosition().getX() < best.tipPosition().getX()) best = p;
        return best == null ? Finger.invalid() : best;
    }

    public Finger rightmost() {
        Finger best = null;
        for (Finger p : items) if (best == null || p.tipPosition().getX() > best.tipPosition().getX()) best = p;
        return best == null ? Finger.invalid() : best;
    }

    public FingerList extended() {
        List<Finger> l = new ArrayList<Finger>();
        for (Finger p : items) if (p.isExtended()) l.add(p);
        return new FingerList(l);
    }

    public FingerList fingerType(Finger.Type type) {
        List<Finger> l = new ArrayList<Finger>();
        for (Finger p : items) if (p.type() == type) l.add(p);
        return new FingerList(l);
    }

    public FingerList append(FingerList other) {
        List<Finger> l = new ArrayList<Finger>(items);
        l.addAll(other.items);
        return new FingerList(l);
    }
}
