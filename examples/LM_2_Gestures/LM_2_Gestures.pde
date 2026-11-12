/**
 * LM_2_Gestures - the four gestures: swipe, circle, screen tap and key tap.
 *
 * The panel lists what you can do. A card is grey until the gesture is
 * recognised; then it turns green and shows which hand made it and the
 * details of the gesture (direction, speed, progress, ...).
 *
 * Gestures are recognised with one extended finger (usually the index):
 *   Swipe       move the finger quickly sideways, at least 15 cm
 *   Circle      draw a circle in the air with the finger tip
 *   Screen tap  push the finger forward (towards the screen) and pull back
 *   Key tap     tap down with the finger, like pressing a key
 *
 * Leap Motion for Processing (Apple Silicon fork) - Tiago Martins
 */
import de.voidplus.leapmotion.*;

// ======================================================
// Table of Contents:
// ├─ 1. Swipe Gesture
// ├─ 2. Circle Gesture
// ├─ 3. Screen Tap Gesture
// └─ 4. Key Tap Gesture
// ======================================================

LeapMotion leap;

final int SWIPE = 0, CIRCLE = 1, SCREEN_TAP = 2, KEY_TAP = 3;
String[] names = { "Swipe", "Circle", "Screen tap", "Key tap" };
String[] howTo = {
  "Move one finger quickly sideways (left/right, up/down), at least 15 cm.",
  "Draw a circle in the air with the tip of one finger (keep it going).",
  "Push one finger forward towards the screen, then pull it back.",
  "Tap one finger downwards, like pressing a key on a keyboard."
};
String[] detail = { "", "", "", "" };   // last details per gesture
String[] who = { "", "", "", "" };      // hand that made it
int[] lastTime = { -9999, -9999, -9999, -9999 };  // millis of the last event
boolean[] active = { false, false, false, false }; // gesture in progress (swipe / circle)

color grey = color(70, 72, 80);
color green = color(60, 200, 110);

void setup() {
  size(900, 560);
  textFont(createFont("Helvetica", 15));
  leap = new LeapMotion(this).allowGestures();  // all gestures
  // leap = new LeapMotion(this).allowGestures("circle, swipe, screen_tap, key_tap");
  // leap = new LeapMotion(this).allowGestures("swipe");  // only swipes
}

void draw() {
  background(26, 28, 34);

  // hands on the right side, mapped to the window
  for (Hand hand : leap.getHands()) hand.draw();

  // header
  fill(255);
  textAlign(LEFT, TOP);
  text(leap.isConnected()
    ? "Use one extended finger. hands: " + leap.countHands() + "   " + leap.getFrameRate() + " fps"
    : "Leap Motion not connected", 20, 16);

  // gesture cards
  float cardW = 400, cardH = 100, x = 20, y = 50;
  for (int i = 0; i < 4; i++) {
    float age = (millis() - lastTime[i]) / 1000.0;         // seconds since the last event
    boolean hot = active[i] || age < 1.2;
    float t = active[i] ? 1 : constrain(1 - age / 1.2, 0, 1);
    color bg = lerpColor(grey, green, t);

    noStroke();
    fill(bg);
    rect(x, y + i * (cardH + 12), cardW, cardH, 10);

    fill(255);
    textAlign(LEFT, TOP);
    textSize(20);
    text(names[i], x + 16, y + i * (cardH + 12) + 12);
    textSize(14);
    fill(hot ? color(255) : color(190));
    text(howTo[i], x + 16, y + i * (cardH + 12) + 40, cardW - 32, 40);
    if (hot && who[i].length() > 0) {
      fill(255);
      textAlign(RIGHT, TOP);
      textSize(16);
      text(who[i] + " hand", x + cardW - 16, y + i * (cardH + 12) + 12);
      textAlign(LEFT, TOP);
      textSize(13);
      text(detail[i], x + 16, y + i * (cardH + 12) + 76);
    }
    textSize(15);
  }
}

String handName(Gesture g) {
  if (!g.hasHands()) return "?";
  Hand h = g.getHands().get(0);
  return h.isLeft() ? "Left" : "Right";
}

String dir(PVector d) {
  // dominant direction of a raw direction vector
  float ax = abs(d.x), ay = abs(d.y), az = abs(d.z);
  if (ax >= ay && ax >= az) return d.x > 0 ? "right" : "left";
  if (ay >= az) return d.y > 0 ? "up" : "down";
  return d.z < 0 ? "forward" : "backward";
}

// ======================================================
// 1. Swipe Gesture

void leapOnSwipeGesture(SwipeGesture g, int state) {
  int     id               = g.getId();
  Finger  finger           = g.getFinger();
  PVector position         = g.getPosition();
  PVector positionStart    = g.getStartPosition();
  PVector direction        = g.getRawDirection();
  float   speed            = g.getSpeed();
  long    duration         = g.getDuration();
  float   durationSeconds  = g.getDurationInSeconds();

  who[SWIPE] = handName(g);
  detail[SWIPE] = "direction: " + dir(direction) + "   speed: " + int(speed) + " mm/s   "
    + nf(durationSeconds, 0, 2) + " s   id " + id;
  lastTime[SWIPE] = millis();
  switch (state) {
    case 1: // Start
      active[SWIPE] = true;
      break;
    case 2: // Update
      break;
    case 3: // Stop
      active[SWIPE] = false;
      println("SwipeGesture: " + id + " " + who[SWIPE] + " hand, " + dir(direction));
      break;
  }
}

// ======================================================
// 2. Circle Gesture

void leapOnCircleGesture(CircleGesture g, int state) {
  int     id               = g.getId();
  Finger  finger           = g.getFinger();
  PVector positionCenter   = g.getCenter();
  float   radius           = g.getRadius();
  float   progress         = g.getProgress();
  long    duration         = g.getDuration();
  float   durationSeconds  = g.getDurationInSeconds();
  int     direction        = g.getDirection();   // 0 = anticlockwise, 1 = clockwise

  who[CIRCLE] = handName(g);
  detail[CIRCLE] = (direction == 1 ? "clockwise" : "anticlockwise") + "   turns: " + nf(progress, 0, 1)
    + "   radius: " + int(radius) + " mm   id " + id;
  lastTime[CIRCLE] = millis();
  switch (state) {
    case 1: // Start
      active[CIRCLE] = true;
      break;
    case 2: // Update
      break;
    case 3: // Stop
      active[CIRCLE] = false;
      println("CircleGesture: " + id + " " + who[CIRCLE] + " hand, " + nf(progress, 0, 1) + " turns");
      break;
  }
}

// ======================================================
// 3. Screen Tap Gesture

void leapOnScreenTapGesture(ScreenTapGesture g) {
  int     id               = g.getId();
  Finger  finger           = g.getFinger();
  PVector position         = g.getRawPosition();
  PVector direction        = g.getDirection();
  long    duration         = g.getDuration();
  float   durationSeconds  = g.getDurationInSeconds();

  who[SCREEN_TAP] = handName(g);
  detail[SCREEN_TAP] = "at (" + int(position.x) + ", " + int(position.y) + ", " + int(position.z) + ") mm   id " + id;
  lastTime[SCREEN_TAP] = millis();
  println("ScreenTapGesture: " + id + " " + who[SCREEN_TAP] + " hand");
}

// ======================================================
// 4. Key Tap Gesture

void leapOnKeyTapGesture(KeyTapGesture g) {
  int     id               = g.getId();
  Finger  finger           = g.getFinger();
  PVector position         = g.getRawPosition();
  PVector direction        = g.getDirection();
  long    duration         = g.getDuration();
  float   durationSeconds  = g.getDurationInSeconds();

  who[KEY_TAP] = handName(g);
  detail[KEY_TAP] = "at (" + int(position.x) + ", " + int(position.y) + ", " + int(position.z) + ") mm   id " + id;
  lastTime[KEY_TAP] = millis();
  println("KeyTapGesture: " + id + " " + who[KEY_TAP] + " hand");
}
