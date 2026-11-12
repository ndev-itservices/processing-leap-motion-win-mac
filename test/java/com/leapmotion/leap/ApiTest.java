package com.leapmotion.leap;

import java.util.ArrayList;
import java.util.List;

/**
 * Self-contained tests (no JUnit) for the pure Java part of the API: frame parsing,
 * lists, vector maths and the gesture recogniser, using synthetic tracking buffers.
 * Run: java -cp build/classes:build/test-classes com.leapmotion.leap.ApiTest
 *
 * @author Tiago Martins
 */
public class ApiTest {

    private static int failures = 0, checks = 0;

    private static void check(boolean cond, String msg) {
        checks++;
        if (!cond) { failures++; System.out.println("  FAIL: " + msg); }
    }

    private static void section(String s) { System.out.println("== " + s); }

    /* ------------------------------------------------------------ builders */

    static void putVec(float[] d, int o, Vector v) { d[o] = v.getX(); d[o + 1] = v.getY(); d[o + 2] = v.getZ(); }

    /** Writes one hand block. Finger tips are given; bones are interpolated from the palm. */
    static void hand(float[] d, int o, int id, boolean left, Vector palm, Vector[] tips, boolean[] extended, float grab) {
        d[o + LeapJNI.H_ID] = id;
        d[o + LeapJNI.H_TYPE] = left ? 0 : 1;
        d[o + LeapJNI.H_CONF] = 1f;
        d[o + LeapJNI.H_VISIBLE] = 2.5f;
        d[o + LeapJNI.H_GRAB] = grab;
        d[o + LeapJNI.H_PINCH] = 0.1f;
        putVec(d, o + LeapJNI.H_PALM_POS, palm);
        putVec(d, o + LeapJNI.H_PALM_STAB, palm);
        putVec(d, o + LeapJNI.H_PALM_NORMAL, new Vector(0, -1, 0));
        putVec(d, o + LeapJNI.H_PALM_DIR, new Vector(0, 0, -1));
        d[o + LeapJNI.H_PALM_WIDTH] = 85f;
        d[o + LeapJNI.H_PALM_QUAT + 3] = 1f;
        for (int i = 0; i < 5; i++) {
            int b = o + LeapJNI.H_DIGITS + i * LeapJNI.D_STRIDE;
            d[b + LeapJNI.D_ID] = id * 10 + i;
            d[b + LeapJNI.D_EXTENDED] = extended[i] ? 1 : 0;
            Vector dir = tips[i].minus(palm);
            for (int k = 0; k < 4; k++) {
                int bo = b + LeapJNI.D_BONES + k * LeapJNI.B_STRIDE;
                putVec(d, bo, palm.plus(dir.times(k * 0.25f)));
                putVec(d, bo + 3, palm.plus(dir.times((k + 1) * 0.25f)));
                d[bo + 6] = 15f;
                d[bo + 10] = 1f;
            }
        }
        int a = o + LeapJNI.H_ARM;
        putVec(d, a, palm.plus(new Vector(0, -20, 250)));
        putVec(d, a + 3, palm.plus(new Vector(0, -10, 60)));
        d[a + 6] = 50f;
        d[a + 10] = 1f;
    }

    static Vector[] tipsFor(Vector palm, Vector indexTip) {
        Vector[] t = new Vector[5];
        t[0] = palm.plus(new Vector(-40, 0, -40));
        t[1] = indexTip;
        t[2] = palm.plus(new Vector(0, 0, -95));
        t[3] = palm.plus(new Vector(20, 0, -90));
        t[4] = palm.plus(new Vector(40, 0, -70));
        return t;
    }

    static final boolean[] ALL_EXT = {true, true, true, true, true};

    /** Simulates a controller feeding frames at 100 fps; returns all gestures emitted. */
    static class Sim {
        final GestureRecognizer rec = new GestureRecognizer();
        Frame prev = null;
        long id = 1, ts = 1_000_000;
        final List<Gesture> gestures = new ArrayList<Gesture>();

        Frame step(Vector palm, Vector indexTip) {
            float[] d = new float[LeapJNI.HAND_STRIDE];
            hand(d, 0, 7, false, palm, tipsFor(palm, indexTip), ALL_EXT, 0f);
            Frame f = new Frame(id++, ts, 100f, d, 1, prev, null);
            ts += 10_000;
            List<Gesture> g = rec.process(f);
            f.setGestures(g);
            gestures.addAll(g);
            prev = f;
            return f;
        }

        Frame stepEmpty() {
            Frame f = new Frame(id++, ts, 100f, new float[0], 0, prev, null);
            ts += 10_000;
            List<Gesture> g = rec.process(f);
            gestures.addAll(g);
            prev = f;
            return f;
        }

        int count(Gesture.Type t, Gesture.State s) {
            int n = 0;
            for (Gesture g : gestures) if (g.type() == t && (s == null || g.state() == s)) n++;
            return n;
        }
    }

    /* --------------------------------------------------------------- tests */

    static void testVector() {
        section("Vector");
        Vector v = new Vector(0, 1, -1);
        check(Math.abs(Math.toDegrees(v.pitch()) - 45) < 1e-3, "pitch 45");
        check(Math.abs(Math.toDegrees(new Vector(1, 0, -1).yaw()) - 45) < 1e-3, "yaw 45");
        check(Math.abs(Math.toDegrees(new Vector(1, -1, 0).roll()) - 45) < 1e-3, "roll 45");
        check(Math.abs(new Vector(1, 0, 0).angleTo(new Vector(0, 1, 0)) - Math.PI / 2) < 1e-5, "angleTo 90");
        check(new Vector(1, 0, 0).cross(new Vector(0, 1, 0)).equals(new Vector(0, 0, 1)), "cross");
        check(Math.abs(new Vector(3, 4, 0).magnitude() - 5) < 1e-6, "magnitude");
        Matrix m = Matrix.fromQuaternion(0, 0, 0, 1);
        check(m.transformDirection(new Vector(1, 2, 3)).equals(new Vector(1, 2, 3)), "identity quaternion");
        Matrix r = Matrix.fromQuaternion(0, (float) Math.sin(Math.PI / 4), 0, (float) Math.cos(Math.PI / 4));
        Vector t = r.transformDirection(new Vector(1, 0, 0));
        check(Math.abs(t.getZ() + 1) < 1e-5 && Math.abs(t.getX()) < 1e-5, "90deg about y: " + t);
    }

    static void testFrameParsing() {
        section("Frame parsing");
        float[] d = new float[2 * LeapJNI.HAND_STRIDE];
        Vector palmL = new Vector(-80, 200, 0), palmR = new Vector(90, 210, -30);
        boolean[] ext = {true, true, false, false, false};
        hand(d, 0, 3, true, palmL, tipsFor(palmL, palmL.plus(new Vector(-10, 0, -100))), ext, 0.6f);
        hand(d, LeapJNI.HAND_STRIDE, 4, false, palmR, tipsFor(palmR, palmR.plus(new Vector(10, 0, -100))), ALL_EXT, 0f);
        Frame f = new Frame(42, 123456, 110f, d, 2, null, null);
        check(f.isValid() && f.id() == 42 && f.timestamp() == 123456, "frame header");
        check(f.hands().count() == 2, "two hands");
        check(f.hand(3).isLeft() && f.hand(4).isRight(), "handedness");
        check(!f.hand(99).isValid(), "unknown hand invalid");
        check(f.fingers().count() == 10, "ten fingers");
        check(f.hand(3).fingers().extended().count() == 2, "extended fingers of left hand");
        check(f.fingers().extended().count() == 7, "extended fingers total");
        check(f.hands().leftmost().id() == 3 && f.hands().rightmost().id() == 4, "leftmost/rightmost");
        check(f.hands().frontmost().id() == 4, "frontmost (smallest z)");
        Finger idx = f.hand(4).fingers().fingerType(Finger.Type.TYPE_INDEX).get(0);
        check(idx.id() == 41, "finger id = hand*10 + type: " + idx.id());
        check(idx.jointPosition(Finger.Joint.JOINT_TIP).equals(idx.tipPosition()), "tip joint == tip");
        check(idx.bone(Bone.Type.TYPE_DISTAL).nextJoint().equals(idx.tipPosition()), "distal next joint == tip");
        check(Math.abs(idx.length() - 0.75f * idx.tipPosition().distanceTo(palmR)) < 1e-3, "finger length");
        check(idx.isFinger() && !idx.isTool() && idx.hand().id() == 4 && idx.frame().id() == 42, "finger links");
        check(f.hand(3).grabStrength() == 0.6f, "grab strength");
        check(Math.abs(f.hand(3).sphereRadius() - (30 + 70 * 0.4f)) < 1e-4, "sphere radius approx");
        check(f.hand(4).arm().isValid() && f.hand(4).arm().wristPosition().getZ() < f.hand(4).arm().elbowPosition().getZ(), "arm");
        check(f.tools().isEmpty() && !f.tool(1).isValid(), "no tools");
        check(f.pointables().count() == 10, "pointables");
        Finger conv = new Finger(f.pointables().get(0));
        check(conv.isValid() && conv.id() == f.pointables().get(0).id(), "Finger(Pointable) conversion");
        check(!new Tool(f.pointables().get(0)).isValid(), "Tool(Pointable) of a finger is invalid");
        check(!Frame.invalid().isValid() && Frame.invalid().hands().isEmpty(), "invalid frame");
        // velocity from previous frame
        float[] d2 = d.clone();
        Vector palmR2 = palmR.plus(new Vector(5, 0, 0));
        hand(d2, LeapJNI.HAND_STRIDE, 4, false, palmR2, tipsFor(palmR2, palmR2.plus(new Vector(10, 0, -100))), ALL_EXT, 0f);
        Frame f2 = new Frame(43, 123456 + 10_000, 110f, d2, 2, f, null);
        Vector vel = f2.hand(4).fingers().fingerType(Finger.Type.TYPE_INDEX).get(0).tipVelocity();
        check(Math.abs(vel.getX() - 500f) < 1e-2 && Math.abs(vel.getY()) < 1e-3, "tip velocity 5mm/10ms = 500mm/s: " + vel);
        check(f2.hand(4).translation(f).equals(new Vector(5, 0, 0)), "hand translation since frame");
    }

    static void testSwipe() {
        section("Swipe gesture");
        Sim s = new Sim();
        s.rec.enable(Gesture.Type.TYPE_SWIPE, true);
        s.rec.enable(Gesture.Type.TYPE_KEY_TAP, true);
        s.rec.enable(Gesture.Type.TYPE_SCREEN_TAP, true);
        Vector palm = new Vector(0, 200, 0);
        for (int i = 0; i < 20; i++) s.step(palm, palm.plus(new Vector(-150, 0, -90)));
        // 300 mm in 0.25 s = 1200 mm/s
        for (int i = 1; i <= 25; i++) s.step(palm, palm.plus(new Vector(-150 + 12 * i, 0, -90)));
        for (int i = 0; i < 20; i++) s.step(palm, palm.plus(new Vector(150, 0, -90)));
        check(s.count(Gesture.Type.TYPE_SWIPE, Gesture.State.STATE_START) == 1, "one swipe start, got " + s.count(Gesture.Type.TYPE_SWIPE, Gesture.State.STATE_START));
        check(s.count(Gesture.Type.TYPE_SWIPE, Gesture.State.STATE_STOP) == 1, "one swipe stop");
        check(s.count(Gesture.Type.TYPE_SWIPE, Gesture.State.STATE_UPDATE) >= 3, "swipe updates");
        check(s.count(Gesture.Type.TYPE_KEY_TAP, null) == 0, "no key tap during horizontal swipe");
        check(s.count(Gesture.Type.TYPE_SCREEN_TAP, null) == 0, "no screen tap during horizontal swipe");
        SwipeGesture start = null, stop = null;
        for (Gesture g : s.gestures) {
            if (g.type() != Gesture.Type.TYPE_SWIPE) continue;
            if (g.state() == Gesture.State.STATE_START) start = new SwipeGesture(g);
            if (g.state() == Gesture.State.STATE_STOP) stop = new SwipeGesture(g);
        }
        check(start != null && start.direction().getX() > 0.99f, "swipe direction +x");
        check(start != null && start.speed() > 1000f, "swipe speed > 1000: " + (start == null ? 0 : start.speed()));
        check(stop != null && stop.position().minus(stop.startPosition()).magnitude() >= 150f, "swipe length >= 150");
        check(start != null && start.id() == stop.id(), "same gesture id for start/stop");
        check(start != null && start.hands().count() == 1 && start.hands().get(0).id() == 7, "swipe hand");
        check(start != null && start.pointable().isFinger() && new Finger(start.pointable()).type() == Finger.Type.TYPE_INDEX, "swipe pointable is index finger");
        // slow motion must not swipe
        Sim slow = new Sim();
        slow.rec.enable(Gesture.Type.TYPE_SWIPE, true);
        for (int i = 0; i < 100; i++) slow.step(palm, palm.plus(new Vector(-150 + 3 * i, 0, -90))); // 300 mm/s
        check(slow.count(Gesture.Type.TYPE_SWIPE, null) == 0, "slow movement is not a swipe");
    }

    static void testCircle() {
        section("Circle gesture");
        Sim s = new Sim();
        s.rec.enable(Gesture.Type.TYPE_CIRCLE, true);
        s.rec.enable(Gesture.Type.TYPE_SWIPE, true);
        Vector palm = new Vector(0, 200, 0);
        Vector center = palm.plus(new Vector(0, 0, -90));
        float r = 30f;
        // 2 turns per second, 1.0 s -> 2 turns, counter-clockwise in the XY plane seen from +z
        int n = 100;
        for (int i = 0; i < 10; i++) s.step(palm, center.plus(new Vector(r, 0, 0)));
        for (int i = 0; i <= n; i++) {
            double a = 2 * Math.PI * 2 * i / n;
            s.step(palm, center.plus(new Vector((float) (r * Math.cos(a)), (float) (r * Math.sin(a)), 0)));
        }
        Vector last = center.plus(new Vector(r, 0, 0));
        for (int i = 0; i < 30; i++) s.step(palm, last);
        check(s.count(Gesture.Type.TYPE_CIRCLE, Gesture.State.STATE_START) == 1, "one circle start, got " + s.count(Gesture.Type.TYPE_CIRCLE, Gesture.State.STATE_START));
        check(s.count(Gesture.Type.TYPE_CIRCLE, Gesture.State.STATE_STOP) == 1, "one circle stop");
        check(s.count(Gesture.Type.TYPE_SWIPE, null) == 0, "circle is not a swipe (377 mm/s)");
        CircleGesture stop = null, start = null;
        for (Gesture g : s.gestures) {
            if (g.type() != Gesture.Type.TYPE_CIRCLE) continue;
            if (g.state() == Gesture.State.STATE_STOP) stop = new CircleGesture(g);
            if (g.state() == Gesture.State.STATE_START) start = new CircleGesture(g);
        }
        check(stop != null && stop.progress() > 1.5f && stop.progress() < 2.6f, "progress ~2 turns: " + (stop == null ? 0 : stop.progress()));
        check(stop != null && Math.abs(stop.radius() - r) < 8f, "radius ~30: " + (stop == null ? 0 : stop.radius()));
        check(stop != null && stop.center().distanceTo(center) < 8f, "center: " + (stop == null ? null : stop.center()));
        check(stop != null && stop.normal().getZ() > 0.9f, "CCW seen from +z -> normal +z: " + (stop == null ? null : stop.normal()));
        check(start != null && start.progress() >= 0.7f && start.progress() < 1.2f, "start after ~270 deg: " + (start == null ? 0 : start.progress()));
        // clockwise circle -> normal -z (points along the finger direction, which is -z)
        Sim cw = new Sim();
        cw.rec.enable(Gesture.Type.TYPE_CIRCLE, true);
        for (int i = 0; i <= n; i++) {
            double a = -2 * Math.PI * 2 * i / n;
            cw.step(palm, center.plus(new Vector((float) (r * Math.cos(a)), (float) (r * Math.sin(a)), 0)));
        }
        CircleGesture cwg = null;
        for (Gesture g : cw.gestures) if (g.type() == Gesture.Type.TYPE_CIRCLE) cwg = new CircleGesture(g);
        check(cwg != null && cwg.normal().getZ() < -0.9f, "CW -> normal -z: " + (cwg == null ? null : cwg.normal()));
        check(cwg != null && cwg.pointable().direction().angleTo(cwg.normal()) <= Math.PI / 2, "Processing lib clockwise test holds");
    }

    static void testTaps() {
        section("Key tap / screen tap");
        Sim s = new Sim();
        s.rec.enable(Gesture.Type.TYPE_KEY_TAP, true);
        s.rec.enable(Gesture.Type.TYPE_SCREEN_TAP, true);
        s.rec.enable(Gesture.Type.TYPE_SWIPE, true);
        Vector palm = new Vector(0, 200, 0);
        Vector rest = palm.plus(new Vector(0, 0, -90));
        for (int i = 0; i < 30; i++) s.step(palm, rest);
        // key tap: 15 mm down in 60 ms (250 mm/s), then back up
        for (int i = 1; i <= 6; i++) s.step(palm, rest.plus(new Vector(0, -2.5f * i, 0)));
        for (int i = 5; i >= 0; i--) s.step(palm, rest.plus(new Vector(0, -2.5f * i, 0)));
        for (int i = 0; i < 60; i++) s.step(palm, rest);
        check(s.count(Gesture.Type.TYPE_KEY_TAP, null) == 1, "one key tap, got " + s.count(Gesture.Type.TYPE_KEY_TAP, null));
        check(s.count(Gesture.Type.TYPE_SCREEN_TAP, null) == 0, "no screen tap on key tap");
        // screen tap: 15 mm forward (-z) in 60 ms then back
        for (int i = 1; i <= 6; i++) s.step(palm, rest.plus(new Vector(0, 0, -2.5f * i)));
        for (int i = 5; i >= 0; i--) s.step(palm, rest.plus(new Vector(0, 0, -2.5f * i)));
        for (int i = 0; i < 60; i++) s.step(palm, rest);
        check(s.count(Gesture.Type.TYPE_SCREEN_TAP, null) == 1, "one screen tap, got " + s.count(Gesture.Type.TYPE_SCREEN_TAP, null));
        check(s.count(Gesture.Type.TYPE_KEY_TAP, null) == 1, "still one key tap");
        check(s.count(Gesture.Type.TYPE_SWIPE, null) == 0, "taps are not swipes");
        for (Gesture g : s.gestures) {
            if (g.type() == Gesture.Type.TYPE_KEY_TAP) {
                KeyTapGesture k = new KeyTapGesture(g);
                check(k.isValid() && k.state() == Gesture.State.STATE_STOP && k.direction().getY() < 0, "key tap fields");
            }
            if (g.type() == Gesture.Type.TYPE_SCREEN_TAP) {
                ScreenTapGesture k = new ScreenTapGesture(g);
                check(k.isValid() && k.direction().getZ() < 0 && k.hands().count() == 1, "screen tap fields");
            }
        }
        // slow sink (below MinDownVelocity) must not tap
        Sim slow = new Sim();
        slow.rec.enable(Gesture.Type.TYPE_KEY_TAP, true);
        for (int i = 0; i < 30; i++) slow.step(palm, rest);
        for (int i = 1; i <= 50; i++) slow.step(palm, rest.plus(new Vector(0, -0.3f * i, 0))); // 30 mm/s
        for (int i = 0; i < 30; i++) slow.step(palm, rest.plus(new Vector(0, -15, 0)));
        check(slow.count(Gesture.Type.TYPE_KEY_TAP, null) == 0, "slow descent is not a key tap");
    }

    static void testGestureStopOnLostFinger() {
        section("Gesture stop when the hand disappears");
        Sim s = new Sim();
        s.rec.enable(Gesture.Type.TYPE_SWIPE, true);
        Vector palm = new Vector(0, 200, 0);
        for (int i = 0; i < 5; i++) s.step(palm, palm.plus(new Vector(-150, 0, -90)));
        for (int i = 1; i <= 20; i++) s.step(palm, palm.plus(new Vector(-150 + 12 * i, 0, -90)));
        check(s.count(Gesture.Type.TYPE_SWIPE, Gesture.State.STATE_START) == 1, "swipe started");
        s.stepEmpty();
        check(s.count(Gesture.Type.TYPE_SWIPE, Gesture.State.STATE_STOP) == 1, "swipe stopped when hand lost");
    }

    static void testConfigAndRecognizerParams() {
        section("Config");
        Config c = new Config(null);
        check(Math.abs(c.getFloat("Gesture.Swipe.MinLength") - 150f) < 1e-6, "default swipe min length");
        check(c.setFloat("Gesture.Swipe.MinLength", 50f) && c.getFloat("Gesture.Swipe.MinLength") == 50f, "set/get float");
        check(c.type("Gesture.Swipe.MinLength") == Config.ValueType.TYPE_FLOAT, "type float");
        check(c.save(), "save without controller");
        GestureRecognizer r = new GestureRecognizer();
        r.setParameter("Gesture.Circle.MinRadius", 12f);
        check(r.circleMinRadius == 12f, "recognizer parameter update");
        check(!r.anyEnabled(), "nothing enabled by default");
        r.enable(Gesture.Type.TYPE_SWIPE, true);
        check(r.isEnabled(Gesture.Type.TYPE_SWIPE) && !r.isEnabled(Gesture.Type.TYPE_CIRCLE), "enable swipe only");
        InteractionBox box = InteractionBox.standard();
        Vector n = box.normalizePoint(box.center());
        check(Math.abs(n.getX() - 0.5f) < 1e-6 && Math.abs(n.getY() - 0.5f) < 1e-6, "interaction box normalise centre");
        check(box.denormalizePoint(n).distanceTo(box.center()) < 1e-3, "denormalise");
    }

    public static void main(String[] args) {
        testVector();
        testFrameParsing();
        testSwipe();
        testCircle();
        testTaps();
        testGestureStopOnLostFinger();
        testConfigAndRecognizerParams();
        System.out.println();
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
