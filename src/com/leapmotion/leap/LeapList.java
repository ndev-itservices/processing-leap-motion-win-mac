package com.leapmotion.leap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Immutable base for the V2 style list classes (HandList, FingerList, ...).
 *
 * @author Tiago Martins
 */
abstract class LeapList<T> implements Iterable<T> {

    protected final List<T> items;

    protected LeapList(List<T> items) {
        this.items = items == null ? Collections.<T>emptyList() : Collections.unmodifiableList(new ArrayList<T>(items));
    }

    public int count() { return items.size(); }
    public int size() { return items.size(); }
    public boolean isEmpty() { return items.isEmpty(); }

    public T get(int index) {
        if (index < 0 || index >= items.size()) return invalidItem();
        return items.get(index);
    }

    protected abstract T invalidItem();

    @Override
    public Iterator<T> iterator() { return items.iterator(); }

    public List<T> asList() { return items; }

    /** No-op kept for API compatibility with the SWIG generated classes. */
    public void delete() { }

    @Override
    public String toString() {
        return getClass().getSimpleName() + items;
    }
}
