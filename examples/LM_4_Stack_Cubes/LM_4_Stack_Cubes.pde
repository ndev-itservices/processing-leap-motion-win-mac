/**
 * LM_4_Stack_Cubes - stack cubes on top of each other in 3D.
 *
 * Pinch (thumb + index finger) near a highlighted cube to pick it up, move the hand,
 * open the fingers to drop it (or throw it: the cube keeps the speed of the hand).
 * Cubes fall with gravity, land on the table or on other cubes, push each other,
 * and tip over the edge when they are not supported. Both hands work at the same time.
 *
 * The physics is a small axis-aligned rigid body simulation (cubes never rotate):
 * gravity, cube/table and cube/cube collisions resolved on the axis of least
 * penetration, inelastic contacts, friction, and a held cube that acts as a
 * kinematic body pushing the others.
 *
 * Keys:  r = reset the scene   n = add a cube   + / - = raise / lower the table
 *
 * Coordinates are the raw Leap Motion millimetres: x right, y up (0 = device), z towards you.
 * The virtual table starts 200 mm above the device (the centre of the tracking zone); hands
 * closer than about 100 mm to the device are not tracked.
 *
 * Leap Motion for Processing (Apple Silicon fork) - Tiago Martins
 */
import de.voidplus.leapmotion.*;
import java.util.HashMap;

LeapMotion leap;

final float CUBE = 40;            // cube edge in mm
final float HALF = CUBE / 2;
float FLOOR_Y = 200;              // height of the virtual table above the device (mm); keys + and - change it
final float FLOOR_HALF = 220;     // half size of the drawn table
final float GRAB_RADIUS = 60;     // max distance between the pinch point and a cube centre to grab it
final float PINCH_ON = 0.6;       // pinch strength to start holding
final float PINCH_OFF = 0.3;      // pinch strength to release (hysteresis)
final float GRAVITY = 4000;       // mm/s^2 (real gravity is 9810; a bit slower reads better)
final float FRICTION = 10;        // horizontal velocity damping per second while supported
final float MAX_THROW = 1500;     // mm/s
final int SUBSTEPS = 4;           // physics sub-steps per frame
final int SOLVER_ITERATIONS = 4;  // collision resolution passes per sub-step
final int START_CUBES = 6;

ArrayList<Cube> cubes = new ArrayList<Cube>();
HashMap<Integer, Cube> heldByHand = new HashMap<Integer, Cube>();     // hand id -> held cube
HashMap<Integer, Boolean> pinching = new HashMap<Integer, Boolean>();
HashMap<Integer, PVector> lastPinch = new HashMap<Integer, PVector>(); // hand id -> previous pinch point
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
  lastPinch.clear();
  for (int i = 0; i < START_CUBES; i++) addCube();
}

void addCube() {
  // place on the table, away from other cubes
  for (int attempt = 0; attempt < 50; attempt++) {
    float x = random(-150, 150);
    float z = random(-60, 100);
    boolean free = true;
    for (Cube c : cubes) if (abs(c.pos.x - x) < CUBE * 1.3 && abs(c.pos.z - z) < CUBE * 1.3 && c.pos.y < FLOOR_Y + CUBE) free = false;
    if (free) { cubes.add(new Cube(x, FLOOR_Y + HALF, z)); return; }
  }
  cubes.add(new Cube(random(-150, 150), FLOOR_Y + HALF + 120, random(-60, 100)));  // drop it from above
}

void draw() {
  float dt = min((millis() - lastMillis) / 1000.0, 0.05);
  lastMillis = millis();

  background(24, 26, 32);
  setupCamera();
  lights();
  directionalLight(180, 180, 180, -0.5, -1, -0.3);

  updateHands(dt);
  simulate(dt);

  drawFloor();
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

void updateHands(float dt) {
  // hands that disappeared release their cube
  ArrayList<Integer> gone = new ArrayList<Integer>();
  for (int id : heldByHand.keySet()) if (!handPresent(id)) gone.add(id);
  for (int id : gone) { heldByHand.get(id).release(new PVector()); heldByHand.remove(id); pinching.remove(id); lastPinch.remove(id); }

  for (Hand hand : leap.getHands()) {
    int id = hand.getId();
    PVector p = pinchPoint(hand);
    PVector prev = lastPinch.get(id);
    PVector handVel = (prev == null || dt <= 0) ? new PVector() : PVector.sub(p, prev).div(dt);
    lastPinch.put(id, p.copy());

    float strength = hand.getPinchStrength();
    boolean wasPinching = pinching.containsKey(id) && pinching.get(id);
    boolean isPinching = wasPinching ? strength > PINCH_OFF : strength > PINCH_ON;
    pinching.put(id, isPinching);

    Cube held = heldByHand.get(id);
    if (isPinching && held == null) {
      Cube nearest = nearestFreeCube(p);
      if (nearest != null) { nearest.grab(); heldByHand.put(id, nearest); held = nearest; }
    } else if (!isPinching && held != null) {
      held.release(handVel);
      heldByHand.remove(id);
      held = null;
    }
    if (held != null) held.follow(p, dt);
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
    if (abs(c.pos.x - p.x) < CUBE * 0.75 && abs(c.pos.y - p.y) < CUBE * 0.75 && abs(c.pos.z - p.z) < CUBE * 0.75) d = 0;
    if (d < bestD) { bestD = d; best = c; }
  }
  return best;
}

// ------------------------------------------------------------------ physics

void simulate(float frameDt) {
  float dt = frameDt / SUBSTEPS;
  for (int s = 0; s < SUBSTEPS; s++) {
    // integrate
    for (Cube c : cubes) {
      if (c.held) continue;
      c.supported = false;
      c.vel.y -= GRAVITY * dt;
      c.pos.add(PVector.mult(c.vel, dt));
    }
    // resolve contacts a few times so stacks converge
    for (int it = 0; it < SOLVER_ITERATIONS; it++) {
      for (Cube c : cubes) c.collideTable();
      for (int i = 0; i < cubes.size(); i++)
        for (int j = i + 1; j < cubes.size(); j++)
          collide(cubes.get(i), cubes.get(j));
    }
    // friction and rest
    for (Cube c : cubes) {
      if (c.held) continue;
      if (c.supported) {
        float damp = max(0, 1 - FRICTION * dt);
        c.vel.x *= damp;
        c.vel.z *= damp;
        if (abs(c.vel.x) < 3) c.vel.x = 0;
        if (abs(c.vel.z) < 3) c.vel.z = 0;
      }
    }
  }
}

// axis-aligned cube/cube contact: separate along the axis of least penetration
void collide(Cube a, Cube b) {
  if (a.held && b.held) return;
  float dx = b.pos.x - a.pos.x, dy = b.pos.y - a.pos.y, dz = b.pos.z - a.pos.z;
  float px = CUBE - abs(dx), py = CUBE - abs(dy), pz = CUBE - abs(dz);
  if (px <= 0 || py <= 0 || pz <= 0) return;   // no overlap

  int axis;                                     // 0 = x, 1 = y, 2 = z
  if (py <= px && py <= pz) axis = 1; else if (px <= pz) axis = 0; else axis = 2;

  if (axis == 1) {
    // vertical contact: a cube whose centre hangs beyond the face below is not supported
    // and tips off sideways instead of balancing on the edge
    Cube upper = dy > 0 ? b : a;
    Cube lower = dy > 0 ? a : b;
    boolean centred = abs(upper.pos.x - lower.pos.x) < HALF && abs(upper.pos.z - lower.pos.z) < HALF;
    if (!centred && !upper.held) axis = (px <= pz) ? 0 : 2;
  }

  float d = axis == 0 ? dx : (axis == 1 ? dy : dz);
  float pen = axis == 0 ? px : (axis == 1 ? py : pz);
  float sign = d >= 0 ? 1 : -1;                 // direction from a to b along the axis
  PVector n = new PVector(axis == 0 ? sign : 0, axis == 1 ? sign : 0, axis == 2 ? sign : 0);

  // positions
  if (a.held) {
    b.pos.add(PVector.mult(n, pen));
  } else if (b.held) {
    a.pos.sub(PVector.mult(n, pen));
  } else if (axis == 1) {
    // let the lower cube keep its place (it is usually resting on something)
    if (sign > 0) b.pos.y += pen; else a.pos.y += pen;
  } else {
    a.pos.sub(PVector.mult(n, pen * 0.5));
    b.pos.add(PVector.mult(n, pen * 0.5));
  }

  // velocities along the contact normal: inelastic, only if they approach each other
  float va = a.vel.dot(n), vb = b.vel.dot(n);
  if (vb - va < 0) {
    if (a.held) {
      b.vel.add(PVector.mult(n, va - vb));
    } else if (b.held) {
      a.vel.add(PVector.mult(n, vb - va));
    } else {
      float avg = (va + vb) * 0.5;
      a.vel.add(PVector.mult(n, avg - va));
      b.vel.add(PVector.mult(n, avg - vb));
    }
  }
  if (axis == 1) {
    Cube upper = sign > 0 ? b : a;
    upper.supported = true;
  }
}

class Cube {
  PVector pos;          // centre, mm
  PVector vel = new PVector();
  boolean held = false;
  boolean supported = false;
  color col;

  Cube(float x, float y, float z) {
    pos = new PVector(x, y, z);
    col = color(random(90, 255), random(90, 255), random(90, 255));
  }

  void grab() { held = true; vel.set(0, 0, 0); }

  void release(PVector handVelocity) {
    held = false;
    vel = handVelocity.copy();
    if (vel.mag() > MAX_THROW) vel.setMag(MAX_THROW);
  }

  void follow(PVector p, float dt) {
    PVector target = new PVector(p.x, max(p.y, FLOOR_Y + HALF), p.z);
    if (dt > 0) vel = PVector.sub(target, pos).div(dt);   // used to push other cubes
    pos.set(target);
  }

  void collideTable() {
    if (held) return;
    if (pos.y - HALF < FLOOR_Y) {
      pos.y = FLOOR_Y + HALF;
      if (vel.y < 0) vel.y = 0;
      supported = true;
    }
  }

  // number of cubes in the stack under this one (table = 0)
  int level() {
    int lvl = 0;
    for (Cube o : cubes) {
      if (o == this || o.held) continue;
      if (abs((o.pos.y + CUBE) - pos.y) < 2 && abs(o.pos.x - pos.x) < CUBE && abs(o.pos.z - pos.z) < CUBE) lvl = max(lvl, o.level() + 1);
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
    PVector palm = hand.getRawPosition();
    pushMatrix();
    translate(palm.x, palm.y, palm.z);
    noStroke();
    fill(200, 200, 210, 120);
    sphere(12);
    popMatrix();
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
  hint(DISABLE_DEPTH_TEST);
  camera();
  perspective();
  noLights();
  fill(255);
  textAlign(LEFT, TOP);
  int tallest = 0;
  for (Cube c : cubes) if (!c.held) tallest = max(tallest, c.level() + 1);
  String status = leap.isConnected() ? "hands: " + leap.countHands() + "   fps: " + leap.getFrameRate() : "Leap Motion not connected";
  text("Pinch (thumb + index) a highlighted cube to pick it up, open the fingers to drop or throw it.   r = reset   n = add cube", 16, 14);
  text(status + "   cubes: " + cubes.size() + "   tallest stack: " + tallest + "   table height: " + int(FLOOR_Y) + " mm (+/-)", 16, 38);
  hint(ENABLE_DEPTH_TEST);
}

void keyPressed() {
  if (key == 'r' || key == 'R') resetScene();
  if (key == 'n' || key == 'N') addCube();
  if (key == '+' || key == '=') moveTable(20);
  if (key == '-' || key == '_') moveTable(-20);
}

void moveTable(float delta) {
  float newY = constrain(FLOOR_Y + delta, 100, 400);
  float d = newY - FLOOR_Y;
  FLOOR_Y = newY;
  for (Cube c : cubes) if (!c.held) c.pos.y += d;   // everything moves with the table
}
