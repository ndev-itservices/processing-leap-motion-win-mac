/**
 * LM_5_Camera_Overlay - shows the infrared camera image with the tracked hand
 * skeleton (bones and joints) drawn on top of it.
 *
 * Every 3D joint position is projected onto the camera image with
 * Image.project(), which uses the camera calibration of the device
 * (lens distortion included), so the drawing matches the real hand in the picture.
 *
 * Keys:  1 = left camera   2 = right camera   b = both   s = toggle skeleton
 *
 * Leap Motion for Processing (Apple Silicon fork) - Tiago Martins
 */
import de.voidplus.leapmotion.*;

LeapMotion leap;
int view = 0;            // 0 = left camera, 1 = right camera, 2 = both
boolean showSkeleton = true;

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
  text("1 = left   2 = right   b = both   s = skeleton on/off     hands: " + leap.countHands()
    + "   " + leap.getFrameRate() + " fps", 12, 10);
}

void drawCamera(Image img, float x, float y, float w, float h) {
  image(img, x, y, w, h);
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
        PVector a = img.project(bone.getRawPrevJoint(), x, y, w, h);
        PVector z = img.project(bone.getRawNextJoint(), x, y, w, h);
        if (a != null && z != null) line(a.x, a.y, z.x, z.y);
      }
    }
    // joints
    noStroke();
    fill(jointColor);
    for (Finger finger : hand.getFingers()) {
      for (int b = 0; b < 4; b++) {
        PVector j = img.project(finger.getBone(b).getRawNextJoint(), x, y, w, h);
        if (j != null) ellipse(j.x, j.y, 8, 8);
      }
      PVector mcp = img.project(finger.getBone(0).getRawPrevJoint(), x, y, w, h);
      if (mcp != null) ellipse(mcp.x, mcp.y, 8, 8);
    }
    // palm and wrist
    fill(palmColor);
    PVector palm = img.project(hand.getRawPosition(), x, y, w, h);
    if (palm != null) ellipse(palm.x, palm.y, 14, 14);
    PVector wrist = img.project(hand.getArm().getWristRawPosition(), x, y, w, h);
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
}
