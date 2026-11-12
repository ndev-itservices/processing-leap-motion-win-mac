/**
 * LM_3_Camera_Images - the raw infrared pictures from the two cameras.
 *
 * Shows what the Leap Motion actually sees: two black-and-white infrared images,
 * one per camera. Hands look bright because the device lights them with
 * infrared LEDs. See LM_5_Camera_Overlay for the tracked skeleton drawn on top.
 *
 * Leap Motion for Processing (Apple Silicon fork) - original by Darius Morawiec
 */
import de.voidplus.leapmotion.*;

LeapMotion leap;

void setup(){
  size(640, 480);
  background(255);
  leap = new LeapMotion(this);
}

void draw(){
  background(255);
  // ...

  if (leap.hasImages()) {
    for (Image camera : leap.getImages()) {
      if (camera.isLeft()) {
        // Left camera
        image(camera, 0, 0);
      } else {
        // Right camera
        image(camera, 0, camera.getHeight());
      }
    }
  }

}
