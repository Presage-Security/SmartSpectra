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
| Android | Build-integration smoke | No camera required |
| iOS | Build-integration smoke | No camera required |

- **Video-fed measurement** (C++, Node.js) — the desktop SDKs accept a recorded
  video file in place of a live camera, so CI can run a full measurement and
  assert that readings came out. No camera or display is needed.
- **Build-integration smoke** (iOS, Android) — the mobile SDKs measure from the
  live device camera and do not accept a recorded video file, so a full
  video-fed measurement can't run on a camera-less CI machine. What you *can*
  automate is a smoke test: the SDK builds, links, launches headless,
  initializes, and surfaces the expected permission/error states.

## What you'll need

For any of the above:

- **A SmartSpectra API key**, provided to the job as a CI **secret** (never
  hard-code it in the repo). Your platform sample accepts it at startup.
- **Network access** from the CI runner to the SmartSpectra service. Measurement
  authenticates online — there is **no offline mode**, so an air-gapped runner
  can't complete a measurement.

For video-fed measurement, additionally:

- **A short recorded video you supply** — a few seconds of a well-lit, mostly
  still face, framed like a real measurement. Keep the clip in your own test
  assets. See the platform guide for the container/codec each SDK expects.

## The video-fed model

A video-fed headless test is the same shape on C++ and Node.js:

```text
recorded video  ->  SDK file input  ->  metric callbacks  ->  assertions
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
3. **Run the sample headlessly** — feed your recorded video (C++/Node.js), or
   launch the SDK headless (iOS/Android) for the build-integration smoke.
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
- **[Android](../android/docs/headless-testing-in-ci.md)** — headless
  build-integration smoke (video-fed measurement is not available; a live
  camera is required to measure).
- **[iOS](../swift/docs/headless-testing-in-ci.md)** — headless build-integration
  smoke (same limitation as Android).

## Limitations

- **No offline mode.** A measurement authenticates against the SmartSpectra
  service; the CI runner needs network access.
- **Mobile is smoke-only.** The Android and iOS SDKs measure from the live
  camera and do not accept a recorded video file, so a full measurement can't
  be automated on a camera-less CI machine.
