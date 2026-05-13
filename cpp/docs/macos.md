---
title: C++ on macOS
description: Install the SmartSpectra C++ SDK and run the SwiftUI macOS sample app.
---

# SmartSpectra C++ Quickstart — macOS

## Supported Platforms

| Platform | Status | Notes |
| -------- | ------ | ----- |
| macOS Apple Silicon (14.0+) | Supported | Homebrew package available |
| macOS Intel | Not supported | — |

For platforms not listed above, contact
[support@presagetech.com](mailto:support@presagetech.com) if you have a
specific need.

## Installation

### Prerequisites

- **Xcode** with Command Line Tools (provides a C++17 toolchain and the Swift compiler)
- **Homebrew**
- **API key** from [physiology.presagetech.com](https://physiology.presagetech.com)
- **Apple Development signing identity** — required for SDK startup on macOS

Install Xcode Command Line Tools if you do not have them:

```bash
xcode-select --install
```

### Add the SDK

The Homebrew formula installs the self-contained SmartSpectra SDK and exposes
its CMake package metadata. You do not need to install OpenCV, protobuf, or
other SDK runtime libraries separately — the formula declares them as
dependencies and Homebrew installs them automatically.

```bash
brew tap presage/smartspectra https://github.com/Presage-Security/homebrew-smartspectra
brew install presage/smartspectra/smartspectra
```

For the release-candidate channel, install `smartspectra-rc` instead:

```bash
brew install presage/smartspectra/smartspectra-rc
```

### Verify the install

After Homebrew finishes, confirm the SDK is wired up before opening Xcode:

```bash
pkg-config --modversion SmartSpectra
ls /opt/homebrew/lib/cmake/SmartSpectra
```

`pkg-config` should print the installed SDK version. The `ls` should list
the `SmartSpectra` CMake config directory the formula installed (this is
what `find_package(SmartSpectra CONFIG REQUIRED)` in your own CMake project
will pick up — see "Installed Paths" below). If either fails, re-run
`brew install` and check `brew doctor`.

### Permissions

SmartSpectra's default macOS builds require a signed host app with the
keychain entitlements needed for the SDK. This
applies to SDK startup in general, including file-based processing.

The SwiftUI sample below ships with the required entitlements already
configured. For your own integrations, mirror the sample's
`smartspectra_swift_ui.entitlements` file
([stable](https://github.com/Presage-Security/SmartSpectra/blob/main/cpp/samples/macos_swiftui_example/smartspectra_swift_ui.entitlements)
| [rc](https://github.com/Presage-Security/SmartSpectra/blob/rc/cpp/samples/macos_swiftui_example/smartspectra_swift_ui.entitlements)).

## Example

The recommended starting point is the **SmartSpectra SwiftUI macOS sample**,
a native SwiftUI app that opens directly in Xcode and links against the
Homebrew-installed SDK. It demonstrates camera capture, validation status,
breathing/cardio metrics, and trend traces end to end.

Source: [Presage-Security/SmartSpectra — `cpp/samples/macos_swiftui_example`](https://github.com/Presage-Security/SmartSpectra/tree/main/cpp/samples/macos_swiftui_example)

### Clone and verify the environment

Match the branch to the Homebrew formula you installed: `main` for the
stable `smartspectra` formula, `rc` for the `smartspectra-rc` formula.

Stable:

```bash
git clone --branch main https://github.com/Presage-Security/SmartSpectra.git
cd SmartSpectra/cpp/samples/macos_swiftui_example
./scripts/check-requirements.sh
```

Release candidate:

```bash
git clone --branch rc https://github.com/Presage-Security/SmartSpectra.git
cd SmartSpectra/cpp/samples/macos_swiftui_example
./scripts/check-requirements.sh
```

The script checks the Homebrew SDK, required model files, Vulkan/MoltenVK
setup, the SDK graph asset path, and code-signing visibility. To apply safe
runtime fixes (install missing Homebrew packages), run:

```bash
./scripts/check-requirements.sh --fix
```

### Open in Xcode

Open `smartspectra_swift_ui.xcodeproj`, select the `smartspectra_swift_ui`
scheme, and configure signing on the app target under **Signing &
Capabilities**:

```text
Team             = your Apple Development team
Bundle Identifier = a unique identifier for your machine or organization
```

To find your Team ID, open `Xcode > Settings > Accounts`, select your Apple ID
and team, and read the `Team ID` value. From the terminal:

```bash
security find-identity -v -p codesigning
```

The 10-character ID in parentheses at the end of an `Apple Development`
identity is your Team ID.

Press **Run**. Enter your `SMARTSPECTRA_API_KEY` in the app and press **Start**.
On first launch, allow camera access when macOS prompts.

### Configuration

If your Homebrew prefix is not `/opt/homebrew`, open the project's Build
Settings and set:

```text
HOMEBREW_PREFIX       = output of `brew --prefix`
SMARTSPECTRA_SDK_ROOT = $(HOMEBREW_PREFIX)
```

The project derives include and library paths from those two variables. The
`Validate Setup` build phase checks the SDK header, library, interface
headers, and OpenCV headers before compilation, so a wrong prefix surfaces
early.

## Additional Details

### Installed Paths

On Apple Silicon Homebrew installs, the default paths are:

- **Headers**: `/opt/homebrew/include/smartspectra/`
- **Libraries**: `/opt/homebrew/lib/`
- **CMake config**: `/opt/homebrew/lib/cmake/SmartSpectra/`
- **pkg-config**: `/opt/homebrew/lib/pkgconfig/SmartSpectra.pc`

Consumer code includes SmartSpectra headers as:

```cpp
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/messages/metrics.h>
```

When linking from your own CMake project:

```cmake
find_package(SmartSpectra CONFIG REQUIRED)
target_link_libraries(my_app PRIVATE SmartSpectra::SDK)
```

### Release-Candidate Builds

Release-candidate builds are published as a separate Homebrew formula,
`smartspectra-rc`, served by the same tap as the stable formula. Stable users
see no change; RC opt-in is additive.

```bash
brew install presage/smartspectra/smartspectra-rc
```

Release candidates do not automatically migrate to the stable formula. After a
stable release ships, uninstall `smartspectra-rc` and install `smartspectra`
to return to the stable channel:

```bash
brew uninstall presage/smartspectra/smartspectra-rc
brew install presage/smartspectra/smartspectra
```

## Next Steps

- [Configure which metrics to compute](metrics.md)
- [Run headless without video output](headless-mode.md)
- [Migration Guide](migration-guide.md) for upgrading from older SDK versions

## Documentation

API reference available at [C++ API Reference](https://smartspectra.presagetech.com/docs/cpp/api-reference).

## Troubleshooting

### Runtime libraries or model files missing

If you see errors about a missing Vulkan loader, MoltenVK driver, or
`.tflite` model files, run the sample's diagnostic script:

```bash
./scripts/check-requirements.sh --fix
```

It verifies the Homebrew SDK install, repairs the graph asset path, and
reinstalls any missing runtime packages.

### Metrics do not appear immediately

Keep the subject still and centered in the camera preview until validation
reports that recording is OK.

For support: contact [support@presagetech.com](mailto:support@presagetech.com)
or [submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues).
