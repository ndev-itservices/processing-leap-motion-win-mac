/**
 * LM_6_Draw - a drawing board controlled with both hands.
 *
 *   Right hand   the index finger is the pen: point at the canvas and push the finger
 *                forward (away from you) to draw; pull it back to stop.
 *   Left hand    the index finger is the pointer for the panel on the left: hold it over
 *                a colour, a shape, a size or an action for half a second (or push it
 *                forward) to select it.
 *
 * Keys:  c = clear   s = save PNG
 *
 * Leap Motion for Processing (Apple Silicon fork) - Tiago Martins
 */
import de.voidplus.leapmotion.*;

LeapMotion leap;
PGraphics canvas;

final int PANEL_W = 170;
final int FOOTER_H = 70;
final float DWELL = 0.5;       // seconds the left finger must stay on a button to select it

// tools
color[] palette = {
  color(255, 255, 255), color(30, 30, 30), color(230, 60, 60), color(255, 150, 40),
  color(250, 220, 60), color(70, 200, 110), color(60, 150, 240), color(170, 90, 230)
};
String[] shapes = { "brush", "circle", "square", "eraser" };
float[] sizes = { 4, 10, 20, 36 };
int colorIndex = 2, shapeIndex = 0, sizeIndex = 1;

ArrayList<Button> buttons = new ArrayList<Button>();
Button hovered = null;
float hoverSince = 0;
PVector lastPen = null;   // previous pen position while drawing
boolean drawing = false;

void setup() {
  size(1100, 700);
  textFont(createFont("Helvetica", 14));
  leap = new LeapMotion(this);
  leap.setWorld(260, 380, 200);   // mm mapped to the window: width, height, depth
  canvas = createGraphics(width - PANEL_W, height - FOOTER_H);
  clearCanvas();
  buildPanel();
}

void clearCanvas() {
  canvas.beginDraw();
  canvas.background(245);
  canvas.endDraw();
}

void buildPanel() {
  buttons.clear();
  int x = 14, y = 44, s = 32, gap = 6;
  for (int i = 0; i < palette.length; i++) {
    buttons.add(new Button("color", i, x + (i % 4) * (s + gap), y + (i / 4) * (s + gap), s, s));
  }
  y += 2 * (s + gap) + 26;
  for (int i = 0; i < shapes.length; i++) buttons.add(new Button("shape", i, x, y + i * 38, PANEL_W - 28, 32));
  y += shapes.length * 38 + 26;
  for (int i = 0; i < sizes.length; i++) buttons.add(new Button("size", i, x + i * 36, y, 32, 32));
  y += 32 + 26;
  buttons.add(new Button("clear", 0, x, y, PANEL_W - 28, 32));
  buttons.add(new Button("save", 0, x, y + 38, PANEL_W - 28, 32));
}

void draw() {
  background(32, 34, 40);
  image(canvas, PANEL_W, 0);

  Hand right = null, left = null;
  for (Hand hand : leap.getHands()) {
    if (hand.isRight() && right == null) right = hand;
    if (hand.isLeft() && left == null) left = hand;
  }

  updateLeft(left);
  updateRight(right);

  drawPanel();
  drawFooter(right != null, left != null);
}

// ------------------------------------------------------------------ right hand: pen

void updateRight(Hand hand) {
  if (hand == null) { drawing = false; lastPen = null; return; }
  Finger index = hand.getIndexFinger();
  PVector p = index.getPosition();                       // mapped to the window
  float push = -index.getTouchDistance();                // > 0 when the finger is pushed forward
  boolean onCanvas = p.x >= PANEL_W && p.y < height - FOOTER_H;
  boolean wantsDraw = index.isExtended() && push > 0 && onCanvas;

  if (wantsDraw) {
    float cx = p.x - PANEL_W, cy = p.y;
    canvas.beginDraw();
    paint(cx, cy, drawing && lastPen != null ? lastPen : null);
    canvas.endDraw();
    lastPen = new PVector(cx, cy);
    drawing = true;
  } else {
    drawing = false;
    lastPen = null;
  }

  // cursor: a ring whose fill shows how close the finger is to the "paper"
  noFill();
  stroke(drawing ? color(60, 200, 110) : color(255, 160));
  strokeWeight(2);
  float r = max(8, sizes[sizeIndex]);
  ellipse(p.x, p.y, r, r);
  noStroke();
  fill(drawing ? color(60, 200, 110) : color(255, 120));
  float f = constrain(map(push, -1, 0, 0, 1), 0, 1);  // approaching the paper
  arc(p.x, p.y, r + 14, r + 14, -HALF_PI, -HALF_PI + TWO_PI * f);
  strokeWeight(1);
}

void paint(float x, float y, PVector from) {
  float s = sizes[sizeIndex];
  color c = palette[colorIndex];
  String shape = shapes[shapeIndex];
  if (shape.equals("eraser")) { c = color(245); s = s * 2; }
  canvas.noStroke();
  canvas.fill(c);
  if (shape.equals("square")) {
    canvas.rectMode(CENTER);
    canvas.rect(x, y, s * 2, s * 2);
  } else if (shape.equals("circle")) {
    canvas.ellipse(x, y, s * 2, s * 2);
  } else {
    // brush / eraser: a continuous stroke
    canvas.stroke(c);
    canvas.strokeWeight(s);
    canvas.strokeCap(ROUND);
    if (from != null) canvas.line(from.x, from.y, x, y); else canvas.point(x, y);
  }
}

// ------------------------------------------------------------------ left hand: panel pointer

void updateLeft(Hand hand) {
  if (hand == null) { hovered = null; return; }
  Finger index = hand.getIndexFinger();
  PVector p = index.getPosition();
  boolean push = -index.getTouchDistance() > 0.15;

  Button over = null;
  for (Button b : buttons) if (b.contains(p.x, p.y)) over = b;
  if (over != hovered) { hovered = over; hoverSince = millis() / 1000.0; }

  float progress = 0;
  if (hovered != null) {
    progress = constrain((millis() / 1000.0 - hoverSince) / DWELL, 0, 1);
    if (progress >= 1 || push) {
      hovered.select();
      hoverSince = millis() / 1000.0 + 1;   // avoid re-selecting immediately
    }
  }

  // pointer
  noFill();
  stroke(255, 200, 60);
  strokeWeight(2);
  ellipse(p.x, p.y, 16, 16);
  if (hovered != null && progress > 0) {
    noStroke();
    fill(255, 200, 60, 180);
    arc(p.x, p.y, 28, 28, -HALF_PI, -HALF_PI + TWO_PI * progress);
  }
  strokeWeight(1);
}

class Button {
  String kind; int index; float x, y, w, h;
  Button(String kind, int index, float x, float y, float w, float h) {
    this.kind = kind; this.index = index; this.x = x; this.y = y; this.w = w; this.h = h;
  }
  boolean contains(float px, float py) { return px >= x && px <= x + w && py >= y && py <= y + h; }
  boolean selected() {
    return (kind.equals("color") && index == colorIndex) || (kind.equals("shape") && index == shapeIndex)
      || (kind.equals("size") && index == sizeIndex);
  }
  void select() {
    if (kind.equals("color")) colorIndex = index;
    else if (kind.equals("shape")) shapeIndex = index;
    else if (kind.equals("size")) sizeIndex = index;
    else if (kind.equals("clear")) clearCanvas();
    else if (kind.equals("save")) saveCanvas();
  }
  void draw() {
    boolean hot = this == hovered;
    stroke(selected() ? color(255) : (hot ? color(255, 200, 60) : color(90)));
    strokeWeight(selected() ? 3 : 1.5);
    if (kind.equals("color")) {
      fill(palette[index]);
      rect(x, y, w, h, 6);
    } else if (kind.equals("size")) {
      fill(50, 52, 60);
      rect(x, y, w, h, 6);
      noStroke();
      fill(220);
      ellipse(x + w / 2, y + h / 2, min(sizes[index], 26), min(sizes[index], 26));
    } else {
      fill(hot ? color(70, 72, 82) : color(50, 52, 60));
      rect(x, y, w, h, 6);
      fill(230);
      textAlign(CENTER, CENTER);
      String label = kind.equals("shape") ? shapes[index] : kind;
      text(label, x + w / 2, y + h / 2 - 1);
    }
    strokeWeight(1);
  }
}

void saveCanvas() {
  String name = "drawing-" + year() + nf(month(), 2) + nf(day(), 2) + "-" + nf(hour(), 2) + nf(minute(), 2) + nf(second(), 2) + ".png";
  canvas.save(sketchPath(name));
  println("saved " + name);
}

// ------------------------------------------------------------------ panel and footer

void drawPanel() {
  noStroke();
  fill(40, 42, 50);
  rect(0, 0, PANEL_W, height - FOOTER_H);
  fill(200);
  textAlign(LEFT, TOP);
  text("Colour", 14, 20);
  text("Shape", 14, 44 + 2 * 38 + 4);
  text("Size", 14, 44 + 2 * 38 + 26 + shapes.length * 38 + 4);
  for (Button b : buttons) b.draw();
  // current tool preview
  float py = height - FOOTER_H - 60;
  fill(200);
  textAlign(LEFT, TOP);   // the buttons above use centred text
  text("Current", 14, py - 20);
  noStroke();
  fill(shapes[shapeIndex].equals("eraser") ? color(245) : palette[colorIndex]);
  float s = min(sizes[sizeIndex] * 2, 44);
  if (shapes[shapeIndex].equals("square")) { rectMode(CENTER); rect(44, py + 16, s, s); rectMode(CORNER); }
  else ellipse(44, py + 16, s, s);
}

void drawFooter(boolean rightPresent, boolean leftPresent) {
  noStroke();
  fill(20, 22, 26);
  rect(0, height - FOOTER_H, width, FOOTER_H);
  textAlign(LEFT, TOP);
  fill(rightPresent ? color(60, 200, 110) : color(200));
  text("RIGHT hand: point with the index finger at the canvas and push it forward (away from you) to draw; pull back to stop.", 16, height - FOOTER_H + 12);
  fill(leftPresent ? color(255, 200, 60) : color(200));
  text("LEFT hand: point with the index finger at the panel and hold it half a second over a colour, shape, size or action to select it.", 16, height - FOOTER_H + 32);
  fill(140);
  text("c = clear   s = save PNG" + (leap.isConnected() ? "" : "        Leap Motion not connected"), 16, height - FOOTER_H + 52);
}

void keyPressed() {
  if (key == 'c' || key == 'C') clearCanvas();
  if (key == 's' || key == 'S') saveCanvas();
}
