---
title: C++ Device Readiness
description: Check device readiness for the configured metrics and camera, and handle the result.
---

# C++ Device Readiness

> **Important:** SDK metrics are offered for general wellness and informational purposes only. SDK metrics have not been cleared by the FDA and may not be used for medical diagnosis or treatment.

Configure the SDK's authentication, metrics, and camera, then check readiness
before starting a measurement:

```cpp
const auto result = sdk.checkDeviceReadiness();
```

Use `result.verdict` for application decisions and `result.reason` for the
explanation and recommended next step. Keep the returned result if you need it later.

The call blocks until cleanup completes. Run it on a worker thread in UI
applications. To cancel, call `sdk.Stop()` from another thread. `Stop()` requests
cancellation without waiting, and the check returns Inconclusive after cleanup.
Keep the SDK alive until the check returns.

See [Device Readiness](../../docs/device-readiness.md) for
verdict meanings, supported inputs, and result handling.

## Telemetry

Telemetry is on by default. When it is enabled, each check sends its full
diagnostic report to Presage: device and SDK details, requested metrics and camera
settings, performance measurements, and free-text diagnostic messages. The report
never includes video frames or vital signs.

To turn it off, set `enable_telemetry` to `false` on `SmartSpectraConfig`:

```cpp
SmartSpectraConfig config;
config.enable_telemetry = false;
```

See [SDK Telemetry & Privacy](../../docs/telemetry-and-privacy.md) for the full
report contents, storage, and retention.

## When a measurement stops for low frame rate

When the frame rate stays too low to measure reliably during a measurement, the
session ends and the error callback receives
`SmartSpectraErrorCode::kSustainedLowFrameRate` (14) instead of
`SmartSpectraErrorCode::kProcessingFailed`. The message is "SmartSpectra stopped
because the frame rate stayed too low to measure reliably." A frame submitted as
the session ends is rejected with the same code. `retryable` is `false` when the
SDK plays a video file, because the same file stops the same way; it is `true` for
cameras and custom input:
the host may offer a user-initiated fresh start; do not restart automatically.
To check whether a device can sustain measurement before starting, call
`checkDeviceReadiness()`.

The SDK measures frame rate from frame timestamps: the timestamp file when one is
given, otherwise the video's playback position. The interframe delay
(`SetInterframeDelay`) changes playback speed, not the measured frame rate.
