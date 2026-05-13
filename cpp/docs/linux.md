---
title: C++ on Linux
description: Install the SmartSpectra C++ SDK package and build Linux apps on Ubuntu and Linux Mint.
---

# SmartSpectra C++ Quickstart — Linux (Ubuntu/Mint)

> **Warning — Experimental platform:** Linux support for the SmartSpectra C++
> SDK is experimental. If you have any issues running SmartSpectra,
> [contact Presage support](https://physiology.presagetech.com) for assistance.

## Supported Platforms

| Platform | Status | Notes |
| -------- | ------ | ----- |
| Ubuntu 22.04 / Mint 21 (amd64) | Experimental | Debian package available |
| Ubuntu 22.04 / Mint 21 (arm64) | Experimental | Debian package available |
| Ubuntu 24.04 / Mint 22 | Coming soon | — |
| Debian 12 | Not supported | — |
| RHEL 9 / Fedora 41 | Not supported | — |

For platforms marked "Not supported" or anything not listed above, contact
[support@presagetech.com](mailto:support@presagetech.com) if you have a
specific need.

## Installation

### Prerequisites

- **CMake 3.22.1 or later** (the version shipped with Ubuntu 22.04 / Mint 21 is sufficient)
- **C++17 compiler** such as GCC or Clang
- **`curl`, `gpg`, and `pkg-config`** — used by the install and verify steps below. Install with `sudo apt install curl gpg pkg-config` if they are not already present.
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

The command prints the installed SDK version (for example, `1.7.0`). If it
prints nothing or reports that the package is missing, reinstall
`libsmartspectra-dev` and confirm you are on a supported Ubuntu 22.04 or Mint
21 `amd64` or `arm64` host.

## Example

This quick start creates a minimal CMake project that links against the
installed `SmartSpectra::SDK` package and reads from the default camera.

You will create exactly these files:

1. `hello_vitals/hello_vitals.cpp`
2. `hello_vitals/CMakeLists.txt`

### Step 1 - Get an API key

1. Open the Presage Developer Admin Service [Portal](https://physiology.presagetech.com).
2. Register or log in.
3. Copy your API key from the portal.

### Step 2 - Create the project directory

```bash
mkdir hello_vitals
cd hello_vitals
```

### Step 3 - Create `hello_vitals.cpp`

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

### Step 4 - Create `CMakeLists.txt`

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

### Step 5 - Build

```bash
cmake -S . -B build
cmake --build build
```

A successful build produces the executable at `build/hello_vitals`. If CMake
reports that it cannot locate `SmartSpectra`, rerun
`pkg-config --modversion SmartSpectra` to confirm the SDK is installed
correctly before continuing.

### Step 6 - Run

Pass the API key as an argument:

```bash
./build/hello_vitals YOUR_API_KEY
```

Or set it once in your shell:

```bash
export SMARTSPECTRA_API_KEY="YOUR_API_KEY"
./build/hello_vitals
```

Sit centered in front of the webcam, well-lit, and stay reasonably still. The
app logs breathing and cardio metrics for 20 seconds, then exits. If no face is
detected the app still runs and exits cleanly, but no metrics callbacks fire.
An internet connection is required for subscription validation when using the
standard SDK.

## Running headless (Docker, CI, no desktop)

A desktop Ubuntu or Mint session provides D-Bus and a Secret Service backend
(gnome-keyring) automatically. Without one — in a Docker container, on a CI
runner, or in an SSH session with no desktop — the SDK cannot persist its
device identity and aborts at initialization with:

```text
Load secret 'key_id' failed: D-Bus Secret Service is not reachable
```

Install a D-Bus launcher and a Secret Service backend, then start a session
bus and unlock a fresh keyring before running your binary:

```bash
sudo apt install -y dbus-x11 gnome-keyring
eval "$(dbus-launch --sh-syntax)"
echo "" | gnome-keyring-daemon --unlock --components=secrets >/dev/null 2>&1
./build/hello_vitals
```

`dbus-launch --sh-syntax` writes `export DBUS_SESSION_BUS_ADDRESS=…;` to
stdout so the `eval` exports the address into the current shell's
environment, and `gnome-keyring-daemon --unlock --components=secrets` opens
the secrets backend with an empty passphrase so libsecret reads and writes
keys unattended. The same three commands also satisfy the SDK on a stock
Ubuntu Server install. (Without `--sh-syntax`, `dbus-launch` prints bare
`KEY=value` lines that `eval` treats as shell-local assignments rather
than env exports, so the SDK subprocess does not inherit the bus address.)

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

## Advanced apt workflows

Most Linux users only need the stable `jammy` repository above. Use these when
you intentionally need release-candidate packages, version pinning, or
repository removal.

### Pinning the installed SDK version

To keep a working machine on the currently installed SDK version while you test
or stage a rollout, hold the package:

```bash
sudo apt-mark hold libsmartspectra-dev
```

Release the hold when you are ready to take SDK updates again:

```bash
sudo apt-mark unhold libsmartspectra-dev
```

### Release-candidate channel

Release-candidate builds are published to a parallel `jammy-rc` apt suite
signed by the same Presage key:

```bash
echo "deb [signed-by=/etc/apt/keyrings/presage-archive-keyring.gpg] https://packages.presagetech.com/apt/ubuntu jammy-rc main" \
  | sudo tee /etc/apt/sources.list.d/presage-technologies-rc.list

sudo apt update && sudo apt -t jammy-rc install libsmartspectra-dev
```

Keep the stable `jammy` source configured alongside `jammy-rc`; the RC
channel does not republish stable releases.

### Returning from RC to stable

```bash
sudo apt update
sudo apt install --reinstall -t jammy libsmartspectra-dev=$(apt-cache madison libsmartspectra-dev | awk '/jammy\/main/ {print $3; exit}')
sudo rm -f /etc/apt/sources.list.d/presage-technologies-rc.list
sudo rm -f /etc/apt/preferences.d/presage-rc
sudo apt update
```

### Uninstalling the package

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

## Next Steps

- [Configure which metrics to compute](metrics.md)
- [Run headless without video output](headless-mode.md)
- [Migration Guide](migration-guide.md) for upgrading from older SDK versions

## Documentation

API reference available at [C++ API Reference](https://smartspectra.presagetech.com/docs/cpp/api-reference).

## Troubleshooting

If you are upgrading an older C++ integration, start with the [C++ Migration Guide](migration-guide.md).

If your binary fails at startup with `Load secret 'key_id' failed: D-Bus
Secret Service is not reachable`, you are on a host without a desktop session
— see [Running headless](#running-headless-docker-ci-no-desktop) for the
D-Bus and keyring bootstrap.

### Debian `Signed-By` conflict

Older Debian instructions installed the Presage key in
`/etc/apt/trusted.gpg.d/` and used a source line without `signed-by=`. If
`apt update` reports `E: Conflicting values set for option Signed-By regarding source https://packages.presagetech.com/apt/ubuntu/ jammy`, remove the legacy key copy and run `apt update` again:

```bash
sudo rm -f /etc/apt/trusted.gpg.d/presage-technologies.gpg
sudo apt update
```

For support: contact [support@presagetech.com](mailto:support@presagetech.com) or [submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues).
