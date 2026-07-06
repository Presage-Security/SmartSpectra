---
title: Headless Testing in CI
description: Smoke-test that the SmartSpectra iOS SDK builds, launches, and initializes on a simulator in CI.
---

# Headless Testing in CI (iOS)

See [Headless Testing in CI](../../docs/headless-testing-in-ci.md) for the
cross-platform overview of what's automatable and why. This page covers the
iOS specifics.

## What's different on iOS

The SDK measures from the live camera and doesn't accept a recorded video
file, and a CI simulator has no camera — so a video-fed test isn't possible.

What you *can* automate on iOS is a **headless build-integration smoke test**:
prove that your integration builds, links, launches, and initializes the SDK
— all with public API, no built-in SDK UI involved.

## What you can automate

Drive `SmartSpectraSDK.shared` directly, the same way you would for any
[headless integration](headless-mode.md), and run it as an XCTest hosted by
your app on the **iOS Simulator**:

1. **Builds and launches** — the test target links your app and the SDK and
   the simulator boots it.
2. **Initializes** — set `sdk.config.apiKey` and call `try await sdk.start()`.
3. **Doesn't hang or crash** — the simulator has no camera, so don't assert on
   a measurement result. `start()` returning at all — whether it succeeds or
   throws a typed `SmartSpectraError` — is the smoke signal that the SDK
   built, launched, and initialized correctly end to end.

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

Keep the check at this smoke level — it confirms the build/launch/init path,
not measurement accuracy (which needs a physical device and a live camera).

## A CI pipeline, in general terms

1. **Expose the API key** as a job secret.
2. **Run the test target headlessly** on a booted iOS Simulator via
   `xcodebuild test`.
3. **Fail the job** if the test target doesn't build or the smoke test fails.

A minimal, provider-neutral sketch (GitHub Actions) — adapt the scheme name
and simulator to your project:

```yaml
name: smartspectra-ios-headless-smoke
on: [push]

jobs:
  headless:
    runs-on: macos-14
    steps:
      - uses: actions/checkout@v4

      - name: Headless build-integration smoke
        env:
          SMARTSPECTRA_API_KEY: ${{ secrets.SMARTSPECTRA_API_KEY }}
        run: |
          xcodebuild test \
            -scheme YourAppSmokeTests \
            -destination 'platform=iOS Simulator,name=iPhone 15' \
            -only-testing:YourAppSmokeTests/HeadlessSmokeTests
```

## Limitations

- **No video-fed measurement.** Unlike the desktop SDKs, iOS has no public
  way to feed a recorded video file in place of the camera, so this pattern
  can only smoke-test the build/launch/init path, not a full measurement.
- **No offline mode.** Like every SmartSpectra SDK, `start()` authenticates
  against the SmartSpectra service, so the runner needs network access.
