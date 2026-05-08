---
title: C++ on Linux
description: Install the SmartSpectra C++ SDK package and build Linux apps on Ubuntu and Linux Mint.
---

> **⚠️ Experimental platform**
>
> Linux support for the SmartSpectra C++ SDK is experimental. If you have any
> issues running SmartSpectra, [contact Presage
> support](https://physiology.presagetech.com) for assistance.

## Supported Platforms

| Platform | Notes |
| -------- | ----- |
| Ubuntu 22.04 / Mint 21 (amd64) | Debian package available |
| Ubuntu 22.04 / Mint 21 (arm64) | Debian package available |
| Ubuntu 24.04 / Mint 22 | Contact [support@presagetech.com](mailto:support@presagetech.com) |
| Debian 12 | Contact [support@presagetech.com](mailto:support@presagetech.com) |
| RHEL 9 / Fedora 41 | Contact [support@presagetech.com](mailto:support@presagetech.com) |

## Installation

### Prerequisites

- **CMake 3.22.1 or later** (the version shipped with Ubuntu 22.04 / Mint 21 is sufficient)
- **C++17 compiler** such as GCC or Clang
- **API key** from [physiology.presagetech.com](https://physiology.presagetech.com)

### Add the SDK

The same `sources.list` entry serves both `amd64` and `arm64` Ubuntu 22.04
hosts. APT selects the package matching your system's `dpkg --print-architecture`
automatically.

```bash
sudo install -d -m 0755 /etc/apt/keyrings
curl -fsSL https://packages.presagetech.com/KEY.gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/presage-archive-keyring.gpg
sudo chmod 644 /etc/apt/keyrings/presage-archive-keyring.gpg

echo "deb [signed-by=/etc/apt/keyrings/presage-archive-keyring.gpg] https://packages.presagetech.com/apt/ubuntu jammy main" \
  | sudo tee /etc/apt/sources.list.d/presage-technologies.list

sudo apt update
sudo apt install libsmartspectra-dev
```

The `signed-by=` source entry scopes the Presage signing key to the Presage apt
repository.

The SmartSpectra SDK package is self-contained. You do not need to install
OpenCV, protobuf, curl, OpenSSL, or other SDK runtime libraries separately.

Verify that the package is visible to build tools:

```bash
pkg-config --modversion SmartSpectra
```

To keep a working machine on the currently installed SDK version while you test
or stage a rollout, hold the package:

```bash
sudo apt-mark hold libsmartspectra-dev
```

Release the hold when you are ready to take SDK updates again:

```bash
sudo apt-mark unhold libsmartspectra-dev
```

### Permissions

No SDK-specific OS permission setup is required on Linux.

## Example

This quick start creates a minimal CMake project that links against the
installed `SmartSpectra::SDK` package and reads from the default camera.

You will create exactly these files:

1. `hello_vitals/hello_vitals.cpp`
2. `hello_vitals/CMakeLists.txt`

### Get an API key

1. Open the Presage Developer Admin Service [Portal](https://physiology.presagetech.com).
2. Register or log in.
3. Copy your API key from the portal.

### Step 1 - Create the project directory

```bash
mkdir hello_vitals
cd hello_vitals
```

### Step 2 - Create `hello_vitals.cpp`

Open a new file named `hello_vitals.cpp` in your editor of choice and paste this
entire file:

```cpp
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/messages/metrics.h>
#include <glog/logging.h>
#include <chrono>
#include <cstdlib>
#include <iostream>
#include <string>
#include <thread>

namespace spectra = presage::smartspectra;

int main(int argc, char** argv) {
    google::InitGoogleLogging(argv[0]);
    FLAGS_alsologtostderr = true;

    std::string api_key;
    if (argc > 1) api_key = argv[1];
    else if (auto* k = std::getenv("SMARTSPECTRA_API_KEY")) api_key = k;
    else if (auto* k = std::getenv("PHYSIOLOGY_API_KEY")) api_key = k;
    else {
        std::cerr << "Usage: ./hello_vitals YOUR_API_KEY\n"
                  << "or export SMARTSPECTRA_API_KEY=YOUR_API_KEY\n";
        return 1;
    }

    spectra::SmartSpectraConfig config;
    config.api_key = api_key;
    config.requested_metrics = spectra::SmartSpectraConfig::BreathingMetrics();
    config.AddMetrics(spectra::SmartSpectraConfig::CardioMetrics());

    spectra::SmartSpectra sdk(config);
    sdk.SetOnMetrics([](const spectra::Metrics& metrics, int64_t) {
        if (metrics.has_cardio()) {
            LOG(INFO) << "Cardio metrics: " << metrics.cardio().ShortDebugString();
        }
        if (metrics.has_breathing()) {
            LOG(INFO) << "Breathing metrics: " << metrics.breathing().ShortDebugString();
        }
    });
    sdk.SetOnError([](const spectra::SmartSpectraError& error) {
        LOG(ERROR) << "Error [" << static_cast<int>(error.code)
                   << "]: " << error.message;
    });

    const auto source_error =
        sdk.UseCamera().SetResolution(1280, 720).SetFps(30).Build();
    if (!source_error.ok()) {
        LOG(ERROR) << "Failed to create camera source: " << source_error.message;
        return 1;
    }

    if (const auto err = sdk.Start(); !err.ok()) {
        LOG(ERROR) << "Failed to start: " << err.message;
        return 1;
    }

    std::cout << "Processing for 20 seconds...\n";
    std::this_thread::sleep_for(std::chrono::seconds(20));
    if (const auto err = sdk.Stop(); !err.ok()) {
        std::cerr << "Stop failed: " << err.message << "\n";
    }
    return 0;
}
```

The example requests breathing and cardio metrics. Add `FaceMetrics()` or other
metric groups with `config.AddMetrics(...)` when your app expects those outputs.

### Step 3 - Create `CMakeLists.txt`

Open a new file named `CMakeLists.txt` in the same directory and paste this
entire file:

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(SmartSpectraHelloVitals CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(SmartSpectra REQUIRED)
add_executable(hello_vitals hello_vitals.cpp)
target_link_libraries(hello_vitals SmartSpectra::SDK)
```

### Step 4 - Build

```bash
cmake -S . -B build
cmake --build build
```

### Step 5 - Run

Pass the API key as an argument:

```bash
./build/hello_vitals YOUR_API_KEY
```

Or set it once in your shell:

```bash
export SMARTSPECTRA_API_KEY="YOUR_API_KEY"
./build/hello_vitals
```

The app logs metrics for 20 seconds, then exits. An internet connection is
required for subscription validation when using the standard SDK.

If CMake cannot locate `SmartSpectra`, run:

```bash
pkg-config --modversion SmartSpectra
```

If that command fails, reinstall `libsmartspectra-dev` and confirm you are on a
supported Ubuntu 22.04 or Mint 21 `amd64` or `arm64` host.

## Running headless (Docker, CI, no desktop)

A desktop Ubuntu or Mint session provides D-Bus and a Secret Service backend
(gnome-keyring) automatically. Without one — in a Docker container, on a CI
runner, or in an SSH session with no desktop — the SDK cannot persist its
device identity and aborts at initialization with:

```
Load secret 'key_id' failed: D-Bus Secret Service is not reachable
```

Install a D-Bus launcher and a Secret Service backend, then start a session
bus and unlock a fresh keyring before running your binary:

```bash
sudo apt install -y dbus-x11 gnome-keyring
eval "$(dbus-launch)"
echo "" | gnome-keyring-daemon --unlock --components=secrets >/dev/null 2>&1
./build/hello_vitals
```

`dbus-launch` exports `DBUS_SESSION_BUS_ADDRESS` into the current shell, and
`gnome-keyring-daemon --unlock --components=secrets` opens the secrets backend
with an empty passphrase so libsecret reads and writes keys unattended. The
same three commands also satisfy the SDK on a stock Ubuntu Server install.

## Build the Provided Samples

The SDK package does not install the sample source code. To build the repository
samples against the installed SDK, clone the SmartSpectra repository after
installing `libsmartspectra-dev`:

```bash
git clone https://github.com/Presage-Security/SmartSpectra.git
cd SmartSpectra/cpp/samples
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --target minimal_example
```

Run a sample with your API key:

```bash
./build/minimal_example/minimal_example --api_key=YOUR_API_KEY
```

## Next Steps

- [Configure which metrics to compute](metrics.md)
- [Run headless without video output](../headless-mode.md)
- [Migration Guide](migration-guide.md) for upgrading from older SDK versions

## Documentation

API reference available at [C++ API Reference](api-reference.md).

## Troubleshooting

If you are upgrading an older C++ integration, start with the [C++ Migration Guide](migration-guide.md).

If your binary fails at startup with `Load secret 'key_id' failed: D-Bus
Secret Service is not reachable`, you are on a host without a desktop session
— see [Running headless](#running-headless-docker-ci-no-desktop) for the
D-Bus and keyring bootstrap.

For support: contact [support@presagetech.com](mailto:support@presagetech.com) or [submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues).
