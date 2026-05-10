---
title: Headless Mode
description: Drive SmartSpectra SDK observable state directly from your own iOS UI — useful for background monitoring or custom interfaces.
---

# Headless Mode (iOS)

The SDK doesn't ship UI. `SmartSpectraSDK.shared` is observable — read its
properties (`metrics`, `validationStatus`, `error`, `imageOutput`,
`processingStatus`) directly from SwiftUI views and SwiftUI auto-tracks
reads. Outside SwiftUI, use `withObservationTracking` and re-arm the
observation after each change. The sample apps include a measurement UI;
your own integration looks however you want.

Use this when you want to:

- Monitor vitals in the background while the app shows other content
- Build a custom measurement UI

## Processing Status

Lifecycle states (same across all platforms):

| Status | Meaning |
| --- | --- |
| **Idle** | Pipeline is not running |
| **Starting** | Pipeline is initializing |
| **Running** | Actively measuring — data is flowing |
| **Stopping** | Teardown in progress, will return to Idle |
| **Error** | Something went wrong |

## Example

Use `SmartSpectraSDK.shared` directly for headless processing:

```swift
import SwiftUI
import SmartSpectra

struct HeadlessExample: View {
    private let sdk = SmartSpectraSDK.shared
    @State private var isMonitoring = false
    @State private var showCameraFeed = false

    init() {
        sdk.config.apiKey = "YOUR_API_KEY"
        sdk.config.cameraPosition = .front
    }

    var body: some View {
        VStack {
            if let metrics = sdk.metrics, metrics.hasBreathing,
               let rate = metrics.breathing.rate.last {
                Text("Breathing: \(Int(rate.value.rounded())) bpm")
            }
            if let metrics = sdk.metrics, metrics.hasCardio,
               let pulse = metrics.cardio.pulseRate.last {
                Text("Pulse: \(Int(pulse.value.rounded())) bpm")
            }
            Text("Status: \(sdk.validationStatus?.hint ?? "")")

            if let error = sdk.error {
                Text(error.message)
                    .foregroundStyle(.red)
            }

            Toggle("Camera Preview", isOn: $showCameraFeed)
                .onChange(of: showCameraFeed) {
                    sdk.config.imageOutputEnabled = showCameraFeed
                }

            if showCameraFeed, let image = sdk.imageOutput {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
            }

            Button(isMonitoring ? "Stop" : "Start") {
                isMonitoring.toggle()
                if isMonitoring {
                    Task { try? await sdk.start() }
                } else {
                    Task { try? await sdk.stop() }
                }
            }
            // Disable the start button when the SDK has an unrecoverable
            // input-unavailable error (e.g. camera permission denied).
            // All other error states either recover on `start()` or
            // return a throwable error that you can surface to the user.
            .disabled(sdk.error?.code == .inputUnavailable && !isMonitoring)
        }
    }
}
```

## Reading Metrics

`sdk.metrics` is the same observable property in headless integrations as
elsewhere — there's no separate "headless" API. See
[iOS Metrics](metrics.md) for the metric request configuration and the
field-by-field reading guide.
