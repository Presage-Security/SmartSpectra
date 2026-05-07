---
title: Swift SDK
description: Get started with the SmartSpectra Swift SDK for iOS — iOS 17+.
---

## Supported Platforms

| Phone              | Notes                              |
| ------------------ | ---------------------------------- |
| iPhone XS or newer | Tested on iPhone SE 3, 16, 17      |

## Installation

### Prerequisites

- **iOS 17.0 or later (Tested on iPhone SE 3, 16, 17)**
- **Xcode 16.0 or later**
- **Physical device** for live camera measurements
- **API Key** from [physiology.presagetech.com](https://physiology.presagetech.com) — see [Authentication](../authentication.md)

1. In Xcode, go to **File → Add Package Dependencies...**
2. Enter: `https://github.com/Presage-Security/SmartSpectra-Swift/`
3. Select **Branch → main**  
**NOTE:** For production releases, use the default `main` branch or select a stable version such as `3.0.0`.
The `main` branch is updated only for final public releases.
For release candidates and other prereleases, select the `rc` branch. The `rc` branch tracks the
latest prerelease package manifest, for example `3.0.0-rc.14`. For reproducible prerelease builds,
pin the exact prerelease version when your package manager supports it.
4. Add to your target

### Permissions

In Xcode:

1. Select your app target.
2. Open the `Info` tab.
3. Add a new row for `Privacy - Camera Usage Description`.
4. Set the value to `This app needs camera access to measure vitals.`

If missing, the SDK fails gracefully with a clear error explaining what to add.

## Example

Use the following setup and quick-start view to run a basic SmartSpectra integration. You can use the provided SwiftUI for a complete screening flow, or build your own interface around the SDK's observable state and metrics.

### Configuration

```swift
let sdk = SmartSpectraSDK.shared

sdk.config.apiKey = "YOUR_API_KEY"
sdk.config.cameraPosition = .front
sdk.config.imageOutputEnabled = true
sdk.config.requestedMetrics = SmartSpectraConfig.breathingMetrics + SmartSpectraConfig.cardioMetrics
```

### Authentication

- **API Key**: Set `sdk.config.apiKey = "YOUR_KEY"`
- **OAuth**: Place `PresageService-Info.plist` in your app's root directory. No code needed — OAuth overrides the API key automatically.

### Quick Start

```swift
import SwiftUI
import SmartSpectra

struct ContentView: View {
    private let sdk = SmartSpectraSDK.shared

    init() {
        sdk.config.apiKey = "YOUR_API_KEY"
    }

    var body: some View {
        VStack {
            if let image = sdk.imageOutput {
                Image(uiImage: image).resizable().aspectRatio(contentMode: .fit)
            }
            Text("Status: \(String(describing: sdk.processingStatus))")
            Button(sdk.processingStatus == .running ? "Stop" : "Start") {
                Task {
                    if sdk.processingStatus == .running {
                        try? await sdk.stop()
                    } else {
                        try? await sdk.start()
                    }
                }
            }
        }
    }
}
```

The SDK ships only the data and lifecycle plane — no SwiftUI views. For a complete screening flow (capture surface, live vitals plot, onboarding, terms-of-service prompts, status overlays), copy the reference implementation from [`samples/demo-app/UI/`](https://github.com/Presage-Security/SmartSpectra/tree/rc/swift/samples/demo-app/UI) into your project. For a UIKit example that builds the UI from scratch, see [`samples/uikit-sample/`](https://github.com/Presage-Security/SmartSpectra/tree/rc/swift/samples/uikit-sample).

## Additional Details

### Runtime State

`SmartSpectraSDK.shared` publishes the current SDK state directly:

- `metrics` for live vitals data
- `imageOutput` for optional camera preview frames
- `processingStatus` for lifecycle updates
- `validationStatus` for readiness hints such as lighting or face position
- `error` for typed runtime failures
- `insight` for responses returned from `requestInsight(_:)`

### UIKit

UIKit apps can drive the SDK directly through its observable state. Configure the shared SDK, observe `imageOutput`, `metrics`, and `processingStatus`, and call `try await sdk.start()` / `try await sdk.stop()` from a `@MainActor` context. See [`samples/uikit-sample/`](https://github.com/Presage-Security/SmartSpectra/tree/rc/swift/samples/uikit-sample) for an example that builds the full UI in UIKit.

### Device Orientation

If you copy the sample screening UI, portrait orientation is recommended.
Remove "Landscape Left," "Landscape Right," and "Portrait Upside Down" from
your supported interface orientations unless your app provides its own
landscape layout.

### Next Steps

- [Use case examples](use-case-examples.md)
- [Configure which metrics to compute](metrics.md)
- [Run headless without the built-in UI](../headless-mode.md)
- [Migration Guide](migration-guide.md) for upgrading from older SDK versions

### Documentation

API reference available at [Swift API Reference](api-reference.md), or build locally via **Product → Build Documentation** in Xcode.

## Troubleshooting

If you're upgrading an older Swift integration, start with the [iOS Migration Guide](migration-guide.md).

For installation, setup, and runtime issues, see the [iOS Troubleshooting Guide](troubleshooting.md).

For support: contact [support@presagetech.com](mailto:support@presagetech.com) or [submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues).
