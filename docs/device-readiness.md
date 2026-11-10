---
title: Device Readiness
description: Check device readiness on C++, Swift, Android, and Node.js and handle the verdict and recommended next step.
---

# Device Readiness

> **Important:** SDK metrics are offered for general wellness and informational purposes only. SDK metrics have not been cleared by the FDA and may not be used for medical diagnosis or treatment.

Device Readiness checks whether a device can support the SDK's configured metrics
and camera. Call `checkDeviceReadiness()` before starting a measurement, using an
SDK instance with authentication, metrics, and camera selection already configured.
Obtain camera permission first where required.

## Run the check

| Platform | Get the result |
| --- | --- |
| C++ | `const auto result = sdk.checkDeviceReadiness();` |
| Swift | `let result = try await sdk.checkDeviceReadiness()` |
| Kotlin | `val result = sdk.checkDeviceReadiness()` |
| Node.js | `const result = await sdk.checkDeviceReadiness();` |

The check uses the current settings and does not start a measurement. Camera
input is required; file and custom-frame inputs return Inconclusive. Wait for any
active measurement or check to finish before running another check.

C++ blocks until cleanup completes. Run the check on a worker thread in UI
applications. To cancel, call `sdk.Stop()` from another thread; `Stop()` requests
cancellation without waiting, and the check returns Inconclusive after cleanup.
Keep the SDK alive until the check returns. On Swift and Kotlin, use task or
coroutine cancellation. Node.js returns a Promise; await it before continuing.

## Handle the result

The completed `DeviceReadinessResult` contains `verdict` and `reason`.
Use `result.verdict` for application decisions; it is not a Boolean:

| Verdict | Meaning |
| --- | --- |
| Ready | The device met the performance requirements for the configured metrics. |
| Limited | Performance was below requirements. Reduce other device activity or the requested metrics, then check again. |
| Blocked | Authentication, permission, configuration, or another prerequisite prevented the check. Follow the result's reason before retrying. |
| Inconclusive | Readiness could not be determined. Follow the result's reason and retry. |

A check stopped for sustained low frame rate can return Limited when the observed
performance is below requirements. A network failure during the check returns
Inconclusive; restore network access to the SmartSpectra service and try again.

Use `result.reason` for the explanation and recommended next step. Display it as
text; use `result.verdict` for application decisions. Keep the returned result if
you need it later. No separate getter or nullable-result check is needed.

For example, in Node.js, compare the lowercase verdict string explicitly:

```javascript
const result = await sdk.checkDeviceReadiness();
console.log(result.reason);
if (result.verdict === 'ready') {
    // Offer to start a measurement.
}
```

Every verdict string is truthy, including `'blocked'`.

## Telemetry

Telemetry is on by default. When it is enabled, each check sends its full
diagnostic report to Presage, not just the verdict and reason. The report includes:

- Device model, OS version, architecture, and SDK version.
- Requested metrics and camera settings.
- Performance measurements, thresholds, and timing.
- Diagnostic messages. These are free text and can include technical context such
  as file paths or platform errors.

The report never includes video frames or vital signs. Reporting is best-effort
and does not change or delay the verdict.

To stop sending readiness reports and session telemetry, disable telemetry in the
SDK configuration:

| Platform | Setting |
| --- | --- |
| C++ | `config.enable_telemetry = false;` |
| Swift | `sdk.config.enableTelemetry = false` |
| Kotlin | `sdk.config.enableTelemetry = false` |
| Node.js | `new SmartSpectraSDK({ ..., enableTelemetry: false })` |

See [SDK Telemetry & Privacy](telemetry-and-privacy.md) for the full report
contents, storage, and retention.
