# SmartSpectra C++ Samples

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Building Samples](#building-samples)
- [Running Samples](#running-samples)
- [macOS Signing](#macos-signing)
- [Command Line Interface](#command-line-interface)
- [Keyboard Shortcuts](#keyboard-shortcuts)

## Overview

This repository contains sample applications for the SmartSpectra C++ SDK. The
SmartSpectra SDK is installed separately; this samples repository is the place to
clone, build, and run example applications against an installed SDK.

Install the SDK for your platform from the C++ SDK documentation:

<https://docs.physiology.presagetech.com/docs/cpp>

Portable CLI samples built by the aggregate `CMakeLists.txt`:

- [Dense Facemesh Example](dense_facemesh_example): Overlays the dense face landmark mesh on the live camera feed. Useful for verifying landmark stability and debugging face tracking. Executable name: `dense_facemesh`.
- [Full Example](full_example): Continuously reads from a video stream (camera or file), generates vitals output at fixed intervals, and overlays a HUD with live plots on the video feed. Executable name: `full_example`.
- [GStreamer Example](gstreamer_example): Pulls frames from a GStreamer pipeline (via OpenCV's GStreamer backend) and pushes them into the SDK through `UseCustomInput()`. Starting point for non-V4L2 capture stacks. Executable name: `gstreamer_example`.
- [Insights Example](insights_example): Streams the higher-level "insights" metric output from a camera or video file. Executable name: `insights_example`.
- [Minimal Example](minimal_example): The smallest possible runnable SmartSpectra C++ application - minimum code to demonstrate the SDK lifecycle. Executable name: `minimal_example`.
- [SmartSpectra Example](smart_spectra_example): Streams metrics and validation status to the terminal while displaying the camera (or input video) feed. Demonstrates the standard SmartSpectra setup pipeline. Executable name: `smart_spectra_example`.

App-style samples built by platform-specific tooling:

- [macOS SwiftUI Example](macos_swiftui_example): Native macOS SwiftUI app that links against the installed SmartSpectra C++ SDK. Built as a standalone macOS/Xcode project.
- [WinUI3 Example](winui3_example): Native Windows WinUI3 app that links against the installed SmartSpectra C++ SDK. Built as a standalone Visual Studio project.

## Prerequisites

1. Install the SmartSpectra C++ SDK for your platform:
   <https://docs.physiology.presagetech.com/docs/cpp>
2. Register and obtain a Presage Technologies Physiology API key from
   <https://physiology.presagetech.com/>.
3. Install the build tools required by your platform. The SDK installation guide
   lists the CMake, compiler, and package-manager setup for Linux, macOS, and
   Windows.

## Building Samples

Configure this samples repository as a standalone CMake project. CMake finds the
installed SmartSpectra SDK through the exported `SmartSpectra::SDK` package.

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --target full_example
```

To build all portable CLI samples:

```bash
cmake --build build
```

If CMake cannot find the SDK, pass the SDK install prefix through
`CMAKE_PREFIX_PATH`.

macOS Homebrew example:

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH=/opt/homebrew
```

Windows zip example:

```powershell
cmake -S . -B build -G "Visual Studio 17 2022" -A x64 `
  -DCMAKE_PREFIX_PATH="C:\path\to\smartspectra-sdk"
cmake --build build --config Release --target full_example
```

## Running Samples

Run the full example, substituting `<YOUR_API_KEY_HERE>` with your Physiology API
key:

```bash
./build/full_example/full_example --also_log_to_stderr \
  --camera_device_index=0 --api_key=<YOUR_API_KEY_HERE>
```

On Windows Visual Studio builds, run the selected configuration output:

```powershell
.\build\full_example\Release\full_example.exe --also_log_to_stderr `
  --camera_device_index=0 --api_key=<YOUR_API_KEY_HERE>
```

## macOS Signing

### Why is signing required?

The SmartSpectra SDK stores its device key in the macOS Keychain. macOS only
grants Keychain access to processes whose code signature carries a matching
`keychain-access-groups` entitlement issued under your Apple Developer Team ID.
An unsigned or ad-hoc-signed executable can run, but its first call into the SDK
that touches the device key will fail with `errSecMissingEntitlement (-34018)` or
a similar keychain error. To run a sample end-to-end you therefore need a signed
app-style wrapper with a provisioning profile and a matching entitlements file.

### Ad-hoc signing for first run (`codesign -s -`)

For a quick smoke test that does not need keychain-backed device-key access,
ad-hoc sign the standalone binary:

```bash
codesign --force --sign - ./build/full_example/full_example
```

This satisfies macOS launch checks but does not grant SDK keychain access. Use
the formal procedure below for full functionality.

### Apple-Development signing (full functionality)

On macOS, the sample must run as signed code with a valid macOS provisioning
profile so the SDK can access its secure keychain storage. Signing the standalone
executable alone is not sufficient for local development; use an app-style
wrapper with matching entitlements and provisioning profile.

Use an app-style wrapper for the sample executable:

1. Build the sample you want to run, for example `full_example`.
2. In the Apple Developer portal, create a macOS App ID for the sample bundle
   identifier you want to use, enable the keychain capability for it, and
   download a matching development provisioning profile.
3. Create a wrapper bundle structure next to the built executable:

   ```bash
   mkdir -p FullExample.app/Contents/MacOS
   ```

4. Copy the sample executable into the bundle:

   ```bash
   cp ./build/full_example/full_example \
     FullExample.app/Contents/MacOS/full_example
   ```

5. Add `FullExample.app/Contents/Info.plist` with at least:

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
     "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
   <plist version="1.0">
   <dict>
     <key>CFBundleIdentifier</key>
     <string>com.example.smartspectra.full-example</string>
     <key>CFBundleName</key>
     <string>FullExample</string>
     <key>CFBundleExecutable</key>
     <string>full_example</string>
     <key>CFBundlePackageType</key>
     <string>APPL</string>
     <key>NSCameraUsageDescription</key>
     <string>Camera access is required to run live SmartSpectra measurements.</string>
   </dict>
   </plist>
   ```

   If you only use `--input_video_path`, the camera usage description is not
   needed, but keeping it in the bundle is harmless.

6. Add `full_example.entitlements` next to the bundle and replace `TEAMID1234`
   and the bundle identifier with your own values:

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
     "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
   <plist version="1.0">
   <dict>
     <key>com.apple.application-identifier</key>
     <string>TEAMID1234.com.example.smartspectra.full-example</string>
     <key>com.apple.developer.team-identifier</key>
     <string>TEAMID1234</string>
     <key>keychain-access-groups</key>
     <array>
       <string>TEAMID1234.com.example.smartspectra.full-example</string>
     </array>
   </dict>
   </plist>
   ```

7. Copy the downloaded provisioning profile into the bundle as
   `embedded.provisionprofile`.
8. Sign the wrapper with your Apple Development identity:

   ```bash
   codesign --force --sign "Apple Development: Your Name (TEAMID1234)" \
     --entitlements full_example.entitlements \
     FullExample.app
   ```

9. Verify the signature and embedded entitlements:

   ```bash
   codesign --display --entitlements :- FullExample.app
   ```

10. Run the sample through the bundle executable:

    ```bash
    ./FullExample.app/Contents/MacOS/full_example --also_log_to_stderr \
      --camera_device_index=0 --api_key=<YOUR_API_KEY_HERE>
    ```

The same pattern applies to other C++ samples. Use a unique bundle identifier
per sample wrapper, and keep the bundle identifier, entitlement values, and
provisioning profile aligned. If any of those values drift, macOS typically
reports keychain entitlement errors such as `errSecMissingEntitlement (-34018)`.

## Command Line Interface

All six portable CLI samples (`dense_facemesh`, `full_example`,
`gstreamer_example`, `insights_example`, `minimal_example`,
`smart_spectra_example`) parse flags via Abseil and support the standard Abseil
help surface. To list the flags declared by a sample, pass `--help=main`:

```bash
./build/full_example/full_example --help=main
```

To read about a specific command line option, pass `--help=<OPTION_NAME>`:

```bash
./build/full_example/full_example --help=verbosity
```

The app-style samples (`macos_swiftui_example`, `winui3_example`) do not use
Abseil flags and have their own platform-native settings UIs; the `--help=main`
flag does not apply to them.

## Keyboard Shortcuts

During the run of any example with a preview window, use the following keyboard
shortcut:

- `q` or `ESC`: exit
