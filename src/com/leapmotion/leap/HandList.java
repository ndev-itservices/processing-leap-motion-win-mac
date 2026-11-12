package com.leapmotion.leap;

import java.util.ArrayList;
import java.util.List;

/** @author Tiago Martins */
public class HandList extends LeapList<Hand> {

    public HandList() { super(null); }

    HandList(List<Hand> hands) { super(hands); }

    @Override
    protected Hand invalidItem() { return Hand.invalid(); }

    public Hand frontmost() {
        Hand best = null;
        for (Hand h : items) if (best == null || h.palmPosition().getZ() < best.palmPosition().getZ()) best = h;
        return best == null ? Hand.invalid() : best;
    }

    public Hand leftmost() {
        Hand best = null;
        for (Hand h : items) if (best == null || h.palmPosition().getX() < best.palmPosition().getX()) best = h;
        return best == null ? Hand.invalid() : best;
    }

    public Hand rightmost() {
        Hand best = null;
        for (Hand h : items) if (best == null || h.palmPosition().getX() > best.palmPosition().getX()) best = h;
        return best == null ? Hand.invalid() : best;
    }

    public HandList append(HandList other) {
        List<Hand> l = new ArrayList<Hand>(items);
        l.addAll(other.items);
        return new HandList(l);
    }
}
