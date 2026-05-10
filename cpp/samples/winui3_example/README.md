# WinUI 3 Example

A modern Windows-native consumer app that mirrors the iOS UIKit sample shipped under `smartspectra/swift/samples/uikit-sample`. Demonstrates how to integrate the SmartSpectra C++ SDK into a WinUI 3 / C++/WinRT desktop application.

## Overview

The sample shows how to:

- Drive `presage::smartspectra::SmartSpectra` from a WinUI 3 window
- Receive preview frames via `SetOnVideoOutput` and render them with a `WriteableBitmap`
- Marshal SDK callbacks (metrics, validation, status, insight, error) onto the UI thread with `DispatcherQueue::TryEnqueue`
- Render trace plots (breathing, arterial pressure) on a XAML `Canvas` using `Polyline` shapes
- Read facial expression scores out of `Metrics::face().expression()` and surface the dominant one as an emoji + label
- Trigger an on-demand insight (`RequestInsight`) and display the LLM analysis

## Key Features

- **Native WinUI 3 / Fluent UI** — full-window camera preview with a translucent gradient panel overlay, validation pill, accent button, and dark theme
- **C++/WinRT code-behind** — XAML markup with the SDK called directly from `MainWindow.xaml.cpp`, no marshaling layer
- **Self-contained Windows App SDK** — runtime is bundled into the build output, so no separate installer is required to run the sample
- **Live trace graphs** — breathing and arterial pressure rendered as glow + main-stroke polylines
- **Facial expressions** — Happy / Sad / Angry / Surprised / Fearful / Disgusted / Contempt / Neutral with confidence

## Prerequisites

1. **The SmartSpectra Windows binary distribution.** Download `SmartSpectra-<version>-windows-x64.zip` from <https://presagetechnologies.com/for-developers> and unzip it anywhere on disk. The unzipped folder is the *SDK root* and must contain `bin/`, `include/`, `lib/`, and `share/` subdirectories.
2. **Visual Studio 2022 (17.4+) or later**, with these workloads installed via Visual Studio Installer:
   - **Desktop development with C++**
   - **Windows application development** — this is the workload that ships the WinUI 3 C++ project templates and `Microsoft.Windows.UI.Xaml.Cpp.Targets`, which wires the XAML markup compiler into the native build chain. Without it the project will not build.
3. **A Presage Physiology API key.** Register at <https://physiology.presagetech.com/>.

## Pointing the project at your SDK

The build needs to know where you unzipped the SmartSpectra distribution. The value is read from the `SmartSpectraSdkDir` MSBuild property (it must end with a trailing backslash) and is resolved in this order:

1. `-p:SmartSpectraSdkDir=...` on the MSBuild command line — highest priority
2. `SMARTSPECTRA_SDK_DIR` environment variable
3. The default value in `Directory.Build.props` next to the solution: **`C:\SmartSpectra-<version>-windows-x64\`** (replace `<version>` with the release you downloaded, e.g. `3.1.0`)

If none of these resolve to a folder containing the SDK, the build fails before doing any work with a message naming the path it tried.

### Easiest setup

Unzip the distribution at the default location (substitute the actual version number you downloaded):

```text
C:\SmartSpectra-<version>-windows-x64\
├─ bin\
├─ include\
├─ lib\
└─ share\
```

…then open the solution and build. No project edits, no environment variables.

### If you unzipped it elsewhere

Pick whichever of these fits:

- **Edit `Directory.Build.props`** next to the solution and change the default path. Affects everyone using your checkout.
- **Set the `SMARTSPECTRA_SDK_DIR` environment variable** so Visual Studio picks it up automatically:

  ```bat
  setx SMARTSPECTRA_SDK_DIR "D:\sdks\SmartSpectra-<version>-windows-x64\"
  ```

  Restart Visual Studio after running `setx` so it inherits the new environment.
- **Override on the MSBuild command line** for a single build:

  ```bat
  msbuild SmartSpectraWinUI.sln -t:Restore -p:Configuration=Release -p:Platform=x64 ^
    -p:SmartSpectraSdkDir="D:\sdks\SmartSpectra-<version>-windows-x64\"
  ```

## Building

### Visual Studio

1. Open `SmartSpectraWinUI.sln`.
2. Select the `Release | x64` configuration.
3. **Build → Build Solution** (or press **F5** to build + debug).

The first build will restore the NuGet packages (`Microsoft.WindowsAppSDK`, `Microsoft.Windows.CppWinRT`, `Microsoft.Windows.SDK.BuildTools`).

### Command line (MSBuild)

From the sample directory, with the SDK path supplied via any of the options above:

```bat
msbuild SmartSpectraWinUI.sln -t:Restore -p:Configuration=Release -p:Platform=x64
msbuild SmartSpectraWinUI.sln               -p:Configuration=Release -p:Platform=x64
```

The post-build step copies `smartspectra.dll`, `opencv_world4100.dll`, and a generated `physiology_edge_manifest.txt` (with the absolute path to `<sdk>/share/smartspectra` baked in) next to the executable, so you can launch the app directly without setting up a runtime search path.

## Configuration

Before running, set the `SMARTSPECTRA_API_KEY` environment variable to your Presage API key:

```bat
setx SMARTSPECTRA_API_KEY "YOUR_API_KEY_HERE"
```

Restart Visual Studio (or the command prompt) after `setx` so it inherits the new value.
The sample reads the key from `std::getenv("SMARTSPECTRA_API_KEY")` at startup — do **not**
hard-code a real key in `MainWindow.xaml.cpp` or commit it to source control.

Other knobs you may want to change:

- **Camera selection / resolution** — edit the `m_spectra->UseCamera()` call in `MainWindow::MainWindow()` (e.g., `UseCamera(1).SetResolution(1920, 1080).SetFps(60)`).
- **Requested metrics** — adjust the `cfg.AddMetrics(...)` calls. The default enables breathing, cardio, and face metric groups.
- **Windows SDK version** — the project pins `WindowsTargetPlatformVersion=10.0.26100.0`. If your machine has a different Windows 10 SDK installed, change it in the `<PropertyGroup Label="Globals">` of `SmartSpectraWinUI.vcxproj`.

## Running

Launch the produced executable directly from the build output:

```bat
bin\x64\Release\SmartSpectraWinUI.exe
```

Click **Start** to begin live capture. Validation hints (e.g., "Center your face") appear as a pill above the preview. Once vitals stabilize, the bottom panel shows pulse rate, breathing rate, and the dominant facial expression alongside live breathing and arterial-pressure traces. Click **Ask AI** during a session to send the buffered metrics to the insights endpoint and display the response.

## Project Layout

```text
winui3-sample/
├─ SmartSpectraWinUI.sln
└─ SmartSpectraWinUI/
   ├─ SmartSpectraWinUI.vcxproj   – packaged-WinUI3 project (unpackaged at runtime)
   ├─ App.xaml{,.h,.cpp}          – Application singleton, launches MainWindow
   ├─ MainWindow.xaml             – UI tree (preview, panel, graphs, buttons)
   ├─ MainWindow.xaml.{h,cpp}     – SDK wiring, callbacks, graph rendering
   ├─ MainWindow.idl              – runtime class declaration for MainWindow
   ├─ pch.{h,cpp}                 – precompiled header
   └─ app.manifest                – per-monitor v2 DPI awareness
```

## Notes

- The project sets `ApplicationType=Windows Store` (not because this is a UWP/Store app — it's a regular desktop app — but because that's the value that causes MSBuild to import the native XAML compiler targets installed by the *Windows application development* workload).
- The build is configured for `WindowsPackageType=None` and `WindowsAppSDKSelfContained=true`, producing an unpackaged executable that includes the Windows App SDK runtime in its output directory.
- `MainWindow.xaml.cpp` includes `<robuffer.h>` to acquire a writable `uint8_t*` from the `WriteableBitmap`'s pixel buffer; preview frames are RGB→BGRA-converted on the worker thread before being copied on the UI thread.
