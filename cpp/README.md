## Supported Platforms

| Platform | Notes |
| --- | --- |
| Ubuntu 22.04 / Mint 21 (amd64) | Debian package available |
| Ubuntu 22.04 / Mint 21 (arm64) | Debian package available (new) |
| macOS (Apple Silicon) | Homebrew package available |
| Windows 10 / 11 | NuGet feed and zip distributions available |
| Ubuntu 24.04 / Mint 22 | Contact [support@presagetech.com](mailto:support@presagetech.com) |
| Debian 12 | Contact [support@presagetech.com](mailto:support@presagetech.com) |
| RHEL 9 / Fedora 41 | Contact [support@presagetech.com](mailto:support@presagetech.com) |

## Installation

### Prerequisites

- **CMake 3.22.1+**
- **C++17 compiler** (GCC, Clang, or MSVC 2022)
- **API Key** from [physiology.presagetech.com](https://physiology.presagetech.com)

<Tabs items={["Linux (Ubuntu/Mint)", "macOS", "Windows"]}>
  <Tab value="Linux (Ubuntu/Mint)">
    Use your normal Linux C++ build environment. The SmartSpectra SDK itself is
    installed from the Debian package below.
  </Tab>
  <Tab value="macOS">
    Install [Homebrew](https://brew.sh) and Xcode Command Line Tools if you don't have them:

    ```bash
    xcode-select --install
    ```

    The Homebrew formula installs the self-contained SmartSpectra SDK package and exposes its CMake package metadata. You do not need to install OpenCV, protobuf, or other SDK runtime libraries separately.
  </Tab>
  <Tab value="Windows">
    1. Install **Visual Studio 2022** with the **Desktop development with C++** workload.
    2. Install **CMake 3.22.1+** via Visual Studio or from [cmake.org](https://cmake.org/download/).
    3. Open an **x64 Native Tools Command Prompt** for Visual Studio when building.
  </Tab>
</Tabs>

### Add the SDK

<Tabs items={["Debian (Ubuntu/Mint)", "Homebrew (macOS)", "NuGet (Windows)", "ZIP (Windows)", "NuGet .NET (Windows)"]}>
  <Tab value="Debian (Ubuntu/Mint)">
    The same `sources.list` entry serves both `amd64` and `arm64` Ubuntu 22.04 hosts — APT selects the package matching your system's `dpkg --print-architecture` automatically.

    > **arm64 users:** arm64 Debian packages are a recent addition. The PPA gains an arm64 `.deb` the first time a production SDK release is cut with arm64 publishing enabled. If `apt install libsmartspectra-dev` returns "package not found" on an arm64 host, first run `sudo apt update`; if the problem persists, the currently published PPA index may predate arm64 support — check the SDK release notes for the first version that lists an arm64 build.

    ```bash
    # Add Presage repository (one-time setup)
    sudo install -d -m 0755 /etc/apt/keyrings
    curl -fsSL https://packages.presagetech.com/KEY.gpg \
      | sudo gpg --dearmor -o /etc/apt/keyrings/presage-archive-keyring.gpg
    sudo chmod 644 /etc/apt/keyrings/presage-archive-keyring.gpg

    echo "deb [signed-by=/etc/apt/keyrings/presage-archive-keyring.gpg] https://packages.presagetech.com/apt/ubuntu jammy main" \
      | sudo tee /etc/apt/sources.list.d/presage-technologies.list

    # Install SDK (works on amd64 and arm64)
    sudo apt update && sudo apt install libsmartspectra-dev
    ```

    The `signed-by=` source entry scopes the Presage signing key to the Presage apt repository.

    The SmartSpectra SDK package is self-contained. You do not need to install OpenCV, protobuf, curl, OpenSSL, or other SDK runtime libraries separately.

    Verify that the package is visible to build tools:

    ```bash
    pkg-config --modversion SmartSpectra
    ```
  </Tab>
  <Tab value="Homebrew (macOS)">
    Public Homebrew packages target **Apple Silicon** systems.

    ```bash
    brew tap presage/smartspectra https://github.com/Presage-Security/homebrew-smartspectra
    brew install presage/smartspectra/smartspectra
    ```

    Release candidates are opt-in and install from a separate formula:

    ```bash
    brew install presage/smartspectra/smartspectra-rc
    ```

    Release candidates do not automatically migrate to the stable formula. After a stable release ships, uninstall `smartspectra-rc` and install `smartspectra` to return to the stable channel.

    Dependencies (OpenCV, protobuf) are installed automatically.

    If `find_package(SmartSpectra REQUIRED)` does not locate the SDK, add Homebrew's prefix to `CMAKE_PREFIX_PATH` before configuring:

    ```bash
    export CMAKE_PREFIX_PATH="/opt/homebrew${CMAKE_PREFIX_PATH:+:$CMAKE_PREFIX_PATH}"
    ```
  </Tab>
  <Tab value="NuGet (Windows)">
    Recommended for Visual Studio and MSBuild projects when you have access to a SmartSpectra
    NuGet feed. The package sets include directories and linker inputs automatically via
    `SmartSpectra.props`, and copies the required DLLs to your build output via
    `SmartSpectra.targets`.

    Add your SmartSpectra NuGet feed as a package source:

    ```powershell
    nuget sources add -Name SmartSpectra `
        -Source <SMARTSPECTRA_NUGET_FEED_URL>
    ```

    Install the package:

    ```powershell
    nuget install SmartSpectra -Version <version>
    ```

    Or declare it in your project file:

    ```xml
    <PackageReference Include="SmartSpectra" Version="<version>" />
    ```

    Replace `<version>` with the current version available from your SmartSpectra NuGet feed.

    No further include, lib, or DLL configuration is required.

    The SmartSpectra SDK package is self-contained. You do not need to install OpenCV, protobuf, or other SDK runtime libraries separately.
  </Tab>
  <Tab value="ZIP (Windows)">
    For CMake-based projects or when a package manager is not available.

    Download `smartspectra-sdk-<version>-windows-x64.zip` from
    [GitHub Releases](https://github.com/Presage-Security/SmartSpectra/releases)
    and extract it.

    Set `SMARTSPECTRA_SDK_PATH` to the extracted directory:

    ```powershell
    $env:SMARTSPECTRA_SDK_PATH = "C:\path\to\smartspectra-sdk"
    ```

    Reference it in your `CMakeLists.txt`:

    ```cmake
    list(APPEND CMAKE_PREFIX_PATH $ENV{SMARTSPECTRA_SDK_PATH})
    find_package(SmartSpectra REQUIRED)
    ```

    Keep the extracted SDK layout intact so CMake config files, runtime DLLs, and bundled resources stay in the locations expected by the package.

    The SmartSpectra SDK package is self-contained. You do not need to install OpenCV, protobuf, or other SDK runtime libraries separately.

    Consumer source should include the public headers as:

    ```cpp
    #include <smartspectra/smartspectra.h>
    #include <smartspectra/smartspectra_config.h>
    #include <smartspectra/messages/metrics.h>
    ```
  </Tab>
  <Tab value="NuGet .NET (Windows)">
    For C# and .NET 8 projects on Windows. Uses the `SmartSpectra.Net` package, which bundles
    a pure P/Invoke wrapper over the C ABI shim and the native runtime DLLs. Metrics are
    returned as fully-typed protobuf objects via the included `SmartSpectra.Net.Protos` assembly.

    Add your SmartSpectra NuGet feed as a package source:

    ```powershell
    nuget sources add -Name SmartSpectra `
        -Source <SMARTSPECTRA_NUGET_FEED_URL>
    ```

    Install the package:

    ```xml
    <PackageReference Include="SmartSpectra.Net" Version="<version>" />
    ```

    Replace `<version>` with the current version available from your SmartSpectra NuGet feed.

    `Google.Protobuf` and the native DLLs are pulled in automatically.
  </Tab>
</Tabs>

### Permissions

<Tabs items={["Linux", "macOS", "Windows"]}>
  <Tab value="Linux">
    No SDK-specific OS permission setup is required.
  </Tab>
  <Tab value="macOS">
    SmartSpectra's default macOS builds require a signed host app with the
    keychain entitlements needed for the SDK's device-key registration path.

    This applies to SDK startup in general, including file-based processing —
    not just live camera use. An unsigned executable can fail on `Start()`
    with keychain entitlement errors such as `errSecMissingEntitlement
    (-34018)` before any video source is opened.

    If you run the sample apps or integrate the public Homebrew SDK, use the
    [macOS signing flow](samples/README.md#macos-signing).

    The main exception is a custom build with remote model delivery disabled
    (for example `-DSMARTSPECTRA_DISABLE_REMOTE_MODEL_DELIVERY=ON`), which avoids the
    default EMD keychain path.
  </Tab>
  <Tab value="Windows">
    No SDK-specific OS permission setup is required.
  </Tab>
</Tabs>

## Example

Build and run from a supported development environment. The C++ SDK is headless by default and can use either a built-in camera or custom frame input.

### Configuration

```cpp
spectra::SmartSpectraConfig config;
config.api_key = api_key;
config.requested_metrics = spectra::SmartSpectraConfig::BreathingMetrics();
config.AddMetrics(spectra::SmartSpectraConfig::CardioMetrics());

spectra::SmartSpectra spectra(config);
const auto source_error =
    spectra.UseCamera().SetResolution(1280, 720).SetFps(30).Build();
```

`BreathingMetrics()` is the named breathing bundle. Add `CardioMetrics()`,
`FaceMetrics()`, or other groups explicitly when your app expects those outputs.

### Authentication

- **API Key**: Pass an API key into `SmartSpectraConfig` before initializing the SDK.

An internet connection is required for subscription validation when using the standard SDK.

### Quick Start

**hello_vitals.cpp:**

```cpp
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/messages/metrics.h>
#include <glog/logging.h>
#include <chrono>
#include <cstdlib>
#include <iostream>
#include <thread>

namespace spectra = presage::smartspectra;

int main(int argc, char** argv) {
    google::InitGoogleLogging(argv[0]);
    FLAGS_alsologtostderr = true;

    std::string api_key;
    if (argc > 1) api_key = argv[1];
    else if (auto* k = std::getenv("SMARTSPECTRA_API_KEY")) api_key = k;
    else { std::cerr << "Usage: ./hello_vitals YOUR_API_KEY\n"; return 1; }

    spectra::SmartSpectraConfig config;
    config.api_key = api_key;
    config.requested_metrics = spectra::SmartSpectraConfig::BreathingMetrics();
    config.AddMetrics(spectra::SmartSpectraConfig::CardioMetrics());

    spectra::SmartSpectra spectra(config);
    spectra.SetOnMetrics([](const presage::smartspectra::Metrics& metrics, int64_t) {
        if (metrics.has_cardio()) {
            LOG(INFO) << "Cardio metrics: " << metrics.cardio();
        }
        if (metrics.has_breathing()) {
            LOG(INFO) << "Breathing metrics: " << metrics.breathing();
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
        LOG(ERROR) << "Failed to start: "
                   << err.message;
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

**CMakeLists.txt:**

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(SmartSpectraHelloVitals CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(SmartSpectra REQUIRED)
add_executable(hello_vitals hello_vitals.cpp)
target_link_libraries(hello_vitals SmartSpectra::SDK)
```

<Tabs items={["Linux / macOS", "Windows"]}>
  <Tab value="Linux / macOS">
    ```bash
    mkdir build && cd build
    cmake .. && make
    ./hello_vitals YOUR_API_KEY
    ```
  </Tab>
  <Tab value="Windows">
    From an **x64 Native Tools Command Prompt**:

    ```powershell
    mkdir build && cd build
    cmake .. -G "Visual Studio 17 2022" -A x64
    cmake --build . --config Release
    Release\hello_vitals.exe YOUR_API_KEY
    ```
  </Tab>
</Tabs>

## Additional Details

### requested_metrics

`SmartSpectraConfig::requested_metrics` controls which metrics the SDK requests
for authorization and output.

- An empty `requested_metrics` list uses `DefaultSupportedMetrics()`.
- `DefaultSupportedMetrics()` returns `BreathingMetrics()` and is the SDK's
  empty-request fallback.
- The static helper lists such as `BreathingMetrics()`, `CardioMetrics()`,
  and `FaceMetrics()` are intended as ready-made groups you can assign
  directly or combine with `AddMetrics()`.

Examples:

```cpp
// Implicit fallback: same effect as DefaultSupportedMetrics().
spectra::SmartSpectraConfig breathing_only;
breathing_only.api_key = api_key;
```

```cpp
// Explicit breathing request.
spectra::SmartSpectraConfig explicit_breathing;
explicit_breathing.api_key = api_key;
explicit_breathing.requested_metrics =
    spectra::SmartSpectraConfig::BreathingMetrics();
```

```cpp
// Start with breathing, then add another predefined group.
spectra::SmartSpectraConfig breathing_plus_cardio;
breathing_plus_cardio.api_key = api_key;
breathing_plus_cardio.requested_metrics =
    spectra::SmartSpectraConfig::BreathingMetrics();
breathing_plus_cardio.AddMetrics(
    spectra::SmartSpectraConfig::CardioMetrics());
```

### Frame Input Options

```cpp
// Built-in camera (default)
spectra.UseCamera().SetResolution(1280, 720).SetFps(30).Build();

// Custom frame input
std::shared_ptr<CustomInput> handle;
if (auto err = spectra.UseCustomInput().Build(handle); !err.ok()) {
    // Handle setup error: err.FullMessage()
}
// Feed frames via handle->Send(frame, timestamp_us)
// with strictly monotonic timestamps in microseconds.
```

### Installed Paths

On macOS with Homebrew (Apple Silicon), the default installed paths are:

- **Headers**: `/opt/homebrew/include/smartspectra/`
- **Libraries**: `/opt/homebrew/lib/`
- **CMake config**: `/opt/homebrew/lib/cmake/SmartSpectra/`
- **pkg-config**: `/opt/homebrew/lib/pkgconfig/SmartSpectra.pc`

Consumer code should include SmartSpectra headers as:

```cpp
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/messages/metrics.h>
```

### Windows SDK Layout

**ZIP archive** (`SmartSpectra-<version>-windows-x64.zip`):

Consumer code should include SmartSpectra headers as:

```cpp
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/messages/metrics.h>
```

The ZIP layout is:

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
  smartspectra_capi.dll       # C ABI shim runtime DLL — required for FFI and .NET consumers;
                              #   include when redistributing if your app uses the C shim or SmartSpectra.Net
  physiology_edge_manifest.txt
  opencv_world4100.dll        # OpenCV runtime dependency — must ship with your app
share/smartspectra/           # Bundled graph and model resources
```

**NuGet package** (`SmartSpectra.<version>.nupkg`) — MSBuild integration handled automatically:

```text
build/native/
  include/              # headers (injected via SmartSpectra.props)
  lib/x64/              # import libs + capi static lib (injected via SmartSpectra.props)
  SmartSpectra.props    # sets AdditionalIncludeDirectories + AdditionalDependencies
  SmartSpectra.targets  # copies DLLs to output directory at build time
runtimes/win-x64/native/
  smartspectra.dll
  opencv_world4100.dll
```

### Debian Advanced Apt Workflows

Most Linux users only need the stable `jammy` repository shown in [Add the SDK](#add-the-sdk). Use the workflows below when you intentionally need release-candidate packages, package holds, or repository removal.

#### Release-Candidate Builds

Release-candidate builds are published to a parallel `jammy-rc` apt suite signed by the same Presage key:

```bash
echo "deb [signed-by=/etc/apt/keyrings/presage-archive-keyring.gpg] https://packages.presagetech.com/apt/ubuntu jammy-rc main" \
  | sudo tee /etc/apt/sources.list.d/presage-technologies-rc.list

sudo apt update && sudo apt -t jammy-rc install libsmartspectra-dev
```

Keep the stable `jammy` source configured alongside `jammy-rc`; the RC channel does not republish stable releases.

To keep stable as the default for upgrades, add a low-priority pin:

```text
Package: *
Pin: release n=jammy-rc
Pin-Priority: 100
```

With that pin, `apt upgrade` follows the stable channel, and `apt -t jammy-rc install libsmartspectra-dev` opts into an RC build on demand.

#### Returning From RC to Stable

```bash
sudo apt update
sudo apt install --reinstall -t jammy libsmartspectra-dev=$(apt-cache madison libsmartspectra-dev | awk '/jammy\/main/ {print $3; exit}')
sudo rm -f /etc/apt/sources.list.d/presage-technologies-rc.list
sudo rm -f /etc/apt/preferences.d/presage-rc
sudo apt update
```

#### Holding a Debian Package Version

```bash
sudo apt-mark hold libsmartspectra-dev
sudo apt-mark showhold
```

Release the hold when you're ready to take updates again:

```bash
sudo apt-mark unhold libsmartspectra-dev
```

#### Uninstalling the Debian Package

```bash
sudo apt remove --purge libsmartspectra-dev
sudo apt autoremove --purge
sudo rm -f /etc/apt/sources.list.d/presage-technologies.list
sudo rm -f /etc/apt/sources.list.d/presage-technologies-rc.list
sudo rm -f /etc/apt/preferences.d/presage-rc
sudo rm -f /etc/apt/keyrings/presage-archive-keyring.gpg
sudo rm -f /etc/apt/trusted.gpg.d/presage-technologies.gpg
sudo apt update
```

### Next Steps

- [Configure which metrics to compute](/docs/cpp/metrics)
- [Headless mode](/docs/headless-mode) — C++ is headless by default, but see the guide for video output callbacks
- [Migration Guide](/docs/cpp/migration-guide) for upgrading from older SDK versions

## Troubleshooting

If you're upgrading from older C++ integrations, start with the [C++ Migration Guide](/docs/cpp/migration-guide).

### Debian `Signed-By` Conflict

Older Debian instructions installed the Presage key in `/etc/apt/trusted.gpg.d/` and used a source line without `signed-by=`. If `apt update` reports `E: Conflicting values set for option Signed-By regarding source https://packages.presagetech.com/apt/ubuntu/ jammy`, remove the legacy key copy and run `apt update` again:

```bash
sudo rm -f /etc/apt/trusted.gpg.d/presage-technologies.gpg
sudo apt update
```

For installation, setup, and runtime issues, contact support for C++ platform-specific guidance.

For support: contact [support@presagetech.com](mailto:support@presagetech.com) or [submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues).
