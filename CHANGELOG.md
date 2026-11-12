# Changelog

## 4.0.1 (2026-11-12)

- Request the desktop tracking mode when connecting (a service left in HMD or screen-top mode
  labels a right hand as left); `allowHdm()` now switches the service to HMD mode.

## 4.0.0 (2026-11-12)

Fork of "Leap Motion for Processing" 2.3.1.6 by Darius Morawiec, by Tiago Martins.

- Works on macOS 11+ (Apple Silicon and Intel) with Processing 4, through the Ultraleap
  Hyperion 6.2 tracking service (the old Leap Motion V2 software was Intel only).
- The `com.leapmotion.leap` Java API was re-implemented over LeapC with a native JNI bridge
  (macOS universal binary; Windows and Linux binaries built by CI).
- Swipe, circle, key tap and screen tap gestures recognised in Java (the Ultraleap service no
  longer provides them).
- Camera image projection (`Image.project`) using the camera extrinsic matrix.
- New examples: LM_0_Test, LM_4_Stack_Cubes, LM_5_Camera_Overlay, LM_6_Draw; LM_2_Gestures
  shows feedback cards.
- Double-click installers for macOS and Windows, driver scripts, diagnostics, tests with a mock
  LeapC and a GitHub Actions workflow.
