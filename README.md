# Leap Motion for Processing — fork for macOS Apple Silicon (M1/M2/M3/M4)

**Fork** of the [Leap Motion for Processing](https://github.com/nok/leap-motion-processing) library
v2.3.1.6 by Darius Morawiec (the original is included, unchanged, in
[`leap-motion-processing-master/`](leap-motion-processing-master/)).

The original library only worked on Intel Macs: it depended on the **Leap Motion V2** software
(the `leapd` daemon, x86_64 only) and on native binaries `libLeap.dylib` / `libLeapJava.dylib`
built for i386/x86_64. This fork keeps the **same API** (`import de.voidplus.leapmotion.*;`)
and the same examples, but:

* replaces the V2 SDK with a Java re-implementation of the `com.leapmotion.leap` package
  (Controller, Frame, Hand, Finger, Bone, Arm, Gesture, Image, Device, ...);
* connects to the current Ultraleap tracking service (**Hyperion 6.x / Gemini 5.x**, LeapC API)
  through a small native JNI bridge (`libLeapJava.dylib`, universal arm64 + x86_64 binary);
* recognises the four gestures (swipe, circle, key tap, screen tap) in Java, because the
  Ultraleap service no longer provides gestures;
* works with **Processing 4.x** (native folders `macos-aarch64` / `macos-x86_64`).

Fork author: **Tiago Martins**. MIT licence, as the original.

---

## Contents

1. [How it works](#1-how-it-works)
2. [Requirements](#2-requirements)
3. [Installing the driver (Ultraleap service)](#3-installing-the-driver-ultraleap-service)
4. [Installing the library in Processing](#4-installing-the-library-in-processing)
5. [Usage](#5-usage)
6. [Differences from the original library](#6-differences-from-the-original-library)
7. [Building from source](#7-building-from-source)
8. [Troubleshooting](#8-troubleshooting)
9. [Repository layout](#9-repository-layout)
10. [Licence](#10-licence)

---

## 1. How it works

```
 Processing 4 sketch
   import de.voidplus.leapmotion.*;        (API of the original library, unchanged)
          │
   de.voidplus.leapmotion.*                LeapMotionForProcessing.jar  (fork)
          │
   com.leapmotion.leap.*                   LeapJava.jar  (Java re-implementation of the V2 API)
          │  JNI
   libLeapJava.dylib                       native bridge (arm64 + x86_64), native/leapjni.c
          │  dlopen
   libLeapC.6.dylib                        Ultraleap LeapC SDK (installed with the driver)
          │  WebSocket 127.0.0.1
   libtrack_server                         Ultraleap Hyperion 6.2 tracking service
          │  USB
   Leap Motion Controller (1st generation, VID 0xF182) or Leap Motion Controller 2
```

The "driver" is the official **Ultraleap Hand Tracking 6.2.0** software for Apple Silicon.
It does the computer vision work (IR cameras → hand skeleton). What this project adds is
everything above it: the native bridge, the Java API and the Processing library. The Ultraleap
installer is not redistributed in this repository; `driver/install_driver.sh` downloads it
from the official site and verifies the signature (Developer ID *ULTRALEAP LIMITED*) and the
Apple notarization before installing.

## 2. Requirements

| Component | Version |
|---|---|
| macOS | 11 (Big Sur) or newer, Apple Silicon or Intel |
| Processing | 4.x (tested with 4.4.10) |
| Device | Leap Motion Controller (original) or Leap Motion Controller 2 |
| Driver | Ultraleap Hand Tracking **6.2.0** (Hyperion) for macOS |
| To build (optional) | Xcode or Command Line Tools, JDK 17+ |

## 3. Installing the driver (Ultraleap service)

Plug the Leap Motion into a USB port and run, from this project folder (or from the
`driver/` folder inside the installed library):

### Option A — official installation (recommended, asks for the administrator password)

```bash
./driver/install_driver.sh
```

The script downloads `tracking-software-apple-silicon-6.2.0.pkg` (≈ 480 MB) to `~/Downloads`,
verifies the signature and opens the macOS installer. It installs
`/Applications/Ultraleap Hand Tracking.app` and a *LaunchDaemon* that starts the
`libtrack_server` service automatically at boot.

### Option B — without an administrator password

```bash
./driver/install_driver.sh --user
```

Extracts the application into `~/Applications/Ultraleap Hand Tracking.app` and installs a
user *LaunchAgent* (`~/Library/LaunchAgents/com.ultraleap.tracking.service.plist`) that starts
the service at login. Everything stays inside the user's home folder.

### Checking

```bash
./driver/leap_service.sh status      # app installed? service running? device on USB?
./driver/bin/leapcheck 5             # connects to the service through LeapC and prints frames for 5 s
./driver/leap_service.sh panel       # opens the Ultraleap Control Panel (camera view)
```

Other commands: `leap_service.sh start | stop | restart | log` and `driver/uninstall_driver.sh`.

## 4. Installing the library in Processing

### From the pre-built `dist/` folder

```bash
./install_library.sh
```

Copies `dist/LeapMotionForProcessing` to `~/Documents/Processing/libraries/` (or to the
sketchbook set in the Processing preferences). If the old Intel folders `LeapJava` and
`LeapMotionForProcessing` exist, they are moved to `libraries/old/`, because they would create
duplicate `com.leapmotion.leap.*` classes and contain incompatible binaries.

### Manually

Copy the `dist/LeapMotionForProcessing` folder (or unzip `dist/LeapMotionForProcessing.zip`)
into the `libraries` folder of your sketchbook and restart Processing.

Installed layout (Processing 4 library format):

```
LeapMotionForProcessing/
├── library.properties
├── library/
│   ├── LeapMotionForProcessing.jar      de.voidplus.leapmotion (fork)
│   ├── LeapJava.jar                     com.leapmotion.leap (LeapC re-implementation)
│   ├── macos-aarch64/libLeapJava.dylib  JNI bridge (universal)
│   └── macos-x86_64/libLeapJava.dylib
├── examples/   LM_0_Test, LM_1_Basics, LM_2_Gestures, LM_3_Camera_Images
├── driver/     installation scripts for the Ultraleap service
├── src/        source code
└── reference/
```

## 5. Usage

The API is the one of the original library; see the reference in
[`leap-motion-processing-master/README.md`](leap-motion-processing-master/README.md).
Start with the **LM_0_Test** example (`File > Examples > Contributed Libraries > Leap Motion for Processing`):
it shows whether the service is connected, the detected device, the hands and the frame rate.

```java
import de.voidplus.leapmotion.*;

LeapMotion leap;

void setup() {
  size(800, 500);
  leap = new LeapMotion(this);          // new LeapMotion(this, true) prints diagnostics
}

void draw() {
  background(255);
  for (Hand hand : leap.getHands()) {
    hand.draw();
    PVector pos = hand.getPosition();   // mapped to the screen (see setWorld / moveWorld)
    PVector raw = hand.getRawPosition();// millimetres, origin at the device
    float grab = hand.getGrabStrength();
    for (Finger f : hand.getOutstretchedFingers()) {
      PVector tip = f.getPosition();
    }
  }
}

// optional callbacks
void leapOnConnect()    { println("connected"); }
void leapOnDisconnect() { println("disconnected"); }
void leapOnFrame()      { }
```

Gestures (see LM_2_Gestures):

```java
leap = new LeapMotion(this).withGestures("swipe, circle, screen_tap, key_tap");

void leapOnSwipeGesture(SwipeGesture g, int state) { /* 1 start, 2 update, 3 stop */ }
void leapOnCircleGesture(CircleGesture g, int state) { }
void leapOnKeyTapGesture(KeyTapGesture g) { }
void leapOnScreenTapGesture(ScreenTapGesture g) { }
```

Camera images (see LM_3_Camera_Images): `leap.allowImages()` and then
`for (Image img : leap.getImages()) image(img, 0, 0);`.

Direct access to the Java API (without Processing) — `LeapJava.jar` + `libLeapJava.dylib`:

```java
Controller c = new Controller(new Listener() {
  public void onFrame(Controller c) { Frame f = c.frame(); /* f.hands() ... */ }
});
```

## 6. Differences from the original library

| Topic | Original (V2 SDK) | This fork (Ultraleap LeapC) |
|---|---|---|
| Tracking service | Leap Motion V2 2.3.1 (`leapd`, Intel) | Ultraleap Hyperion 6.2 / Gemini 5 (native arm64) |
| Native binaries | `libLeap.dylib` + `libLeapJava.dylib` (i386/x86_64) | universal `libLeapJava.dylib`; `libLeapC` ships with the driver |
| Gestures | recognised by the service | recognised in Java with the same default thresholds; tunable with `setGesture*()` |
| Tools (pens) | supported | not available in Ultraleap: `getTools()` returns an empty list |
| `getSpherePosition/Radius` | computed by the service | approximated from the grab strength (open hand 100 mm, fist 30 mm) |
| `getTouchZone/Distance` | adaptive touch plane | approximation: plane at z = 0, 100 mm hover zone |
| Finger tip velocity, stabilised tip position | provided by the service | computed in Java from the previous frame |
| `setConfig()` | service configuration | `Gesture.*` keys configure the recogniser; other keys are sent to the service (best effort) |
| Images | 8-bit IR | 8-bit IR (`allow_images` is enabled by default in the Hyperion configuration) |
| Device FOV / range | reported by the service | Hyperion reports zero for the original controller; V2 values are used as fallback |
| Processing | 3.x | 4.x (`macos-aarch64` / `macos-x86_64` folders) |

Everything else (hands, fingers, bones, arm, callbacks, `setWorld`, `moveWorld`, `draw()`,
devices) keeps the behaviour documented in the original library.

## 7. Building from source

```bash
./build.sh            # compiles Java (JDK 17+), JNI bridge (clang), generates dist/
./install_library.sh  # installs into the sketchbook
./driver/build_leapcheck.sh
```

Tests (no hardware and no Ultraleap service needed):

```bash
./scripts/run_tests.sh        # ApiTest: synthetic frames, gestures, lists, vectors (73 checks)
                              # BridgeTest: libLeapJava.dylib + mock libLeapC (50 checks)
./scripts/run_sketch_test.sh  # real Processing sketch, via the CLI, with the installed library and the mock
```

The mock (`native/mock/mock_leapc.c`) is a fake `libLeapC` that emits connection, device,
policy, 100 fps frames with one hand whose index finger draws a circle, and IR images. It also
reproduces the Hyperion behaviour of stopping the tracker when a client clears tracking-mode
policy flags it never set.

End-to-end diagnostic (JNI bridge + LeapC + service + device):

```bash
./scripts/leap_diag.sh 10
```

Compiling an example with the Processing CLI:

```bash
/Applications/Processing.app/Contents/MacOS/Processing cli \
    --sketch=$HOME/Documents/Processing/libraries/LeapMotionForProcessing/examples/LM_1_Basics \
    --output=/tmp/lm1 --force --build
```

## 8. Troubleshooting

* **"Ultraleap Hand Tracking is not installed"** in the console — the driver is missing: `./driver/install_driver.sh`.
* **"the Ultraleap tracking service is not running"** — `./driver/leap_service.sh start`
  (or open `Ultraleap Hand Tracking` in /Applications). Check with `leap_service.sh status`.
* **Service running but `isConnected()` false / no hands** — confirm the device on USB
  (`leap_service.sh status` shows "Leap Motion Controller detected"); try another USB port
  or cable; open the Control Panel to see the cameras.
* **`isConnected()` true, device detected, but 0 fps and no hands** — the service's tracker
  stopped (`/var/log/ultraleap/tracker_log.txt` shows `tracking::framerate : 0`). Restart the service:
  `sudo launchctl kickstart -k system/com.ultraleap.tracking.service` (or `./driver/leap_service.sh restart`),
  or unplug and replug the Leap Motion. The first 3.0.0 build caused this by clearing policy flags it
  had never set; it is fixed (the bridge only sends deltas).
* **Duplicate library / conflicting `com.leapmotion.leap` classes** — remove the old `LeapJava`
  folder from `libraries` (`install_library.sh` does this automatically).
* **Processing on Intel (x86_64)** — the dylib is universal; install the
  `tracking-software-apple-intel-6.2.0.pkg` package (the script picks it by architecture).
* More messages: `new LeapMotion(this, true)` or `-Dleap.verbose=true`.

## 9. Repository layout

```
LeepMotionDriver/
├── README.md, LICENSE.txt, library.properties
├── build.sh                      builds everything and generates dist/
├── install_library.sh            installs into ~/Documents/Processing/libraries
├── src/com/leapmotion/leap/      Java re-implementation of the V2 API over LeapC (+ LeapJNI, GestureRecognizer)
├── src/de/voidplus/leapmotion/   Processing library (fork; only version strings changed)
├── native/leapjni.c, LeapC.h     JNI bridge; build_native.sh (arm64 + x86_64); mock/ (fake libLeapC for tests)
├── driver/                       install_driver.sh, leap_service.sh, uninstall_driver.sh,
│                                 leapcheck.c (LeapC diagnostic), LaunchAgent plist
├── examples/                     LM_0_Test (new) + the three original examples
├── test/java/, test/sketch/      ApiTest and BridgeTest (no hardware), LeapDiag, test sketch
├── scripts/                      run_tests.sh, run_sketch_test.sh, leap_diag.sh
└── leap-motion-processing-master/  original library (reference, unchanged)
```

## 10. Licence

MIT — see [LICENSE.txt](LICENSE.txt). Copyright 2013-2016 Darius Morawiec (original library);
copyright 2026 Tiago Martins (fork: LeapC re-implementation, JNI bridge, driver scripts).

The Ultraleap Hand Tracking software and the LeapC SDK are property of Ultraleap Ltd. and are
subject to the Ultraleap licence; they are not redistributed here (the installation script
downloads them from the official site). The `native/LeapC.h` header is included only to build
the bridge, under the terms of the Ultraleap Tracking SDK Agreement.
