---
title: C++ on Windows
description: Install and build the SmartSpectra C++ SDK on Windows.
---

# SmartSpectra C++ Quickstart — Windows

> **Warning — Experimental platform:** Windows support for the SmartSpectra
> C++ SDK is experimental. If you have any issues running SmartSpectra,
> [contact Presage support](https://physiology.presagetech.com) for assistance.

## Supported Platforms

| Platform | Status | Notes |
| -------- | ------ | ----- |
| Windows 10 / 11 (x64) | Experimental | ZIP distribution available |

For platforms not listed above, contact
[support@presagetech.com](mailto:support@presagetech.com) if you have a
specific need.

## Installation

### Prerequisites

Install **Visual Studio Build Tools 2022 or later** with the **Desktop development with C++** workload.

You can use either the full Visual Studio IDE or the standalone Build Tools:

- **Full IDE**: [Visual Studio 2022](https://visualstudio.microsoft.com/vs/) — recommended if you want the Visual Studio CMake project UI shown in the example below.
- **Build Tools only**: [Visual Studio Build Tools 2022](https://visualstudio.microsoft.com/visual-cpp-build-tools/) — lighter install for command-line builds.

During installation, select the **Desktop development with C++** workload. CMake and the x64 build tools are included automatically.

You also need an **API key** from [physiology.presagetech.com](https://physiology.presagetech.com).

### Add the SDK

Download `smartspectra-sdk-<version>-windows-x64.zip` from
[GitHub Releases](https://github.com/Presage-Security/SmartSpectra/releases)
and extract it to a permanent location, for example `C:\SmartSpectra`.

Keep the extracted layout intact — CMake config files, runtime DLLs, and
bundled resources must stay in the locations expected by the package.

### Permissions

No SDK-specific OS permission setup is required on Windows.

## Example: Visual Studio CMake Project

This walkthrough sets up a minimal CMake project in Visual Studio that reads
from a camera and prints vitals to the console.

### 1. Create the project files

Create a folder, for example `C:\Projects\HelloVitals`, and add these three
files inside it.

**`CMakeLists.txt`**:

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(HelloVitals CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

list(APPEND CMAKE_PREFIX_PATH $ENV{SMARTSPECTRA_SDK_PATH})
find_package(SmartSpectra REQUIRED)

add_executable(hello_vitals hello_vitals.cpp)
target_link_libraries(hello_vitals SmartSpectra::SDK)

# Copy SmartSpectra runtime DLLs (and their dependencies) next to the exe
# so it can be launched directly from Visual Studio or Explorer without
# having to put the SDK bin directory on PATH.
add_custom_command(TARGET hello_vitals POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
            $<TARGET_RUNTIME_DLLS:hello_vitals> $<TARGET_FILE_DIR:hello_vitals>
    COMMAND_EXPAND_LISTS)
```

**`CMakeSettings.json`** — tells Visual Studio where the extracted SDK is:

```json
{
  "configurations": [
    {
      "name": "x64-Release",
      "generator": "Visual Studio 17 2022",
      "configurationType": "Release",
      "buildRoot": "${projectDir}\\out\\build\\${name}",
      "variables": [
        {
          "name": "CMAKE_PREFIX_PATH",
          "value": "C:\\SmartSpectra",
          "type": "PATH"
        }
      ]
    }
  ]
}
```

Replace `C:\\SmartSpectra` with the folder where you extracted the ZIP.

**`hello_vitals.cpp`**:

```cpp
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/messages/metrics.h>
#include <glog/logging.h>
#include <chrono>
#include <iostream>
#include <string>
#include <thread>

namespace spectra = presage::smartspectra;

int main(int argc, char** argv) {
    google::InitGoogleLogging(argv[0]);
    google::SetStderrLogging(google::INFO);

    std::string api_key = "YOUR_API_KEY";

    spectra::SmartSpectraConfig config;
    config.api_key = api_key;
    config.requested_metrics = spectra::SmartSpectraConfig::BreathingMetrics();
    config.AddMetrics(spectra::SmartSpectraConfig::CardioMetrics());

    spectra::SmartSpectra spectra(config);
    spectra.SetOnMetrics([](const presage::smartspectra::Metrics& metrics, int64_t) {
        if (metrics.has_cardio()) {
            LOG(INFO) << "Cardio metrics: " << metrics.cardio().ShortDebugString();
        }
        if (metrics.has_breathing()) {
            LOG(INFO) << "Breathing metrics: " << metrics.breathing().ShortDebugString();
        }
    });
    spectra.SetOnError([](const spectra::SmartSpectraError& error) {
        LOG(ERROR) << "Error [" << static_cast<int>(error.code)
                   << "]: " << error.message;
    });

    const auto source_error =
        spectra.UseCamera().SetResolution(1280, 720).SetFps(30).Build();
    if (!source_error.ok()) {
        LOG(ERROR) << "Failed to create camera source: " << source_error.message;
        return 1;
    }

    if (const auto err = spectra.Start(); !err.ok()) {
        LOG(ERROR) << "Failed to start: " << err.message;
        return 1;
    }

    std::cout << "Processing... Press Ctrl+C to stop.\n";
    std::this_thread::sleep_for(std::chrono::seconds(5));
    if (const auto err = spectra.Stop(); !err.ok()) {
        std::cerr << "Stop failed: " << err.message << "\n";
    }
    return 0;
}
```

### 2. Open the project in Visual Studio

1. Open Visual Studio 2022.
2. Choose **Open a local folder** and select `C:\Projects\HelloVitals`.
3. Visual Studio detects `CMakeLists.txt` and configures the project automatically.
   The **Output** window shows CMake configuration progress.
4. Wait for the **CMake generation finished** message in the Output window before building.

If Visual Studio does not pick up the SDK path from `CMakeSettings.json`,
open **Project → CMake Settings** and verify that `CMAKE_PREFIX_PATH` points
to your extracted SDK folder.

### 3. Build

Select **x64-Release** from the configuration dropdown in the toolbar, then
choose **Build → Build All** (or press `Ctrl+Shift+B`).

The compiled executable is placed in
`out\build\x64-Release\Release\hello_vitals.exe`.

### 4. Run

Replace `"YOUR_API_KEY"` in `hello_vitals.cpp` with your key from
[physiology.presagetech.com](https://physiology.presagetech.com) and rebuild.

Then either press `Ctrl+F5` (**Debug → Start Without Debugging**) in Visual
Studio, or run the executable directly:

```powershell
.\out\build\x64-Release\Release\hello_vitals.exe
```

The SDK DLLs are copied next to `hello_vitals.exe` by the post-build step in
`CMakeLists.txt`, so no `PATH` setup is required.

You should see breathing and cardio metrics printed to the console within a few
seconds of the camera starting.

## Additional Details

### Metric selection

Adjust the metric bundles in `hello_vitals.cpp` before building:

```cpp
config.requested_metrics = spectra::SmartSpectraConfig::BreathingMetrics();
config.AddMetrics(spectra::SmartSpectraConfig::CardioMetrics());
```

Available bundles: `BreathingMetrics()`, `CardioMetrics()`, `FaceMetrics()`.

### ZIP layout reference

```text
include/
  smartspectra/                    # C++ SDK headers and protobuf metric headers
  smartspectra/interface/          # Bundled third-party headers
  smartspectra_capi.h              # C ABI shim for FFI consumers
lib/
  smartspectra.lib                             # C++ SDK import library (MSVC)
  smartspectra_capi.lib                        # C ABI shim import library
  SmartSpectra_MessageProtos_*.lib             # Message proto static libs
  cmake/SmartSpectra/SmartSpectraConfig.cmake  # CMake package
bin/
  smartspectra.dll            # C++ SDK runtime DLL — must ship with your app
  smartspectra_capi.dll       # C ABI shim runtime DLL — required for FFI consumers
  physiology_edge_manifest.txt
  opencv_world4100.dll        # OpenCV runtime dependency — must ship with your app
share/smartspectra/           # Bundled graph and model resources
```

When you ship your app to other machines, copy `smartspectra.dll`,
`opencv_world4100.dll`, and the contents of `share/smartspectra/` next to
the executable (or onto the PATH).

## Next Steps

- [Configure which metrics to compute](metrics.md)
- [Run headless without video output](headless-mode.md)
- [Migration Guide](migration-guide.md) for upgrading from older SDK versions

## Troubleshooting

If the app starts but can't find DLLs, verify that `smartspectra.dll`,
`opencv_world*.dll`, and other SDK DLLs from the extracted ZIP are present
next to the executable or on the Windows DLL search path.

If you are upgrading an older C++ integration, see the [C++ Migration Guide](migration-guide.md).

For support: contact [support@presagetech.com](mailto:support@presagetech.com) or [submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues).
