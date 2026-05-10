---
title: Headless Mode
description: The SmartSpectra C++ SDK is headless by default — wire up metric and frame callbacks for custom integrations.
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

Lifecycle states (same across all platforms):

| Status | Meaning |
| --- | --- |
| **Idle** | Pipeline is not running |
| **Starting** | Pipeline is initializing |
| **Running** | Actively measuring — data is flowing |
| **Stopping** | Teardown in progress, will return to Idle |
| **Error** | Something went wrong |

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
