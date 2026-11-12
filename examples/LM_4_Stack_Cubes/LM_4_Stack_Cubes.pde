/**
 * LM_4_Stack_Cubes - stack cubes on top of each other in 3D.
 *
 * Pinch (thumb + index finger) near a cube to pick it up, move the hand, open the
 * fingers to drop it. A dropped cube falls and lands on the floor or on top of another
 * cube. Both hands work at the same time.
 *
 * Keys:  r = reset the scene   n = add a cube
 *
 * Coordinates are the raw Leap Motion millimetres: x right, y up (0 = device), z towards you.
 * The virtual table is 120 mm above the device, because hands closer than that are not tracked.
 *
 * Leap Motion for Processing (Apple Silicon fork) - Tiago Martins
 */
import de.voidplus.leapmotion.*;
import java.util.HashMap;

LeapMotion leap;

final float CUBE = 40;           // cube edge in mm
final float FLOOR_Y = 120;       // height of the virtual table above the device (mm); hands cannot be tracked lower
final float GRAB_RADIUS = 60;    // max distance between the pinch point and a cube centre to grab it
final float PINCH_ON = 0.6;      // pinch strength to start holding
final float PINCH_OFF = 0.3;     // pinch strength to release (hysteresis)
final float GRAVITY = 9.81 * 100;// mm/s^2 (scaled down from 9810 to make the fall readable)
final float FLOOR_HALF = 220;    // half size of the drawn floor
final int START_CUBES = 6;

ArrayList<Cube> cubes = new ArrayList<Cube>();
HashMap<Integer, Cube> heldByHand = new HashMap<Integer, Cube>();  // hand id -> held cube
HashMap<Integer, Boolean> pinching = new HashMap<Integer, Boolean>();
int lastMillis;

void setup() {
  size(1000, 650, P3D);
  leap = new LeapMotion(this);
  textFont(createFont("Helvetica", 16));
  resetScene();
  lastMillis = millis();
}

void resetScene() {
  cubes.clear();
  heldByHand.clear();
  pinching.clear();
  for (int i = 0; i < START_CUBES; i++) addCube();
}

void addCube() {
  // place on the floor, away from other cubes
  for (int attempt = 0; attempt < 50; attempt++) {
    float x = random(-150, 150);
    float z = random(-60, 100);
    boolean free = true;
    for (Cube c : cubes) if (abs(c.pos.x - x) < CUBE * 1.3 && abs(c.pos.z - z) < CUBE * 1.3 && c.pos.y < FLOOR_Y + CUBE) free = false;
    if (free) { cubes.add(new Cube(x, FLOOR_Y + CUBE / 2, z)); return; }
  }
  cubes.add(new Cube(random(-150, 150), FLOOR_Y + CUBE / 2, random(-60, 100)));
}

void draw() {
  float dt = min((millis() - lastMillis) / 1000.0, 0.05);
  lastMillis = millis();

  background(24, 26, 32);
  setupCamera();
  lights();
  directionalLight(180, 180, 180, -0.5, -1, -0.3);

  updateHands();
  for (Cube c : cubes) c.update(dt);

  drawFloor();
  // cubes within reach of a hand that is not pinching are highlighted
  ArrayList<Cube> reachable = new ArrayList<Cube>();
  for (Hand hand : leap.getHands()) {
    if (heldByHand.containsKey(hand.getId())) continue;
    Cube n = nearestFreeCube(pinchPoint(hand));
    if (n != null) reachable.add(n);
  }
  for (Cube c : cubes) c.draw(reachable.contains(c));
  drawHands();

  drawHud();
}

// ------------------------------------------------------------------ interaction

void updateHands() {
  // hands that disappeared release their cube
  ArrayList<Integer> gone = new ArrayList<Integer>();
  for (int id : heldByHand.keySet()) if (leap.getHand(id) == null && !handPresent(id)) gone.add(id);
  for (int id : gone) { heldByHand.get(id).release(); heldByHand.remove(id); pinching.remove(id); }

  for (Hand hand : leap.getHands()) {
    int id = hand.getId();
    PVector pinchPoint = pinchPoint(hand);
    float strength = hand.getPinchStrength();
    boolean wasPinching = pinching.containsKey(id) && pinching.get(id);
    boolean isPinching = wasPinching ? strength > PINCH_OFF : strength > PINCH_ON;
    pinching.put(id, isPinching);

    Cube held = heldByHand.get(id);
    if (isPinching && held == null) {
      Cube nearest = nearestFreeCube(pinchPoint);
      if (nearest != null) { nearest.grab(); heldByHand.put(id, nearest); held = nearest; }
    } else if (!isPinching && held != null) {
      held.release();
      heldByHand.remove(id);
      held = null;
    }
    if (held != null) held.follow(pinchPoint);
  }
}

boolean handPresent(int id) {
  for (Hand h : leap.getHands()) if (h.getId() == id) return true;
  return false;
}

PVector pinchPoint(Hand hand) {
  PVector thumb = hand.getThumb().getRawPosition();
  PVector index = hand.getIndexFinger().getRawPosition();
  return PVector.lerp(thumb, index, 0.5);
}

Cube nearestFreeCube(PVector p) {
  Cube best = null;
  float bestD = GRAB_RADIUS;
  for (Cube c : cubes) {
    if (c.held) continue;
    float d = PVector.dist(c.pos, p);
    // inside the cube (with some margin) counts as distance zero
    if (abs(c.pos.x - p.x) < CUBE * 0.75 && abs(c.pos.y - p.y) < CUBE * 0.75 && abs(c.pos.z - p.z) < CUBE * 0.75) d = 0;
    if (d < bestD) { bestD = d; best = c; }
  }
  return best;
}

// ------------------------------------------------------------------ cubes

class Cube {
  PVector pos;          // centre, mm
  float vy = 0;         // vertical velocity while falling
  boolean held = false;
  boolean resting = true;
  color col;

  Cube(float x, float y, float z) {
    pos = new PVector(x, y, z);
    col = color(random(90, 255), random(90, 255), random(90, 255));
  }

  void grab() { held = true; resting = false; vy = 0; }

  void release() { held = false; resting = false; vy = 0; }

  void follow(PVector p) {
    pos.set(p.x, max(p.y, FLOOR_Y + CUBE / 2), p.z);
  }

  void update(float dt) {
    if (held || resting) return;
    vy -= GRAVITY * dt;
    float newY = pos.y + vy * dt;
    float landing = landingHeight();
    if (newY <= landing) {
      pos.y = landing;
      vy = 0;
      resting = true;
    } else {
      pos.y = newY;
    }
  }

  // highest surface below this cube that it overlaps enough to rest on
  float landingHeight() {
    float h = FLOOR_Y + CUBE / 2;  // table
    for (Cube o : cubes) {
      if (o == this || o.held) continue;
      float top = o.pos.y + CUBE;
      if (top > pos.y + 0.01) continue;                       // above us: ignore
      float overlapX = CUBE - abs(o.pos.x - pos.x);
      float overlapZ = CUBE - abs(o.pos.z - pos.z);
      if (overlapX >= CUBE * 0.5 && overlapZ >= CUBE * 0.5) h = max(h, top);
    }
    return h;
  }

  // number of cubes in the stack under this one (floor = 0)
  int level() {
    int lvl = 0;
    for (Cube o : cubes) {
      if (o == this || o.held) continue;
      if (abs((o.pos.y + CUBE) - pos.y) < 1 && abs(o.pos.x - pos.x) < CUBE && abs(o.pos.z - pos.z) < CUBE) lvl = max(lvl, o.level() + 1);
    }
    return lvl;
  }

  void draw(boolean reachable) {
    pushMatrix();
    translate(pos.x, pos.y, pos.z);
    noStroke();
    fill(held ? lerpColor(col, color(255), 0.4) : col);
    box(CUBE);
    if (held || reachable) {
      stroke(held ? color(255, 240) : color(90, 220, 130, 200));
      noFill();
      box(CUBE + 4);
    }
    popMatrix();
  }
}

// ------------------------------------------------------------------ drawing

void setupCamera() {
  // look at the interaction space from the front, slightly above; Leap y points up
  camera(0, FLOOR_Y + 330, 640, 0, FLOOR_Y + 110, 0, 0, -1, 0);
  perspective(radians(50), float(width) / height, 10, 3000);
}

void drawFloor() {
  // virtual table at FLOOR_Y
  pushMatrix();
  noStroke();
  fill(45, 48, 58);
  translate(0, FLOOR_Y - 0.5, 0);
  box(FLOOR_HALF * 2, 1, FLOOR_HALF * 2);
  popMatrix();
  stroke(70, 74, 88);
  for (float g = -FLOOR_HALF; g <= FLOOR_HALF; g += CUBE) {
    line(g, FLOOR_Y + 0.2, -FLOOR_HALF, g, FLOOR_Y + 0.2, FLOOR_HALF);
    line(-FLOOR_HALF, FLOOR_Y + 0.2, g, FLOOR_HALF, FLOOR_Y + 0.2, g);
  }
  // the device itself, below the table
  noStroke();
  fill(90, 94, 110);
  pushMatrix();
  translate(0, 1, 0);
  box(80, 2, 30);
  popMatrix();
}

void drawHands() {
  for (Hand hand : leap.getHands()) {
    boolean isPinching = pinching.containsKey(hand.getId()) && pinching.get(hand.getId());
    // fingers as segments
    stroke(200, 200, 210, 180);
    strokeWeight(2);
    for (Finger f : hand.getFingers()) {
      for (int b = 0; b < 4; b++) {
        Bone bone = f.getBone(b);
        PVector a = bone.getRawPrevJoint();
        PVector z = bone.getRawNextJoint();
        line(a.x, a.y, a.z, z.x, z.y, z.z);
      }
    }
    // palm
    PVector palm = hand.getRawPosition();
    pushMatrix();
    translate(palm.x, palm.y, palm.z);
    noStroke();
    fill(200, 200, 210, 120);
    sphere(12);
    popMatrix();
    // pinch point
    PVector p = pinchPoint(hand);
    pushMatrix();
    translate(p.x, p.y, p.z);
    noStroke();
    fill(isPinching ? color(255, 90, 90) : color(90, 220, 130), 220);
    sphere(isPinching ? 7 : 5);
    popMatrix();
  }
  strokeWeight(1);
}

void drawHud() {
  // 2D overlay
  hint(DISABLE_DEPTH_TEST);
  camera();
  perspective();
  noLights();
  fill(255);
  textAlign(LEFT, TOP);
  int tallest = 0;
  for (Cube c : cubes) if (!c.held && c.resting) tallest = max(tallest, c.level() + 1);
  String status = leap.isConnected() ? "hands: " + leap.countHands() + "   fps: " + leap.getFrameRate() : "Leap Motion not connected";
  text("Pinch (thumb + index) a highlighted cube to pick it up, open the fingers to drop it.   r = reset   n = add cube", 16, 14);
  text(status + "   cubes: " + cubes.size() + "   tallest stack: " + tallest, 16, 38);
  hint(ENABLE_DEPTH_TEST);
}

void keyPressed() {
  if (key == 'r' || key == 'R') resetScene();
  if (key == 'n' || key == 'N') addCube();
}
