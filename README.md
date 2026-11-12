# Leap Motion for Processing

Use the **Leap Motion Controller** (the small hand-tracking camera) in **Processing 4** (not 3),
on **macOS 11 or newer (Apple Silicon and Intel)** and **Windows 10/11**.

A fork of [Leap Motion for Processing](https://github.com/nok/leap-motion-processing) by
Darius Morawiec, updated by **Tiago Martins** so that it works on today's computers: the
original only ran on old Intel Macs. The programming interface and the examples are the same,
so old sketches keep working.

> **Tested with the original Leap Motion Controller (2013) on macOS (Apple Silicon) and Windows 11.**
> The Leap Motion Controller 2 is supported by the driver and the library but has **not been tested**
> yet; if you have one, please tell us how it goes in an issue.

> Made for artists and students: you do not need to compile anything or use the Terminal.
> If something does not work, read [Problems and solutions](#problems-and-solutions).

---

## Start in 4 steps

### 1. Download

Go to the [Releases page](../../releases/latest) and, under **Assets**, download the file
**LeapMotionForProcessing-4.0.1.zip** (the number is the version; take the newest). Unzip it:
you get a folder called `LeapMotionForProcessing`.

### 2. Install the driver

The Leap Motion needs the official software from its maker, Ultraleap. Download the one for
your computer, open the downloaded file and follow the installer (it asks for your password once):

| Your computer | Download |
|---|---|
| Mac with Apple chip (M1, M2, M3, M4...) | [Ultraleap Hand Tracking 6.2.0 for Apple Silicon](https://s3.eu-west-1.amazonaws.com/downloads.ultraleap.com/software/tracking-software/6.2.0/tracking-software-apple-silicon-6.2.0.pkg) (.pkg, 480 MB) |
| Mac with Intel processor | [Ultraleap Hand Tracking 6.2.0 for Apple Intel](https://s3.eu-west-1.amazonaws.com/downloads.ultraleap.com/software/tracking-software/6.2.0/tracking-software-apple-intel-6.2.0.pkg) (.pkg, 480 MB) |
| Windows 10 / 11 (64-bit) | [Ultraleap Hand Tracking 6.2.0 for Windows](https://s3.eu-west-1.amazonaws.com/downloads.ultraleap.com/software/tracking-software/6.2.0/tracking-software-windows-6.2.0.exe) (.exe, 400 MB) |

Not sure which Mac you have? Apple menu > About This Mac: "Chip Apple M…" means Apple chip,
"Processor Intel…" means Intel. These links come from Ultraleap's official download page,
[ultraleap.com/downloads/leap-controller](https://www.ultraleap.com/downloads/leap-controller/).

After the installer finishes, plug the Leap Motion into a USB port. On a Mac, an app called
**Ultraleap Hand Tracking** appears in your Applications folder; on Windows an Ultraleap icon
appears next to the clock. You only do this once.

### 3. Install the library

Copy the folder **`LeapMotionForProcessing`** (the one you unzipped in step 1) into the
`libraries` folder of your Processing sketchbook:

| | Where the `libraries` folder is |
|---|---|
| Mac | `Documents` > `Processing` > `libraries` |
| Windows | `Documents` > `Processing` > `libraries` |

If the `libraries` folder does not exist yet, open Processing once (it creates the `Processing`
folder) and create `libraries` inside it. If you had an older version of this library, delete the
old `LeapMotionForProcessing` and `LeapJava` folders first. Restart Processing if it was open.

> Prefer to let a script do steps 2 and 3? Inside the unzipped folder, open `install` and run
> `Install Leap Motion for Processing (macOS).command` (right-click > Open) or
> `Install Leap Motion for Processing (Windows).bat`. It downloads the same driver and copies
> the library for you.

### 4. Try it

In Processing open **File > Examples > Contributed Libraries > Leap Motion for Processing >
LM_0_Test** and press **Run** (the triangle). Put a hand above the device:

* green dot = the Leap Motion works, you see your hand on screen;
* red dot = read the message on screen, it says what is missing.

---

## Examples

All examples are in Processing under **File > Examples > Contributed Libraries > Leap Motion for Processing**.

| Example | What it does | What you do |
|---|---|---|
| **LM_0_Test** | Checks that everything works: connection, device, frames per second, hands | Put a hand above the device |
| **LM_1_Basics** | Shows every value the Leap Motion gives: position, fingers, bones, arm, grab, pinch | Move your hands; look at the console |
| **LM_2_Gestures** | Four gestures with feedback cards that turn green | Swipe, draw a circle, tap forward, tap down with one finger |
| **LM_3_Camera_Images** | The raw infrared pictures from the two cameras | Just look |
| **LM_4_Stack_Cubes** | A 3D table with cubes you pick up and stack; cubes fall, push each other and tip over | Pinch a cube with thumb + index, move, open the fingers to drop or throw |
| **LM_5_Camera_Overlay** | The camera picture with the tracked skeleton drawn on top | Keys: 1 / 2 / b cameras, m mirror, v flip |
| **LM_6_Draw** | A drawing board | Right index finger draws (push forward), left index finger picks colour, shape, size |

The basics in code:

```java
import de.voidplus.leapmotion.*;

LeapMotion leap;

void setup() {
  size(800, 500);
  leap = new LeapMotion(this);
}

void draw() {
  background(255);
  for (Hand hand : leap.getHands()) {
    hand.draw();                              // draws the hand
    PVector pos = hand.getPosition();         // position on the screen
    float grab = hand.getGrabStrength();      // 0 = open hand, 1 = fist
    for (Finger finger : hand.getOutstretchedFingers()) {
      PVector tip = finger.getPosition();     // finger tip on the screen
    }
  }
}
```

More: `hand.isLeft()`, `hand.getPinchStrength()`, `hand.getIndexFinger()`,
`finger.getRawPosition()` (millimetres), `leap.getImages()` (camera pictures),
`new LeapMotion(this).allowGestures()` with `leapOnSwipeGesture(...)`,
`leapOnCircleGesture(...)`, `leapOnKeyTapGesture(...)`, `leapOnScreenTapGesture(...)`.
The full reference is in [original/README.md](original/README.md): same API, but ignore that page's
installation notes and Processing 3 version list, they describe the old Intel-only version.

---

## What you need

### Mac

| | |
|---|---|
| macOS | **11 (Big Sur) or newer**. Tested up to macOS 27.0.1 |
| Computer | Apple Silicon (M1, M2, M3, M4...) or Intel |
| Processing | **version 4** from [processing.org/download](https://processing.org/download). Processing 3 is not supported |
| Device | Leap Motion Controller (the original, 2013) or Leap Motion Controller 2 |
| Driver (Apple Silicon) | [Ultraleap Hand Tracking 6.2.0 for Apple Silicon (.pkg, 480 MB)](https://s3.eu-west-1.amazonaws.com/downloads.ultraleap.com/software/tracking-software/6.2.0/tracking-software-apple-silicon-6.2.0.pkg) |
| Driver (Intel Mac) | [Ultraleap Hand Tracking 6.2.0 for Apple Intel (.pkg)](https://s3.eu-west-1.amazonaws.com/downloads.ultraleap.com/software/tracking-software/6.2.0/tracking-software-apple-intel-6.2.0.pkg) |

Not sure which Mac you have? Apple menu > About This Mac: "Chip Apple M…" is Apple Silicon,
"Processor Intel…" is Intel. The installer in step 2 downloads the right driver for you; the
links are here in case you prefer to install it by hand (double-click the .pkg and follow it).

### Windows

| | |
|---|---|
| Windows | **10 or 11, 64-bit** |
| Computer | Intel or AMD processor (Windows on ARM is not supported by the Ultraleap driver) |
| Processing | **version 4** from [processing.org/download](https://processing.org/download). Processing 3 is not supported |
| Device | Leap Motion Controller (the original, 2013) or Leap Motion Controller 2 |
| Driver | [Ultraleap Hand Tracking 6.2.0 for Windows (.exe, 400 MB)](https://s3.eu-west-1.amazonaws.com/downloads.ultraleap.com/software/tracking-software/6.2.0/tracking-software-windows-6.2.0.exe) |

All drivers come from Ultraleap's official page:
[ultraleap.com/downloads/leap-controller](https://www.ultraleap.com/downloads/leap-controller/).

**Tested:** macOS 27.0.1 (Apple Silicon) and Windows 11, Processing 4.4.10, original Leap Motion
Controller (2013).
**Should work, not yet confirmed with a real device:** Leap Motion Controller 2 (the driver and the
library support it; the camera overlay example LM_5 may need the projection mode changed with the
`p` key); Intel Macs and Linux (Ubuntu 22.04), whose binaries are built and tested automatically
against a simulated device. Please report how it goes in an issue.

---

## Problems and solutions

**The dot is red and the screen says the service is not running**
The Leap Motion driver is installed but stopped. On macOS open *Ultraleap Hand Tracking* in your
Applications folder (or restart the Mac); on Windows start the *Ultraleap Tracking Service* from the
Ultraleap control panel.

**The dot is red and the screen says the driver is not installed**
Run the installer again, or install the driver by hand from
[ultraleap.com/downloads/leap-controller](https://www.ultraleap.com/downloads/leap-controller/)
(choose your system: Apple Silicon, Apple Intel or Windows).

**The dot is green but no hands appear**
Keep the hand 10 to 50 cm above the device, palm down, in a room without strong sunlight.
Check that the device's USB cable is firmly connected; try another port.

**Device detected, green dot, but 0 fps**
Unplug and plug the Leap Motion again, or restart the driver (macOS: open *Ultraleap Hand Tracking*;
Windows: Ultraleap control panel).

**Processing says the library is duplicated or in conflict**
An old version of the library is still in your sketchbook. Delete the folders `LeapJava` and
`LeapMotionForProcessing` from `Documents/Processing/libraries` and run the installer again.

**I want to know what is wrong**
macOS: double-click `install/Check Leap Motion (macOS).command`; it prints, in plain words, what
works and what to do.

---

## For developers

<details>
<summary>How it works, building, tests, contributing</summary>

### How it works

```
 Processing 4 sketch  (import de.voidplus.leapmotion.*)
   │
   de.voidplus.leapmotion.*      LeapMotionForProcessing.jar  (fork of the original library)
   │
   com.leapmotion.leap.*         LeapJava.jar  (Java re-implementation of the V2 API over LeapC)
   │  JNI
   libLeapJava / LeapJava.dll    native bridge, native/leapjni.c (macOS universal, Windows x64, Linux x64)
   │  dynamic loading
   libLeapC                      Ultraleap LeapC SDK, installed with the driver
   │  WebSocket 127.0.0.1
   Ultraleap tracking service    Hyperion 6.2 (libtrack_server / LeapSvc)
   │  USB
   Leap Motion Controller
```

The "driver" is the official Ultraleap Hand Tracking software, which does the computer vision.
It is not redistributed here: the installers download it from Ultraleap's site and, on macOS,
verify its Apple signature and notarization. This project adds everything above it.

Differences from the original library: tools (pens) do not exist in Ultraleap; the four gestures
are recognised in Java with the V2 default thresholds (tunable through `setGesture*()`);
`getSphereRadius()` and the touch zone are approximations; finger velocities and stabilised
positions are computed from the previous frame.

### Layout

```
build.sh, install_library.sh     build everything / install into the sketchbook
library.properties               Processing library metadata
src/com/leapmotion/leap/         Java API over LeapC (+ LeapJNI loader, GestureRecognizer)
src/de/voidplus/leapmotion/      Processing wrapper (fork; version strings only)
native/                          leapjni.c, LeapC.h, build_native.sh / .bat, mock/ (fake LeapC for tests)
driver/                          driver install/status/uninstall scripts (macOS), leapcheck.c
install/                         double-click installers and check for end users
examples/                        LM_0 ... LM_6
test/                            ApiTest, BridgeTest, LeapDiag, test sketch
scripts/                         run_tests.sh/.bat, run_sketch_test.sh, leap_diag.sh
.github/workflows/build.yml      CI: builds and tests on macOS, Windows and Linux; packages the zip
original/                        the original library 2.3.1.6, unchanged (reference)
```

### Building

Requirements: JDK 17+, a C compiler (Xcode Command Line Tools / MSVC / gcc). Processing itself is
optional: `build.sh` downloads `core.jar` from Maven Central when Processing is not installed.

```bash
./build.sh              # Java + native bridge for this platform + dist/LeapMotionForProcessing(.zip)
./install_library.sh    # copy into ~/Documents/Processing/libraries
./scripts/run_tests.sh  # ApiTest (73 checks, pure Java) + BridgeTest (59 checks, JNI bridge + mock LeapC)
./scripts/run_sketch_test.sh   # a real Processing sketch via the CLI against the mock
./scripts/leap_diag.sh 10      # end-to-end with the real service and device
```

Windows: `native\build_native.bat` and `native\mock\build_mock.bat` from a Developer Command
Prompt, then `scripts\run_tests.bat`. The release zip is produced by the GitHub Actions
workflow, which builds the native bridge on the three systems and runs the tests on each
(without hardware, against the mock).

### Releasing

Tag a version (`git tag v3.0.1 && git push --tags`): the workflow attaches
`LeapMotionForProcessing-<version>.zip` to a GitHub release.

### Contributing

Issues and pull requests are welcome. Everything in the repository (code, comments, examples,
docs) is in English. Run `scripts/run_tests.sh` before submitting.

</details>

---

## Licence

MIT, see [LICENSE.txt](LICENSE.txt). Copyright 2013-2016 Darius Morawiec (original library),
copyright 2026 Tiago Martins (this fork). The Ultraleap Hand Tracking software and the LeapC SDK
belong to Ultraleap Ltd. and have their own licence; they are downloaded from Ultraleap's website
and not redistributed here.
