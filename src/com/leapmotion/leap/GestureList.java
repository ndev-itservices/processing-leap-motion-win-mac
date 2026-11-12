package com.leapmotion.leap;

import java.util.List;

/** @author Tiago Martins */
public class GestureList extends LeapList<Gesture> {

    public GestureList() { super(null); }

    GestureList(List<Gesture> g) { super(g); }

    @Override
    protected Gesture invalidItem() { return Gesture.invalid(); }
}
