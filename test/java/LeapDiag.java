import com.leapmotion.leap.Controller;
import com.leapmotion.leap.Device;
import com.leapmotion.leap.Finger;
import com.leapmotion.leap.Frame;
import com.leapmotion.leap.Gesture;
import com.leapmotion.leap.Hand;
import com.leapmotion.leap.Listener;

/**
 * Command line diagnostic: connects to the Ultraleap service through the Java API and
 * prints connection, device and tracking information for a few seconds.
 * Usage: java -Djava.library.path=<dir with libLeapJava.dylib> -cp LeapJava.jar LeapDiag [seconds]
 *
 * @author Tiago Martins
 */
public class LeapDiag {

    public static void main(String[] args) throws Exception {
        int seconds = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        Controller.setVerbose(true);
        final long[] frames = {0};
        final int[] maxHands = {0};
        Controller c = new Controller(new Listener() {
            @Override public void onInit(Controller c) { System.out.println("[event] onInit"); }
            @Override public void onServiceConnect(Controller c) { System.out.println("[event] onServiceConnect  (service version " + c.serviceVersion() + ", client " + c.clientVersion() + ")"); }
            @Override public void onServiceDisconnect(Controller c) { System.out.println("[event] onServiceDisconnect"); }
            @Override public void onConnect(Controller c) { System.out.println("[event] onConnect  devices=" + c.devices()); }
            @Override public void onDisconnect(Controller c) { System.out.println("[event] onDisconnect"); }
            @Override public void onDeviceChange(Controller c) { System.out.println("[event] onDeviceChange devices=" + c.devices().count()); }
            @Override public void onImages(Controller c) { }
            @Override public void onFrame(Controller c) {
                frames[0]++;
                Frame f = c.frame();
                if (f.hands().count() > maxHands[0]) maxHands[0] = f.hands().count();
                if (frames[0] % 60 == 1) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("frame ").append(f.id()).append(" fps=").append((int) f.currentFramesPerSecond()).append(" hands=").append(f.hands().count());
                    for (Hand h : f.hands()) {
                        sb.append("\n    ").append(h.isLeft() ? "L" : "R").append(" palm=").append(h.palmPosition())
                          .append(" grab=").append(String.format("%.2f", h.grabStrength())).append(" pinch=").append(String.format("%.2f", h.pinchStrength()))
                          .append(" extended=").append(h.fingers().extended().count());
                        Finger i = h.fingers().fingerType(Finger.Type.TYPE_INDEX).get(0);
                        sb.append(" indexTip=").append(i.tipPosition()).append(" v=").append((int) i.tipVelocity().magnitude());
                    }
                    for (Gesture g : f.gestures()) sb.append("\n    gesture ").append(g);
                    System.out.println(sb);
                }
            }
        });
        if (!c.isOpen()) {
            System.out.println("Controller could not open LeapC: " + c.openError());
            System.exit(2);
        }
        c.enableGesture(Gesture.Type.TYPE_SWIPE);
        c.enableGesture(Gesture.Type.TYPE_CIRCLE);
        c.enableGesture(Gesture.Type.TYPE_KEY_TAP);
        c.enableGesture(Gesture.Type.TYPE_SCREEN_TAP);
        for (int i = 0; i < seconds; i++) {
            Thread.sleep(1000);
            System.out.println("t=" + (i + 1) + "s service=" + c.isServiceConnected() + " connected=" + c.isConnected()
                    + " frames=" + frames[0] + " maxHands=" + maxHands[0]);
        }
        for (Device d : c.devices()) System.out.println("device: " + d);
        System.out.println("RESULT service=" + c.isServiceConnected() + " device=" + c.isConnected() + " frames=" + frames[0] + " maxHands=" + maxHands[0]);
        c.delete();
        System.exit(c.isServiceConnected() ? 0 : 3);
    }
}
