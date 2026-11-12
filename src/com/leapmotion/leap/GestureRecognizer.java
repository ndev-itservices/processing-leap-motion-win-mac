package com.leapmotion.leap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Java implementation of the four V2 gestures (swipe, circle, key tap, screen tap) on top of
 * raw Ultraleap tracking frames. Thresholds follow the V2 defaults and can be tuned through
 * {@link Config} ("Gesture.*" keys).
 *
 * @author Tiago Martins
 */
class GestureRecognizer {

    /* parameters (V2 defaults) */
    float circleMinRadius = 5.0f;              // mm
    float circleMinArc = (float) (1.5 * Math.PI); // rad
    float swipeMinLength = 150.0f;             // mm
    float swipeMinVelocity = 1000.0f;          // mm/s
    float keyTapMinDownVelocity = 50.0f;       // mm/s
    float keyTapHistorySeconds = 0.1f;         // s
    float keyTapMinDistance = 3.0f;            // mm
    float screenTapMinForwardVelocity = 50.0f; // mm/s
    float screenTapHistorySeconds = 0.1f;      // s
    float screenTapMinDistance = 5.0f;         // mm

    /* internal tuning */
    private static final float CIRCLE_MIN_SPEED = 40f;     // mm/s, below this a circle stops
    private static final long CIRCLE_STOP_GRACE_US = 150000;
    private static final long CIRCLE_WINDOW_US = 500000;    // positions used to estimate the centre
    private static final float CIRCLE_MAX_STEP_RAD = 1.2f;  // reject jumps (straight lines through the centroid)
    private static final long TAP_DEBOUNCE_US = 400000;
    private static final long HISTORY_US = 1200000;

    private final EnumSet<Gesture.Type> enabled = EnumSet.noneOf(Gesture.Type.class);
    private final Map<Integer, Track> tracks = new HashMap<Integer, Track>();
    private int nextId = 1;

    private static final class Sample {
        final long t; final Vector p; final Vector v;
        Sample(long t, Vector p, Vector v) { this.t = t; this.p = p; this.v = v; }
    }

    private static final class Track {
        final Deque<Sample> history = new ArrayDeque<Sample>();
        long lastSeen;
        // swipe
        boolean swipeCandidate, swipeActive;
        int swipeId; long swipeStart; Vector swipeStartPos;
        // circle
        boolean circleActive; int circleId; long circleStart; float circleAngle; int circleSign; long circleLastMoving;
        Vector circleCenter = Vector.zero(), circleNormal = Vector.zero(); float circleRadius;
        int circleReverseCount;
        // taps
        long lastKeyTap, lastScreenTap;
        // last pointable (for stop events when the finger disappears)
        Pointable last;
    }

    synchronized void enable(Gesture.Type type, boolean on) {
        if (type == null || type == Gesture.Type.TYPE_INVALID) return;
        if (on) enabled.add(type); else enabled.remove(type);
    }

    synchronized boolean isEnabled(Gesture.Type type) { return enabled.contains(type); }

    synchronized boolean anyEnabled() { return !enabled.isEmpty(); }

    synchronized void setParameter(String key, float value) {
        if ("Gesture.Circle.MinRadius".equals(key)) circleMinRadius = value;
        else if ("Gesture.Circle.MinArc".equals(key)) circleMinArc = value;
        else if ("Gesture.Swipe.MinLength".equals(key)) swipeMinLength = value;
        else if ("Gesture.Swipe.MinVelocity".equals(key)) swipeMinVelocity = value;
        else if ("Gesture.KeyTap.MinDownVelocity".equals(key)) keyTapMinDownVelocity = value;
        else if ("Gesture.KeyTap.HistorySeconds".equals(key)) keyTapHistorySeconds = value;
        else if ("Gesture.KeyTap.MinDistance".equals(key)) keyTapMinDistance = value;
        else if ("Gesture.ScreenTap.MinForwardVelocity".equals(key)) screenTapMinForwardVelocity = value;
        else if ("Gesture.ScreenTap.HistorySeconds".equals(key)) screenTapHistorySeconds = value;
        else if ("Gesture.ScreenTap.MinDistance".equals(key)) screenTapMinDistance = value;
    }

    /** Processes one frame and returns the gestures recognised in it. */
    synchronized List<Gesture> process(Frame frame) {
        List<Gesture> out = new ArrayList<Gesture>();
        if (frame == null || !frame.isValid() || enabled.isEmpty()) return out;
        long now = frame.timestamp();

        for (Pointable p : frame.pointables()) {
            if (!p.isValid() || !p.isExtended()) continue;
            Track t = tracks.get(p.id());
            if (t == null) { t = new Track(); tracks.put(p.id(), t); }
            t.lastSeen = now;
            t.last = p;
            t.history.addLast(new Sample(now, p.tipPosition(), p.tipVelocity()));
            while (!t.history.isEmpty() && now - t.history.peekFirst().t > HISTORY_US) t.history.removeFirst();

            if (enabled.contains(Gesture.Type.TYPE_SWIPE)) swipe(t, p, frame, now, out);
            if (enabled.contains(Gesture.Type.TYPE_CIRCLE)) circle(t, p, frame, now, out);
            if (enabled.contains(Gesture.Type.TYPE_KEY_TAP)) keyTap(t, p, frame, now, out);
            if (enabled.contains(Gesture.Type.TYPE_SCREEN_TAP)) screenTap(t, p, frame, now, out);
        }

        // pointables that vanished (or stopped being extended): close active gestures, drop stale tracks
        Iterator<Map.Entry<Integer, Track>> it = tracks.entrySet().iterator();
        while (it.hasNext()) {
            Track t = it.next().getValue();
            if (t.lastSeen == now) continue;
            Pointable p = t.last;
            if (t.swipeActive) {
                out.add(new SwipeGesture(t.swipeId, Gesture.State.STATE_STOP, now - t.swipeStart, frame, p,
                        t.swipeStartPos, p.tipPosition(), p.tipPosition().minus(t.swipeStartPos).normalized(), 0f));
                t.swipeActive = false; t.swipeCandidate = false;
            }
            if (t.circleActive) {
                out.add(new CircleGesture(t.circleId, Gesture.State.STATE_STOP, now - t.circleStart, frame, p,
                        t.circleCenter, t.circleNormal, Math.abs(t.circleAngle) / (float) (2 * Math.PI), t.circleRadius));
                t.circleActive = false;
            }
            if (now - t.lastSeen > HISTORY_US) it.remove();
            else t.history.clear();
        }
        return out;
    }

    /* -------------------------------------------------------------- swipe */
    private void swipe(Track t, Pointable p, Frame frame, long now, List<Gesture> out) {
        Vector v = p.tipVelocity();
        float speed = v.magnitude();
        if (!t.swipeCandidate && !t.swipeActive) {
            if (speed >= swipeMinVelocity) {
                t.swipeCandidate = true;
                t.swipeStart = now;
                t.swipeStartPos = p.tipPosition();
            }
            return;
        }
        Vector disp = p.tipPosition().minus(t.swipeStartPos);
        float length = disp.magnitude();
        if (t.swipeCandidate && !t.swipeActive) {
            if (speed < swipeMinVelocity * 0.5f) { t.swipeCandidate = false; return; }
            if (length >= swipeMinLength) {
                t.swipeActive = true;
                t.swipeId = nextId++;
                out.add(new SwipeGesture(t.swipeId, Gesture.State.STATE_START, now - t.swipeStart, frame, p,
                        t.swipeStartPos, p.tipPosition(), disp.normalized(), speed));
            }
            return;
        }
        // active
        if (speed < swipeMinVelocity * 0.5f) {
            out.add(new SwipeGesture(t.swipeId, Gesture.State.STATE_STOP, now - t.swipeStart, frame, p,
                    t.swipeStartPos, p.tipPosition(), disp.normalized(), speed));
            t.swipeActive = false; t.swipeCandidate = false;
        } else {
            out.add(new SwipeGesture(t.swipeId, Gesture.State.STATE_UPDATE, now - t.swipeStart, frame, p,
                    t.swipeStartPos, p.tipPosition(), disp.normalized(), speed));
        }
    }

    /* ------------------------------------------------------------- circle */
    private void circle(Track t, Pointable p, Frame frame, long now, List<Gesture> out) {
        float speed = p.tipVelocity().magnitude();
        // collect recent samples
        List<Sample> win = new ArrayList<Sample>();
        for (Sample s : t.history) if (now - s.t <= CIRCLE_WINDOW_US) win.add(s);
        if (win.size() < 4) { resetCircleCandidate(t); return; }

        Vector c = Vector.zero();
        for (Sample s : win) c = c.plus(s.p);
        c = c.divide(win.size());
        float r = 0;
        for (Sample s : win) r += s.p.distanceTo(c);
        r /= win.size();

        Sample prev = win.get(win.size() - 2), cur = win.get(win.size() - 1);
        Vector a = prev.p.minus(c), b = cur.p.minus(c);
        Vector n = a.cross(b);
        float nm = n.magnitude();
        float step = a.angleTo(b);
        if (nm > 1e-6f) n = n.divide(nm);

        boolean moving = speed >= CIRCLE_MIN_SPEED;
        if (moving) t.circleLastMoving = now;

        if (!t.circleActive) {
            if (!moving || r < circleMinRadius || step > CIRCLE_MAX_STEP_RAD || nm <= 1e-6f) {
                if (!moving) resetCircleCandidate(t);
                return;
            }
            // accumulate signed arc using a persistent normal estimate
            if (t.circleNormal.magnitude() < 0.5f) t.circleNormal = n;
            float sign = n.dot(t.circleNormal) >= 0 ? 1f : -1f;
            if (sign < 0) {
                t.circleReverseCount++;
                if (t.circleReverseCount > 2) { resetCircleCandidate(t); t.circleNormal = n; }
                return;
            }
            t.circleReverseCount = 0;
            t.circleNormal = t.circleNormal.times(0.8f).plus(n.times(0.2f)).normalized();
            t.circleAngle += step;
            t.circleCenter = c; t.circleRadius = r;
            if (t.circleStart == 0) t.circleStart = prev.t;
            if (t.circleAngle >= circleMinArc) {
                t.circleActive = true;
                t.circleId = nextId++;
                out.add(new CircleGesture(t.circleId, Gesture.State.STATE_START, now - t.circleStart, frame, p,
                        c, t.circleNormal, t.circleAngle / (float) (2 * Math.PI), r));
            }
            return;
        }
        // active circle
        boolean reversed = nm > 1e-6f && n.dot(t.circleNormal) < 0;
        if (reversed) t.circleReverseCount++; else t.circleReverseCount = 0;
        boolean stop = (now - t.circleLastMoving > CIRCLE_STOP_GRACE_US) || t.circleReverseCount > 3 || step > CIRCLE_MAX_STEP_RAD;
        if (!stop && moving && !reversed) {
            t.circleNormal = t.circleNormal.times(0.9f).plus(n.times(0.1f)).normalized();
            t.circleAngle += step;
            t.circleCenter = t.circleCenter.times(0.8f).plus(c.times(0.2f));
            t.circleRadius = t.circleRadius * 0.8f + r * 0.2f;
        }
        float progress = t.circleAngle / (float) (2 * Math.PI);
        if (stop) {
            out.add(new CircleGesture(t.circleId, Gesture.State.STATE_STOP, now - t.circleStart, frame, p,
                    t.circleCenter, t.circleNormal, progress, t.circleRadius));
            resetCircleCandidate(t);
            t.circleActive = false;
        } else {
            out.add(new CircleGesture(t.circleId, Gesture.State.STATE_UPDATE, now - t.circleStart, frame, p,
                    t.circleCenter, t.circleNormal, progress, t.circleRadius));
        }
    }

    private void resetCircleCandidate(Track t) {
        t.circleAngle = 0; t.circleStart = 0; t.circleReverseCount = 0;
        t.circleNormal = Vector.zero(); t.circleRadius = 0;
    }

    /* ---------------------------------------------------------------- taps */
    private void keyTap(Track t, Pointable p, Frame frame, long now, List<Gesture> out) {
        if (now - t.lastKeyTap < TAP_DEBOUNCE_US) return;
        long window = (long) (keyTapHistorySeconds * 1000000);
        Sample oldest = null; float minVy = 0; float maxDrop = 0; Vector top = null;
        for (Sample s : t.history) {
            if (now - s.t > window) continue;
            if (oldest == null) { oldest = s; top = s.p; }
            if (s.p.getY() > top.getY()) top = s.p;
            if (s.v.getY() < minVy) minVy = s.v.getY();
        }
        if (oldest == null || top == null) return;
        Vector cur = p.tipPosition();
        maxDrop = top.getY() - cur.getY();
        Vector v = p.tipVelocity();
        boolean rebounding = v.getY() >= -keyTapMinDownVelocity * 0.25f;
        boolean dominantY = maxDrop > Math.abs(top.getX() - cur.getX()) && maxDrop > Math.abs(top.getZ() - cur.getZ());
        if (maxDrop >= keyTapMinDistance && -minVy >= keyTapMinDownVelocity && rebounding && dominantY) {
            t.lastKeyTap = now;
            out.add(new KeyTapGesture(nextId++, now - oldest.t, frame, p, cur, new Vector(0, -1, 0)));
        }
    }

    private void screenTap(Track t, Pointable p, Frame frame, long now, List<Gesture> out) {
        if (now - t.lastScreenTap < TAP_DEBOUNCE_US) return;
        long window = (long) (screenTapHistorySeconds * 1000000);
        Sample oldest = null; float minVz = 0; Vector back = null;
        for (Sample s : t.history) {
            if (now - s.t > window) continue;
            if (oldest == null) { oldest = s; back = s.p; }
            if (s.p.getZ() > back.getZ()) back = s.p;
            if (s.v.getZ() < minVz) minVz = s.v.getZ();
        }
        if (oldest == null || back == null) return;
        Vector cur = p.tipPosition();
        float push = back.getZ() - cur.getZ();
        Vector v = p.tipVelocity();
        boolean retracting = v.getZ() >= -screenTapMinForwardVelocity * 0.25f;
        boolean dominantZ = push > Math.abs(back.getX() - cur.getX()) && push > Math.abs(back.getY() - cur.getY());
        if (push >= screenTapMinDistance && -minVz >= screenTapMinForwardVelocity && retracting && dominantZ) {
            t.lastScreenTap = now;
            out.add(new ScreenTapGesture(nextId++, now - oldest.t, frame, p, cur, new Vector(0, 0, -1)));
        }
    }
}
