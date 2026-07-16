---
title: Headless Testing in CI
description: Run a SmartSpectra sample headlessly in a CI pipeline — feed a recorded video for automated measurement, or smoke-test that the SDK builds and starts.
---

# Headless Testing in CI

The SmartSpectra SDKs are **headless** — they ship no built-in UI, so your
application (or a test harness) drives the SDK and reads results from
callbacks. That same property lets you exercise the SDK **unattended in a CI
pipeline**: on every commit, prove that your integration still builds, starts,
and produces readings, without a person holding a phone in front of a camera.

This page is the cross-platform overview. Each SDK has its own guide with the
exact sample, commands, and flags — see [Per-platform guides](#per-platform-guides).

## Two ways to test headlessly

What you can automate depends on how the platform accepts input:

| Platform | Mode | Input in CI |
| --- | --- | --- |
| C++ (Linux/macOS/Windows) | Video-fed measurement | A recorded video file you supply |
| Node.js | Video-fed measurement | A recorded video file you supply |
| Android | Video-fed measurement (testing-only opt-in API) | A recorded video file you supply |
| iOS | Video-fed measurement (testing-only opt-in API) | A recorded video file you supply |

- **Video-fed measurement** — every SDK can run a full measurement from a
  recorded video in place of a live camera, so CI can assert that readings
  came out. No camera or display is needed. On desktop (C++, Node.js) the
  file input is regular public API; on mobile (iOS, Android) it is a
  **testing-only API behind an explicit opt-in** (`@_spi(Testing)` on iOS,
  `@OptIn(SmartSpectraTestingApi::class)` on Android) so it can't leak into
  production code.
- **Build-integration smoke** — the lighter fallback on any platform when you
  don't have a recorded clip: prove the SDK builds, links, launches headless,
  initializes, and surfaces the expected permission/error states.

## What you'll need

For any of the above:

- **A SmartSpectra API key**, provided to the job as a CI **secret** (never
  hard-code it in the repo). Your platform sample accepts it at startup.
- **Network access** from the CI runner to the SmartSpectra service. Measurement
  authenticates online — there is **no offline mode**, so an air-gapped runner
  can't complete a measurement.

For video-fed measurement, additionally:

- **A short recorded video you supply** — around 30–60 seconds of a well-lit,
  mostly still face, framed like a real measurement (long enough for rates to
  compute; a few seconds isn't). Keep the clip in your own test assets. See
  the platform guide for the container/codec each SDK expects.

## The video-fed model

A video-fed headless test is the same shape on every platform:

```text
recorded video  ->  SDK video input  ->  metric callbacks  ->  assertions
        (no camera, no display)
```

Feed the file, let the SDK run its normal pipeline, and assert on what the
callbacks emit. Keep the assertion **smoke-level**: check that the SDK produced
real readings (for example, a pulse rate and a breathing rate appeared), rather
than checking exact values. A smoke test confirms the integration and the model
pipeline are wired up end-to-end; it is not an accuracy benchmark.

## A CI pipeline, in general terms

Whatever CI system you use, the shape is the same:

1. **Expose the API key** as a job secret.
2. **Install or build** the SmartSpectra SDK for the platform (follow the
   platform's install guide).
3. **Run the sample headlessly** — feed your recorded video through the
   platform's video input (a headless sample on desktop, an instrumented /
   XCTest run on mobile).
4. **Fail the job** if the run crashed or didn't produce the expected output.

A minimal, provider-neutral sketch (GitHub Actions, video-fed) — adapt the
install and run steps to your platform's guide:

```yaml
name: smartspectra-headless-smoke
on: [push]

jobs:
  headless:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      # Install the SmartSpectra SDK for your platform (see the platform guide).
      - name: Install SmartSpectra
        run: echo "follow the platform install guide"

      # Run a headless sample against a recorded video you keep in the repo.
      # The sample exits non-zero (failing the job) if it can't measure.
      - name: Headless measurement smoke
        run: |
          ./your_smartspectra_sample \
            --api_key="${{ secrets.SMARTSPECTRA_API_KEY }}" \
            --input_video_path=./test-assets/face.mp4
```

## Per-platform guides

Bring the model above; each guide fills in the concrete sample, flags, and CI
steps for its platform (see the sidebar):

- **[C++](../cpp/docs/headless-mode.md#running-headlessly-in-ci)** —
  headless sample with recorded-video input on Linux, macOS, and Windows.
- **[Node.js](../nodejs/docs/headless-testing-in-ci.md)** — headless sample
  that plays a recorded video through the SDK.
- **[Android](../android/docs/headless-testing-in-ci.md)** — video-fed
  measurement as an instrumented test on an emulator, via the opt-in
  `SmartSpectraTestingApi` frame-feed.
- **[iOS](../swift/docs/headless-testing-in-ci.md)** — video-fed measurement
  as an XCTest on the iOS Simulator, via the `@_spi(Testing)` video input.

## Limitations

- **No offline mode.** A measurement authenticates against the SmartSpectra
  service; the CI runner needs network access.
- **Mobile video input is testing-only.** The Android and iOS video APIs sit
  behind explicit opt-ins; keep them out of production code paths. In
  production, mobile measures from the live device camera.
