/**
 * Automated Processing test: runs the library for ~4 s against the mock libLeapC and prints
 * a RESULT line. Launch with scripts/run_sketch_test.sh (sets -Dleap.leapc.path through
 * JAVA_TOOL_OPTIONS). Author: Tiago Martins
 */
import de.voidplus.leapmotion.*;

LeapMotion leap;
int frames = 0, circles = 0, connects = 0, maxHands = 0, maxFingers = 0, imagesSeen = 0;
float lastGrab = -1;
PVector lastIndex;

void setup() {
  size(400, 300);
  leap = new LeapMotion(this, true).withGestures("circle").allowImages();
}

void draw() {
  background(20);
  for (Hand hand : leap.getHands()) {
    hand.draw();
    maxHands = max(maxHands, leap.countHands());
    maxFingers = max(maxFingers, hand.getOutstretchedFingers().size());
    lastGrab = hand.getGrabStrength();
    lastIndex = hand.getIndexFinger().getRawPosition();
  }
  if (leap.hasImages()) imagesSeen = max(imagesSeen, leap.getImages().size());
  if (frameCount > 240) {
    println("RESULT connected=" + leap.isConnected() + " leapFrames=" + frames + " fps=" + leap.getFrameRate()
      + " connects=" + connects + " maxHands=" + maxHands + " maxFingers=" + maxFingers
      + " grab=" + lastGrab + " indexTip=" + lastIndex + " circles=" + circles + " images=" + imagesSeen
      + " devices=" + leap.getDevices().size());
    exit();
  }
}

void leapOnFrame() { frames++; }
void leapOnConnect() { connects++; }
void leapOnCircleGesture(CircleGesture g, int state) { circles++; }
