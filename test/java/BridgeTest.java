import com.leapmotion.leap.CircleGesture;
import com.leapmotion.leap.Controller;
import com.leapmotion.leap.Device;
import com.leapmotion.leap.Finger;
import com.leapmotion.leap.Frame;
import com.leapmotion.leap.Gesture;
import com.leapmotion.leap.Hand;
import com.leapmotion.leap.Image;
import com.leapmotion.leap.Listener;
import com.leapmotion.leap.Vector;

/**
 * End-to-end test of the JNI bridge + Java API against the fake libLeapC (native/mock).
 * Run with: java -Dleap.leapc.path=native/mock/build/libLeapC-mock.dylib
 *               -Djava.library.path=build/native -cp build/classes:build/test-classes BridgeTest
 *
 * @author Tiago Martins
 */
public class BridgeTest {

    private static int failures = 0, checks = 0;

    private static void check(boolean cond, String msg) {
        checks++;
        if (!cond) { failures++; System.out.println("  FAIL: " + msg); }
    }

    public static void main(String[] args) throws Exception {
        final int[] events = new int[8]; // init, serviceConnect, connect, frame, deviceChange, images, exit
        Controller c = new Controller(new Listener() {
            @Override public void onInit(Controller c) { events[0]++; }
            @Override public void onServiceConnect(Controller c) { events[1]++; }
            @Override public void onConnect(Controller c) { events[2]++; }
            @Override public void onFrame(Controller c) { events[3]++; }
            @Override public void onDeviceChange(Controller c) { events[4]++; }
            @Override public void onImages(Controller c) { events[5]++; }
            @Override public void onExit(Controller c) { events[6]++; }
        });
        check(c.isOpen(), "controller opened LeapC: " + c.openError());
        if (!c.isOpen()) { System.out.println(checks + " checks, " + failures + " failures"); System.exit(1); }

        c.enableGesture(Gesture.Type.TYPE_CIRCLE);
        c.setPolicy(Controller.PolicyFlag.POLICY_BACKGROUND_FRAMES);
        c.setPolicy(Controller.PolicyFlag.POLICY_IMAGES);

        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < 2500) Thread.sleep(50);

        System.out.println("== connection");
        check(c.isServiceConnected(), "service connected");
        check(c.isConnected(), "device connected");
        check(events[0] == 1, "onInit once");
        check(events[1] >= 1, "onServiceConnect fired");
        check(events[2] >= 1, "onConnect fired");
        check(events[4] >= 1, "onDeviceChange fired");
        check("6.2.0".equals(c.clientVersion()), "client version via LeapGetVersion: " + c.clientVersion());

        System.out.println("== device");
        check(c.devices().count() == 1, "one device");
        Device d = c.devices().get(0);
        check(d.isValid() && "MOCK-LMC-0001".equals(d.serialNumber()), "serial: " + d.serialNumber());
        check(d.type() == Device.Type.TYPE_PERIPHERAL, "type peripheral");
        check(Math.abs(d.horizontalViewAngle() - 2.303835f) < 1e-5 && Math.abs(d.verticalViewAngle() - 2.007129f) < 1e-5, "fov");
        check(Math.abs(d.range() - 800f) < 1e-3 && Math.abs(d.baseline() - 40f) < 1e-3, "range/baseline in mm: " + d.range() + "/" + d.baseline());
        check(d.isStreaming(), "streaming");

        System.out.println("== frames");
        Frame f = c.frame();
        check(f.isValid() && f.id() > 20, "frames flowing, id=" + f.id() + " onFrame=" + events[3]);
        check(events[3] > 30, "more than 30 onFrame callbacks in 2.5 s (CI runners are slow): " + events[3]);
        check(Math.abs(f.currentFramesPerSecond() - 100f) < 1e-3, "fps 100");
        check(f.hands().count() == 1, "one hand");
        Hand h = f.hands().get(0);
        check(h.id() == 12 && h.isRight() && !h.isLeft(), "hand id/handedness");
        check(Math.abs(h.grabStrength() - 0.5f) < 1e-6 && Math.abs(h.pinchStrength() - 0.25f) < 1e-6, "grab/pinch");
        check(h.palmPosition().equals(new Vector(20, 200, 10)), "palm position: " + h.palmPosition());
        check(h.palmNormal().equals(new Vector(0, -1, 0)) && h.direction().equals(new Vector(0, 0, -1)), "palm normal/direction");
        check(Math.abs(h.palmWidth() - 85f) < 1e-6, "palm width");
        check(h.timeVisible() > 0.5f, "time visible: " + h.timeVisible());
        check(h.fingers().count() == 5 && h.fingers().extended().count() == 5, "five extended fingers");
        Finger idx = h.fingers().fingerType(Finger.Type.TYPE_INDEX).get(0);
        check(idx.id() == 121, "index finger id " + idx.id());
        float r = idx.tipPosition().distanceTo(new Vector(20, 200, -80));
        check(Math.abs(r - 30f) < 0.01f, "index tip on the 30 mm circle: r=" + r);
        check(idx.tipVelocity().magnitude() > 150 && idx.tipVelocity().magnitude() < 450, "tip velocity ~283 mm/s: " + idx.tipVelocity().magnitude());
        check(idx.bone(com.leapmotion.leap.Bone.Type.TYPE_METACARPAL).prevJoint().equals(h.palmPosition()), "metacarpal starts at palm");
        check(Math.abs(idx.width() - 15f) < 1e-6, "bone width");
        check(h.arm().isValid() && h.arm().wristPosition().equals(new Vector(20, 190, 70)) && Math.abs(h.arm().width() - 50f) < 1e-6, "arm");
        Frame f1 = c.frame(1);   // frames keep arriving (fast on slow CI runners), so only check it is a recent valid frame
        check(f1.isValid() && Math.abs(f1.id() - f.id()) <= 20, "frame history: " + f1.id() + " vs " + f.id());

        System.out.println("== policy / images");
        check(c.isPolicySet(Controller.PolicyFlag.POLICY_BACKGROUND_FRAMES), "background frames policy");
        check(c.isPolicySet(Controller.PolicyFlag.POLICY_IMAGES), "images policy");
        check(events[5] > 5, "onImages callbacks: " + events[5]);
        check(c.images().count() == 2, "two images");
        if (c.images().count() == 2) {
            Image im = c.images().get(0);
            check(im.width() == 640 && im.height() == 240 && im.bytesPerPixel() == 1, "image size 640x240x1");
            check(im.data().length == 640 * 240 && (im.data()[5] & 0xFF) == 5, "left image data");
            check((c.images().get(1).data()[5] & 0xFF) == 105, "right image data");
            check(im.format() == Image.FormatType.INFRARED, "IR format");
        }
        check(f.images().count() == c.images().count(), "frame.images()");
        // rectilinear <-> pixel mapping (mock: pixel = (320 + 135 h, 120 + 68 v))
        Vector px = c.rectilinearToPixel(0, new Vector(0.5f, -0.25f, 1));
        check(px != null && Math.abs(px.getX() - 387.5f) < 1e-3 && Math.abs(px.getY() - 103f) < 1e-3, "rectilinearToPixel: " + px);
        Vector ray = c.pixelToRectilinear(1, new Vector(455f, 120f, 0));
        check(ray != null && Math.abs(ray.getX() - 1f) < 1e-5 && Math.abs(ray.getY()) < 1e-5 && ray.getZ() == 1f, "pixelToRectilinear: " + ray);
        if (c.images().count() == 2) {
            Image left = c.images().get(0), right = c.images().get(1);
            Vector w = left.warp(new Vector(0.5f, -0.25f, 0));
            check(Math.abs(w.getX() - 387.5f) < 1e-3, "Image.warp: " + w);
            Vector rr = left.rectify(new Vector(320f, 120f, 0));
            check(Math.abs(rr.getX()) < 1e-6 && Math.abs(rr.getY()) < 1e-6, "Image.rectify centre: " + rr);
            float[] sc = left.calibrationScale();
            check(sc[0] == 1f && sc[1] == 1f, "calibration scale 1,1 (calibration matches the image): " + sc[0] + ", " + sc[1]);
            float[] ex = c.extrinsicCameraMatrix(0);
            check(ex != null && ex[12] == -20f && ex[0] == 1f && ex[5] == 1f, "extrinsic matrix: identity rotation, left camera at x = -20");
            // straight above the left camera: ray (0, 0) -> principal point
            Vector pr = left.project(new Vector(-20, 200, 0));
            check(Math.abs(pr.getX() - 320f) < 1e-3 && Math.abs(pr.getY() - 120f) < 1e-3, "project above left camera -> principal point: " + pr);
            check(Float.isNaN(left.project(new Vector(0, 0, 0)).getX()), "project below the device is NaN");
            // 100 mm to the right of the left camera: h = -100/200 = -0.5 -> x = 320 - 67.5 (raw image is mirrored)
            Vector pr2 = left.project(new Vector(80, 200, 0));
            check(Math.abs(pr2.getX() - 252.5f) < 1e-2 && Math.abs(pr2.getY() - 120f) < 1e-2, "project right of the camera -> left in the raw image: " + pr2);
            // 50 mm towards the user above the right camera: v = 50/200 = 0.25 -> y = 120 + 17 (towards the user = bottom)
            Vector pr3 = right.project(new Vector(20, 200, 50));
            check(Math.abs(pr3.getX() - 320f) < 1e-2 && Math.abs(pr3.getY() - 137f) < 1e-2, "project towards the user -> bottom of the image: " + pr3);
            // the real device sample: palm (-66.8, 181.4, 203.8) on the left image lands bottom-right of centre
            Vector palm = left.project(new Vector(-66.8f, 181.4f, 203.8f));
            check(palm.getX() > 340 && palm.getX() < 370 && palm.getY() > 180 && palm.getY() < 240, "real-device palm sample: " + palm);
            // the fixed-offset mode gives the same result as the extrinsic one on this device
            Vector v2 = left.project(new Vector(-66.8f, 181.4f, 203.8f), Image.MODE_V2);
            check(palm.distanceTo(v2) < 1e-3, "MODE_V2 == MODE_EXTRINSIC for a +-20 mm offset: " + v2);
        }

        System.out.println("== gestures (circle drawn by the mock)");
        int circles = 0; CircleGesture last = null;
        for (int i = 0; i < 40; i++) {
            Frame fr = c.frame(i);
            for (Gesture g : fr.gestures()) if (g.type() == Gesture.Type.TYPE_CIRCLE) { circles++; if (last == null) last = new CircleGesture(g); }
        }
        check(circles > 0, "circle gesture recognised in recent frames: " + circles);
        check(last != null && last.radius() > 20 && last.radius() < 40, "circle radius ~30: " + (last == null ? 0 : last.radius()));
        check(last != null && last.center().distanceTo(new Vector(20, 200, -80)) < 10, "circle center");
        check(last != null && last.pointable().isFinger() && new Finger(last.pointable()).type() == Finger.Type.TYPE_INDEX, "circle pointable = index");

        System.out.println("== config");
        check(c.config().setFloat("tracking_processing_auto_flip", 1.0f) && c.config().save(), "config save through LeapSaveConfigValue");
        check(c.config().setFloat("Gesture.Circle.MinRadius", 7f) && c.config().getFloat("Gesture.Circle.MinRadius") == 7f, "gesture config");

        System.out.println("== shutdown");
        c.delete();
        check(events[6] == 1, "onExit once");
        int framesAtExit = events[3];
        Thread.sleep(300);
        check(events[3] == framesAtExit, "no callbacks after delete()");
        check(!c.isOpen(), "closed");

        System.out.println();
        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
