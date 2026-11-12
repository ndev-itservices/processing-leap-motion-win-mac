/**
 * LM_5_Camera_Overlay - shows the infrared camera image with the tracked hand
 * skeleton (bones and joints) drawn on top of it.
 *
 * Every 3D joint position is projected onto the camera image with
 * Image.project(), which uses the camera calibration of the device
 * (lens distortion included), so the drawing matches the real hand in the picture.
 *
 * The cameras look upwards, so the raw image is mirrored compared with what you see when you
 * look down at your hands. By default the image is mirrored back (key m toggles it, key v flips
 * it vertically); the skeleton follows the same transform.
 *
 * Keys:  1 = left camera   2 = right camera   b = both   s = toggle skeleton
 *        m = mirror horizontally   v = flip vertically
 *        p = cycle the 3D-to-pixel projection mode   d = print debug info to the console
 *
 * Leap Motion for Processing (Apple Silicon fork) - Tiago Martins
 */
import de.voidplus.leapmotion.*;

LeapMotion leap;
int view = 0;            // 0 = left camera, 1 = right camera, 2 = both
boolean showSkeleton = true;
boolean mirror = true;   // mirror horizontally (natural "looking down" view)
boolean flipV = false;   // flip vertically (off: what is near you is at the bottom, like looking down at the table)
int projectionMode = 0;  // com.leapmotion.leap.Image.MODE_* (key p)

color boneColor = color(0, 255, 160);
color jointColor = color(255, 200, 0);
color palmColor = color(80, 160, 255);

void setup() {
  size(1280, 560);
  leap = new LeapMotion(this).allowImages();
  textFont(createFont("Helvetica", 14));
}

void draw() {
  background(0);

  ArrayList<Image> images = leap.getImages();
  if (images.isEmpty()) {
    fill(255);
    textAlign(CENTER, CENTER);
    text(leap.isConnected() ? "Waiting for camera images..." : "Leap Motion not connected", width / 2, height / 2);
    return;
  }

  if (view == 2) {
    // both cameras side by side
    float w = width / 2.0;
    float h = w * images.get(0).height / images.get(0).width;
    float y = (height - h) / 2;
    for (int i = 0; i < min(2, images.size()); i++) {
      drawCamera(images.get(i), i * w, y, w, h);
    }
  } else {
    int index = min(view, images.size() - 1);
    Image img = images.get(index);
    float w = width;
    float h = w * img.height / img.width;
    if (h > height) { h = height; w = h * img.width / img.height; }
    drawCamera(img, (width - w) / 2, (height - h) / 2, w, h);
  }

  fill(255);
  textAlign(LEFT, TOP);
  text("1 = left   2 = right   b = both   s = skeleton   m = mirror (" + (mirror ? "on" : "off") + ")   v = flip ("
    + (flipV ? "on" : "off") + ")     hands: " + leap.countHands() + "   " + leap.getFrameRate() + " fps", 12, 10);
  text("p = projection mode " + projectionMode + ": " + com.leapmotion.leap.Image.projectionModeName(projectionMode)
    + "     d = print debug info", 12, 30);
}

// image pixel -> screen, honouring the mirror / flip settings
PVector toScreen(Image img, PVector raw, float x, float y, float w, float h) {
  PVector p = img.project(raw);
  if (p == null) return null;
  float sx = p.x / img.width, sy = p.y / img.height;
  if (mirror) sx = 1 - sx;
  if (flipV) sy = 1 - sy;
  return new PVector(x + sx * w, y + sy * h);
}

void drawCamera(Image img, float x, float y, float w, float h) {
  pushMatrix();
  translate(x + (mirror ? w : 0), y + (flipV ? h : 0));
  scale(mirror ? -1 : 1, flipV ? -1 : 1);
  image(img, 0, 0, w, h);
  popMatrix();
  noFill();
  stroke(90);
  rect(x, y, w, h);
  fill(255);
  textAlign(LEFT, BOTTOM);
  text((img.getId() == 0 ? "left" : "right") + " camera  " + img.width + "x" + img.height, x + 8, y + h - 6);
  if (!showSkeleton) return;

  for (Hand hand : leap.getHands()) {
    // bones
    stroke(boneColor);
    strokeWeight(3);
    for (Finger finger : hand.getFingers()) {
      for (int b = 0; b < 4; b++) {
        Bone bone = finger.getBone(b);
        PVector a = toScreen(img, bone.getRawPrevJoint(), x, y, w, h);
        PVector z = toScreen(img, bone.getRawNextJoint(), x, y, w, h);
        if (a != null && z != null) line(a.x, a.y, z.x, z.y);
      }
    }
    // joints
    noStroke();
    fill(jointColor);
    for (Finger finger : hand.getFingers()) {
      for (int b = 0; b < 4; b++) {
        PVector j = toScreen(img, finger.getBone(b).getRawNextJoint(), x, y, w, h);
        if (j != null) ellipse(j.x, j.y, 8, 8);
      }
      PVector mcp = toScreen(img, finger.getBone(0).getRawPrevJoint(), x, y, w, h);
      if (mcp != null) ellipse(mcp.x, mcp.y, 8, 8);
    }
    // palm and wrist
    fill(palmColor);
    PVector palm = toScreen(img, hand.getRawPosition(), x, y, w, h);
    if (palm != null) ellipse(palm.x, palm.y, 14, 14);
    PVector wrist = toScreen(img, hand.getArm().getWristRawPosition(), x, y, w, h);
    if (wrist != null) ellipse(wrist.x, wrist.y, 10, 10);
    if (palm != null && wrist != null) {
      stroke(palmColor);
      strokeWeight(2);
      line(palm.x, palm.y, wrist.x, wrist.y);
    }
    // label
    if (palm != null) {
      fill(255);
      textAlign(CENTER, BOTTOM);
      text(hand.isLeft() ? "left hand" : "right hand", palm.x, palm.y - 12);
    }
  }
  strokeWeight(1);
}

void keyPressed() {
  if (key == '1') view = 0;
  if (key == '2') view = 1;
  if (key == 'b' || key == 'B') view = 2;
  if (key == 's' || key == 'S') showSkeleton = !showSkeleton;
  if (key == 'm' || key == 'M') mirror = !mirror;
  if (key == 'v' || key == 'V') flipV = !flipV;
  if (key == 'p' || key == 'P') {
    projectionMode = (projectionMode + 1) % 6;
    com.leapmotion.leap.Image.projectionMode = projectionMode;
    println("projection mode " + projectionMode + ": " + com.leapmotion.leap.Image.projectionModeName(projectionMode));
  }
  if (key == 'd' || key == 'D') printDebug();
}

void printDebug() {
  com.leapmotion.leap.Controller c = leap.getController();
  println("---- LM_5 debug ----");
  println("service " + c.serviceVersion() + "  client " + c.clientVersion());
  for (int cam = 0; cam < 2; cam++) {
    float[] m = c.extrinsicCameraMatrix(cam);
    if (m == null) { println("camera " + cam + ": no extrinsic matrix"); continue; }
    println("camera " + cam + " extrinsic (column major):");
    for (int r = 0; r < 4; r++) println("   " + nf(m[r], 0, 4) + "  " + nf(m[4 + r], 0, 4) + "  " + nf(m[8 + r], 0, 4) + "  " + nf(m[12 + r], 0, 4));
  }
  ArrayList<Image> images = leap.getImages();
  for (Image img : images) {
    println("image " + img.getId() + ": " + img.width + "x" + img.height);
    com.leapmotion.leap.Image raw = img.getRaw();
    com.leapmotion.leap.Vector centre = raw.rectify(new com.leapmotion.leap.Vector(img.width / 2, img.height / 2, 0));
    println("   ray at image centre: " + centre);
    com.leapmotion.leap.Vector p0 = raw.warp(new com.leapmotion.leap.Vector(0, 0, 0));
    println("   pixel of ray (0,0,1): " + p0 + "   calibration scale: " + raw.calibrationScale()[0] + ", " + raw.calibrationScale()[1]);
    for (int mode = 0; mode < 6; mode++) {
      com.leapmotion.leap.Vector t = new com.leapmotion.leap.Vector(0, 200, 0);
      println("   mode " + mode + " point (0,200,0) -> " + raw.project(t, mode) + "   point (50,200,0) -> "
        + raw.project(new com.leapmotion.leap.Vector(50, 200, 0), mode) + "   point (0,200,50) -> "
        + raw.project(new com.leapmotion.leap.Vector(0, 200, 50), mode));
    }
  }
  for (Hand hand : leap.getHands()) {
    PVector palm = hand.getRawPosition();
    println("hand " + (hand.isLeft() ? "left" : "right") + " palm raw " + palm);
    for (Image img : images) println("   palm on image " + img.getId() + ": " + img.project(palm));
  }
}
