package com.leapmotion.leap;

import java.util.ArrayList;
import java.util.List;

/**
 * Tools (pens, sticks) are not tracked by Ultraleap Gemini/Hyperion; this list is always empty.
 *
 * @author Tiago Martins
 */
public class ToolList extends LeapList<Tool> {

    public ToolList() { super(null); }

    ToolList(List<Tool> t) { super(t); }

    @Override
    protected Tool invalidItem() { return Tool.invalid(); }

    public Tool frontmost() { return items.isEmpty() ? Tool.invalid() : items.get(0); }
    public Tool leftmost() { return items.isEmpty() ? Tool.invalid() : items.get(0); }
    public Tool rightmost() { return items.isEmpty() ? Tool.invalid() : items.get(0); }

    public ToolList append(ToolList other) {
        List<Tool> l = new ArrayList<Tool>(items);
        l.addAll(other.items);
        return new ToolList(l);
    }
}
