# SmartSpectra SwiftUI macOS Example

This example is a native macOS SwiftUI app that can be opened and built directly in Xcode. It links against the SmartSpectra C++ SDK installed through Homebrew.

The app shows a live camera preview, validation status, breathing metrics, cardio metrics, and trend traces.

## Quick Start

Install SmartSpectra and the required runtime packages through Homebrew, then open `smartspectra_swift_ui.xcodeproj` in Xcode, select the `smartspectra_swift_ui` scheme, and press Run.

Before first run, check the SDK and runtime setup:

```sh
./scripts/check-requirements.sh
```

To apply safe runtime fixes, run:

```sh
./scripts/check-requirements.sh --fix
```

Then select the app target and set Signing & Capabilities:

```text
Team = your Apple Development team
Bundle Identifier = your unique bundle identifier
```

Enter `SMARTSPECTRA_API_KEY` in the app and press Start. On first launch, allow camera access when macOS prompts.

## Requirements

- macOS with Xcode installed.
- SmartSpectra SDK installed through Homebrew.
- Homebrew dependencies used by this SDK build:
  - OpenCV
  - `vulkan-loader`
  - `molten-vk`
- Apple Development signing in Xcode.
- A SmartSpectra API key.

This SDK build requires macOS 14.0 or newer at runtime.

The app must be signed with an Apple Development identity because SmartSpectra stores SDK state in Keychain. An ad-hoc signed app can launch, but Keychain writes fail with `-34018`.

## Xcode Setup

The project uses:

- Xcode Build Settings for the Homebrew prefix, SDK path, include paths, library paths, linker inputs, and deployment target.
- Xcode Signing & Capabilities for the Apple Development team and bundle identifier.
- `swift_ui/Info.plist` for macOS app metadata and camera permission text.
- `smartspectra_swift_ui.entitlements` for Keychain access.

### Build Settings

Select the `smartspectra_swift_ui` project, open Build Settings, and search for these user-defined settings:

```text
HOMEBREW_PREFIX = /opt/homebrew
SMARTSPECTRA_SDK_ROOT = $(HOMEBREW_PREFIX)
```

If Homebrew is installed somewhere else, set `HOMEBREW_PREFIX` to the output of:

```sh
brew --prefix
```

The project derives these paths from those values:

```text
SMARTSPECTRA_LIB_DIR = $(SMARTSPECTRA_SDK_ROOT)/lib
SMARTSPECTRA_INCLUDE_DIR = $(SMARTSPECTRA_SDK_ROOT)/include
SMARTSPECTRA_INTERFACE_INCLUDE_DIR = $(SMARTSPECTRA_SDK_ROOT)/include/smartspectra/interface
OPENCV_INCLUDE_DIR = $(HOMEBREW_PREFIX)/opt/opencv/include/opencv4
OPENCV_LIB_DIR = $(HOMEBREW_PREFIX)/opt/opencv/lib
```

The `Validate Setup` build phase checks the SDK header, SDK library, interface headers, and OpenCV headers before compilation. If Xcode reports that SmartSpectra cannot be found, fix `HOMEBREW_PREFIX` first.

### Signing

Select the `smartspectra_swift_ui` target, open Signing & Capabilities, and set:

```text
Team = your Apple Development team
Bundle Identifier = a unique bundle identifier for your machine or organization
```

The checked-in project intentionally does not hard-code a development team. To find your Team ID:

1. Open `Xcode > Settings > Accounts`.
2. Select your Apple ID.
3. Select the team you want to use.
4. Read the `Team ID` value shown in the team details.

You can also list signing identities from Terminal:

```sh
security find-identity -v -p codesigning
```

Look for the 10-character team ID in parentheses at the end of an `Apple Development` identity.

`smartspectra_swift_ui.entitlements` is already assigned as the app's entitlements file.

## Command-Line Build

From the repository root, provide your Apple Developer Team ID:

```sh
SMARTSPECTRA_DEVELOPMENT_TEAM=<team-id> task cpp:macos-swiftui-example
```

If your Homebrew prefix is not `/opt/homebrew`, pass it explicitly:

```sh
HOMEBREW_PREFIX="$(brew --prefix)" \
SMARTSPECTRA_DEVELOPMENT_TEAM=<team-id> \
task cpp:macos-swiftui-example
```

## Requirements Script

The helper script checks the Homebrew SDK, required model files, Vulkan/MoltenVK setup, SDK graph asset path, and code-signing visibility:

```sh
./scripts/check-requirements.sh
```

`--fix` can:

- install missing `opencv`, `vulkan-loader`, and `molten-vk`
- create `$(HOMEBREW_PREFIX)/share/smartspectra/graph` as a symlink to the SDK graph assets

The graph symlink is needed because `libsmartspectra.dylib` looks for model files under:

```text
$(HOMEBREW_PREFIX)/share/smartspectra/graph/models
```

## Troubleshooting

### `SmartSpectra SDK not found`

Confirm that Homebrew contains the SDK files:

```sh
test -f "$(brew --prefix)/include/smartspectra/smartspectra.h"
test -d "$(brew --prefix)/include/smartspectra/interface"
test -f "$(brew --prefix)/lib/libsmartspectra.dylib"
```

In Xcode, confirm:

```text
HOMEBREW_PREFIX = <output of brew --prefix>
SMARTSPECTRA_SDK_ROOT = $(HOMEBREW_PREFIX)
```

### `Signing Team is not set`

Select the app target, open Signing & Capabilities, and choose your Apple Development team. For command-line builds, pass your Team ID:

```sh
SMARTSPECTRA_DEVELOPMENT_TEAM=<team-id> task cpp:macos-swiftui-example
```

### `Library not loaded: @rpath/libvulkan.1.dylib`

Install the Vulkan loader:

```sh
brew install vulkan-loader
```

or run:

```sh
./scripts/check-requirements.sh --fix
```

### `Vulkan is not available: vkCreateInstance failed with ErrorIncompatibleDriver`

Install MoltenVK:

```sh
brew install molten-vk
```

Then restart the app.

### Missing `.tflite` model files under `$(HOMEBREW_PREFIX)/share/smartspectra`

Run:

```sh
./scripts/check-requirements.sh --fix
```

This links the SDK graph assets into the location expected by this SDK build.

### `Store keychain item 'emd.state.v1' failed: A required entitlement isn't present. (-34018)`

The app was not signed with a real Apple Development profile. In Xcode, check the app target's Signing & Capabilities settings and make sure the Team is set.

Then verify the entitlements include:

```text
com.apple.application-identifier = <team-id>.<bundle-id>
com.apple.developer.team-identifier = <team-id>
keychain-access-groups = <team-id>.*
```

### Metrics do not appear immediately

Keep the subject still and centered in the camera preview until validation reports that recording is OK.
