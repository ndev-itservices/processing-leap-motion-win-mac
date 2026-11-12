package com.leapmotion.leap;

/**
 * Tools are not tracked by Ultraleap Gemini/Hyperion. Kept for API compatibility: always invalid.
 *
 * @author Tiago Martins
 */
public class Tool extends Pointable {

    private static final Tool INVALID = new Tool();

    Tool() {
    }

    public Tool(Pointable p) {
        super(p instanceof Tool ? p : null);
    }

    public static Tool invalid() { return INVALID; }

    @Override
    public boolean isTool() { return valid; }
}
