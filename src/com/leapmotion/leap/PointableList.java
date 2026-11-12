package com.leapmotion.leap;

import java.util.ArrayList;
import java.util.List;

/** @author Tiago Martins */
public class PointableList extends LeapList<Pointable> {

    public PointableList() { super(null); }

    PointableList(List<? extends Pointable> p) { super(new ArrayList<Pointable>(p)); }

    @Override
    protected Pointable invalidItem() { return Pointable.invalid(); }

    public Pointable frontmost() {
        Pointable best = null;
        for (Pointable p : items) if (best == null || p.tipPosition().getZ() < best.tipPosition().getZ()) best = p;
        return best == null ? Pointable.invalid() : best;
    }

    public Pointable leftmost() {
        Pointable best = null;
        for (Pointable p : items) if (best == null || p.tipPosition().getX() < best.tipPosition().getX()) best = p;
        return best == null ? Pointable.invalid() : best;
    }

    public Pointable rightmost() {
        Pointable best = null;
        for (Pointable p : items) if (best == null || p.tipPosition().getX() > best.tipPosition().getX()) best = p;
        return best == null ? Pointable.invalid() : best;
    }

    public PointableList extended() {
        List<Pointable> l = new ArrayList<Pointable>();
        for (Pointable p : items) if (p.isExtended()) l.add(p);
        return new PointableList(l);
    }

    public PointableList append(PointableList other) {
        List<Pointable> l = new ArrayList<Pointable>(items);
        l.addAll(other.items);
        return new PointableList(l);
    }
}
