---
title: Headless Mode on Swift
description: Use SmartSpectra with your own iOS UI, AVFoundation camera, CVPixelBuffer, or CMSampleBuffer video input.
sidebarTitle: Headless Mode
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

## Use Your Own Camera or Video Source

Use this for a camera shared with another feature, or for frames decoded by your
app. The demo app's [Video Testing sample](https://github.com/Presage-Security/SmartSpectra/blob/main/swift/samples/demo-app/VideoInput/VideoTestingView.swift)
shows public frame submission from `AVAssetReader`, including clip orientation,
presentation timestamps, rejected frames, cancellation, and source cleanup.

Select custom input while the SDK is stopped. Await `start()` before sending
frames; you do not need to wait for `processingStatus == .running`.

```swift
import AVFoundation
import SmartSpectra

final class CameraFrames: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    private let input: CustomInput

    init(input: CustomInput) {
        self.input = input
    }

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        switch input.sendFrame(sampleBuffer) {
        case .accepted:
            break
        case .rejected(let error):
            // Handle invalid state, unsupported pixels, or timestamp errors.
            print("Frame rejected: \(error.code): \(error.message)")
        }
    }
}

@MainActor
func connectCameraOutput(_ output: AVCaptureVideoDataOutput) async throws -> CameraFrames {
    let sdk = SmartSpectraSDK.shared
    sdk.config.apiKey = "YOUR_API_KEY"
    let input = try sdk.useCustomInput(frameTransform: .none)
    try await sdk.start()

    let receiver = CameraFrames(input: input)
    output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
    output.alwaysDiscardsLateVideoFrames = true
    output.setSampleBufferDelegate(receiver, queue: DispatchQueue(label: "camera.frames"))
    return receiver // Retain this receiver for as long as capture runs.
}
```

Configure and attach the output to your app's capture session before calling
`connectCameraOutput`. Start capture afterward, on your capture session's worker
queue. To finish, stop capture and detach the delegate, drain the delegate queue,
then await `sdk.stop()` before releasing the receiver. Call `try sdk.useCamera()`
while stopped if the next measurement should use SDK capture. Serialize these
lifecycle operations and handle thrown errors; frame-error callbacks run on the
delegate queue, so dispatch UI updates to the main actor.

The host app owns its capture session, camera permissions, preview, and camera
settings. Configure the capture connection to deliver upright pixels, or select
a `FrameTransform` when calling `useCustomInput()`. Avoid applying the same
rotation twice. `sdk.config.cameraPosition` does not rotate or mirror custom
frames. Custom input itself requires no SDK camera permission or capture session;
normal SDK authentication is still required.

For a decoded video or another source, submit a pixel buffer with its timestamp:

```swift
let result = input.sendFrame(pixelBuffer, timestampUs: presentationTimestampUs)
```

| Input | Supported layout |
| --- | --- |
| `CVPixelBuffer` | `kCVPixelFormatType_32BGRA`, including padded rows |
| `CVPixelBuffer` | 8-bit bi-planar NV12, full or video range, even dimensions |
| `CMSampleBuffer` | A ready, uncompressed video sample containing one of these pixel formats |

The sample-buffer overload uses the presentation timestamp. Explicit timestamps
are nonnegative, strictly increasing microseconds below `Int64.max - 2`, on one
monotonic timeline per run. Do not use wall-clock arrival times. Duplicate or
backward timestamps return `.nonMonotonicTimestamp`; gaps over two seconds
return `.timestampGap`. Stop and start again after an interruption to begin a
fresh timeline. Invalid or unsupported pixels return `.frameConversionFailed`.

Submit from a serial worker queue. Keep pixels unchanged until `sendFrame`
returns; afterward you can release or reuse them. `.accepted` confirms input
consumption, not completed measurement output. Observe SDK metrics separately.

The handle survives `stop()`/`start()` and `reset()`. Reset stops processing and
clears measurement output while preserving configuration and the selected input.
Calling `useCamera()` or selecting another custom input invalidates older
handles. Source selection is stopped-only and throws `.invalidState` during
startup, measurement, or teardown. Stop the host's frame delivery before
switching sources; the SDK does not stop the host's camera.

## Processing Status

Lifecycle states:

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
