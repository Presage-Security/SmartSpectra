---
title: Headless Testing in CI on iOS
description: Run a full SmartSpectra measurement on the iOS Simulator in CI by feeding a recorded video, or smoke-test that the SDK builds and initializes.
sidebarTitle: Headless Testing in CI
---

# Headless Testing in CI (iOS)

See [Headless Testing in CI](../../docs/headless-testing-in-ci.md) for the
cross-platform overview of what's automatable and why. This page covers the
iOS specifics.

## What's different on iOS

The SDK normally measures from the live camera, but it also ships a
**testing-only video-input API**: point it at a recorded video file and it
plays the clip through the same pipeline as a live camera. A CI simulator has
no camera — with video input, that no longer matters, so CI can run a **full
video-fed measurement** in an XCTest.

The API is gated behind an SPI group so it can't leak into production code by
accident: it is only visible to targets that opt in with
`@_spi(Testing) import SmartSpectra`.

```swift
@_spi(Testing) import SmartSpectra

sdk.setVideoInput(path: path)        // .mov, .mp4, .qt
sdk.setVideoTimestampInput(path: ts) // optional: one ms value per line
sdk.setVideoInputEnabled(true)       // camera off, video in; toggleable
```

While video input is enabled the SDK does not open the camera, so the test
needs no camera hardware and no camera permission.

Two levels of CI coverage, pick per test:

1. **[Video-fed measurement](#option-1-the-video-fed-test)** — a full
   measurement from a recorded clip, asserting that real readings came out.
2. **[Build-integration smoke](#option-2-the-build-integration-smoke)** — no
   clip needed; proves the SDK builds, launches, and initializes.

## Option 1: The video-fed test

Drive `SmartSpectraSDK.shared` directly, the same way you would for any
[headless integration](headless-mode.md), from an XCTest hosted by your app
on the **iOS Simulator**. Feed the clip, poll the observable `metrics`
property, and assert that real readings appeared — a pulse rate and a
breathing rate — not their exact values.

```swift
import XCTest
@_spi(Testing) import SmartSpectra

final class VideoMeasurementTests: XCTestCase {
    @MainActor
    func testMeasuresFromRecordedVideo() async throws {
        let sdk = SmartSpectraSDK.shared
        sdk.config.apiKey = ProcessInfo.processInfo.environment["SMARTSPECTRA_API_KEY"] ?? ""
        // The default request is breathing-only; ask for cardio too so a
        // pulse rate can appear. See the metrics guide.
        sdk.config.requestedMetrics =
            SmartSpectraConfig.breathingMetrics + SmartSpectraConfig.cardioMetrics

        // A short clip of a well-lit, mostly still face, bundled with the
        // test target.
        let videoURL = try XCTUnwrap(
            Bundle(for: Self.self).url(forResource: "face", withExtension: "mov"),
            "face.mov missing from the test bundle"
        )
        sdk.setVideoInput(path: videoURL.path)
        sdk.setVideoInputEnabled(true)
        defer { sdk.setVideoInputEnabled(false) }

        try await sdk.start()

        var sawPulse = false
        var sawBreathing = false
        let deadline = Date().addingTimeInterval(120)
        while Date() < deadline, !(sawPulse && sawBreathing) {
            if let metrics = sdk.metrics {
                if metrics.hasCardio,
                   metrics.cardio.pulseRate.contains(where: { $0.value > 0 }) {
                    sawPulse = true
                }
                if metrics.hasBreathing,
                   metrics.breathing.rate.contains(where: { $0.value > 0 }) {
                    sawBreathing = true
                }
            }
            try await Task.sleep(for: .milliseconds(250))
        }
        try await sdk.stop()

        XCTAssertTrue(sawPulse, "no pulse reading came out of the recorded clip")
        XCTAssertTrue(sawBreathing, "no breathing reading came out of the recorded clip")
    }
}
```

## Option 2: The build-integration smoke

If you don't have a recorded clip yet (or want a faster job on every push),
skip the video calls entirely and keep the check at smoke level — no SPI
import needed:

```swift
import XCTest
import SmartSpectra

final class HeadlessSmokeTests: XCTestCase {
    @MainActor
    func testSDKInitializesHeadless() async throws {
        let sdk = SmartSpectraSDK.shared
        sdk.config.apiKey = ProcessInfo.processInfo.environment["SMARTSPECTRA_API_KEY"] ?? ""

        do {
            try await sdk.start()
            try await sdk.stop()
        } catch let error as SmartSpectraError {
            print("SmartSpectra reported: \(error.message)")
        }
    }
}
```

The simulator has no camera, so don't assert on a measurement result here:
`start()` returning at all — whether it succeeds or throws a typed
`SmartSpectraError` — is the smoke signal that the SDK built, launched, and
initialized correctly end to end.

## The recorded video

Supply your own short clip and keep it in your test assets:

- Around **30–60 seconds** of a **well-lit, mostly still face**, framed like
  a real measurement — long enough for the pipeline to compute rates (a
  measurement runs about 30 seconds); a clip of only a few seconds won't
  produce readings.
- Container/codec: `.mov`, `.mp4`, or `.qt`; H.264 is a safe choice.
- Record in **standard (video/limited) range** — full-range clips can be
  rejected by the brightness validation before a measurement starts.
- Frame timing comes from the clip itself. If your clip needs an external
  time base, supply a sidecar file via `setVideoTimestampInput(path:)` with
  one millisecond timestamp per line, one line per frame.

See [iOS Metrics](metrics.md) for which metrics to request and how to read
them.

## A CI pipeline, in general terms

1. **Expose the API key** as a job secret.
2. **Run the test target headlessly** on a booted iOS Simulator via
   `xcodebuild test`.
3. **Fail the job** if the test target doesn't build or the test fails.

A minimal, provider-neutral sketch (GitHub Actions) — adapt the scheme name
and simulator to your project:

```yaml
name: smartspectra-ios-headless-video
on: [push]

jobs:
  headless:
    runs-on: macos-14
    steps:
      - uses: actions/checkout@v4

      - name: Video-fed measurement test
        env:
          SMARTSPECTRA_API_KEY: ${{ secrets.SMARTSPECTRA_API_KEY }}
        run: |
          xcodebuild test \
            -scheme YourAppVideoTests \
            -destination 'platform=iOS Simulator,name=iPhone 15' \
            -only-testing:YourAppVideoTests/VideoMeasurementTests
```

## Limitations

- **Testing only.** The video-input API is SPI-gated for a reason: keep
  `@_spi(Testing)` imports out of production targets. The API may change
  without a migration path.
- **No offline mode.** Like every SmartSpectra SDK, a measurement
  authenticates against the SmartSpectra service, so the runner needs
  network access.
- **iOS 26 simulator decoder issue.** Video decoding on the iOS 26 simulator
  can fail with a `-12900` decoder error; run video-fed tests on an iOS 17
  or 18 simulator.
- **Smoke, not accuracy.** A recorded-clip run confirms the integration and
  model pipeline end to end; it is not an accuracy benchmark.
