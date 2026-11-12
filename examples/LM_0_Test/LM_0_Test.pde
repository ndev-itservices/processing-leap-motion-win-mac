/**
 * LM_0_Test - checks that the Leap Motion is working.
 *
 * Shows the connection state to the Ultraleap tracking service, the detected
 * device, the number of hands, and draws the hands. If something is wrong the
 * message on screen says what is missing (service not running, device unplugged, ...).
 *
 * Leap Motion for Processing (Apple Silicon fork) - Tiago Martins
 */
import de.voidplus.leapmotion.*;

LeapMotion leap;

void setup() {
  size(800, 500);
  leap = new LeapMotion(this, true); // true = diagnostic messages in the console
  textFont(createFont("Helvetica", 14));
}

void draw() {
  background(30);

  // hands
  for (Hand hand : leap.getHands()) {
    hand.draw();
  }

  // status
  boolean connected = leap.isConnected();
  fill(connected ? color(80, 220, 120) : color(240, 90, 90));
  noStroke();
  ellipse(24, 24, 16, 16);
  fill(255);
  textAlign(LEFT, CENTER);
  if (connected) {
    text("Leap Motion connected  |  " + leap.getFrameRate() + " fps  |  hands: " + leap.countHands()
      + "  |  fingers: " + leap.countFingers(), 42, 24);
  } else {
    text("Leap Motion NOT connected. Check: is the Ultraleap service running? is the device plugged in?", 42, 24);
  }

  int y = 60;
  for (Device d : leap.getDevices()) {
    text("Device: " + d.getRaw() + "  (FOV " + nf(degrees(d.getHorizontalViewAngle()), 0, 1) + "° x "
      + nf(degrees(d.getVerticalViewAngle()), 0, 1) + "°, range " + nf(d.getRange(), 0, 0) + " mm)", 42, y);
    y += 22;
  }

  for (Hand hand : leap.getHands()) {
    PVector p = hand.getRawPosition();
    text((hand.isLeft() ? "Left" : "Right") + "  id " + hand.getId()
      + "  position (" + nf(p.x, 0, 0) + ", " + nf(p.y, 0, 0) + ", " + nf(p.z, 0, 0) + ") mm"
      + "  grab " + nf(hand.getGrabStrength(), 0, 2) + "  pinch " + nf(hand.getPinchStrength(), 0, 2)
      + "  extended fingers " + hand.getOutstretchedFingers().size(), 42, y);
    y += 22;
  }

  fill(160);
  text("Library version " + leap.getVersion() + "  -  " + leap.getVersionSDK(), 42, height - 20);
}

void leapOnConnect() {
  println("Leap Motion: device connected");
}

void leapOnDisconnect() {
  println("Leap Motion: device disconnected");
}
