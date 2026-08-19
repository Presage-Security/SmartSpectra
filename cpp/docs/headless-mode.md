---
title: Headless Mode on C++
description: The SmartSpectra C++ SDK is headless by default — wire up metric and frame callbacks for custom integrations.
sidebarTitle: Headless Mode
---

# Headless Mode (C++)

The SDK doesn't ship UI. Register lambdas with `SetOnMetrics`,
`SetOnVideoOutput`, and `SetOnError`; rendering is your code's job. The
C++ sample apps show reference UI implementations.

Use this when you want to:

- Render metrics in your own UI
- Process metrics with no UI at all (logging, server-side, batch)
- Feed your own frames instead of the SDK's built-in camera

## Processing Status

Lifecycle states, reported via `OnProcessingStatusChangedFn` and `GetStatus()`:

| Status | Value | Meaning |
| --- | --- | --- |
| `kUninitialized` | 0 | SDK constructed but not yet initialized |
| `kIdle` | 1 | Pipeline is not running |
| `kStarting` | 2 | Pipeline is initializing |
| `kRunning` | 3 | Actively measuring — data is flowing |
| `kStopping` | 4 | Teardown in progress, will return to `kIdle` |
| `kError` | 5 | Something went wrong |

## Example

```cpp
namespace spectra = presage::smartspectra;

spectra::SmartSpectraConfig config;
config.api_key = "YOUR_API_KEY";
config.requested_metrics = spectra::SmartSpectraConfig::BreathingMetrics();

spectra::SmartSpectra sdk(config);

sdk.SetOnMetrics([](const spectra::Metrics& metrics, int64_t ts) {
    // Process metrics
});

sdk.SetOnVideoOutput([](const spectra::FrameBuffer& frame, int64_t ts) {
    // Optional: render frame in your own UI
});

if (const auto source_error =
        sdk.UseCamera().SetResolution(1280, 720).SetFps(30).Build();
    !source_error.ok()) {
    // Handle setup error
} else if (const auto err = sdk.Start(); !err.ok()) {
    // Handle startup error
}

// ... run until done ...

sdk.Stop();
```

## Custom frame input

For custom frame input instead of the built-in camera:

```cpp
std::shared_ptr<spectra::CustomInput> handle;
if (auto err = sdk.UseCustomInput().Build(handle); !err.ok()) {
    // Handle setup error: err.FullMessage()
}
// Feed frames manually:
// handle->Send(frame, timestamp_us);
// timestamp_us must be strictly monotonic.
```

## Reading Metrics

`SetOnMetrics` fires the same way regardless of frame source. See
[C++ Metrics](metrics.md) for the metric request configuration and the
metric catalog.

## Running Headlessly in CI

Feed a recorded video through `UseFile` instead of a live camera to run an
unattended measurement in a CI pipeline — see
[Headless Testing in CI](../../docs/headless-testing-in-ci.md) for the
cross-platform model and prerequisites (API key, network access, your own
recorded video).

The `minimal_example` sample supports this out of the box:

```bash
./build/samples/minimal_example/minimal_example \
  --api_key=YOUR_API_KEY --input_video_path=/path/to/video.mp4
```

The sample plays the file through the same pipeline as a live camera, prints
metrics as they arrive, and exits when the file finishes — a non-zero exit
code (or no metrics printed) means the run failed. Use a 30–60 second clip
(well-lit, mostly still face) in a widely-supported container/codec such as
MP4 (H.264). A clip of only a few seconds will not produce readings: pulse rate
needs 12 seconds, breathing rate starts at roughly 10 seconds and is not
confident until 30, and HRV starts at roughly 30 seconds and is not confident
until 60.

A provider-neutral CI step (GitHub Actions), after installing or building the
SDK for your platform:

```yaml
- name: Headless measurement smoke
  run: |
    ./build/samples/minimal_example/minimal_example \
      --api_key="${{ secrets.SMARTSPECTRA_API_KEY }}" \
      --input_video_path=./test-assets/face.mp4
```

There is no offline mode — the CI runner needs network access to the
SmartSpectra service to authenticate and run a measurement.
