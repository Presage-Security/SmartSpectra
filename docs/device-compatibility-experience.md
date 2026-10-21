---
title: Device Compatibility Results and Recovery
description: Check the configured SDK on C++, Swift, Android, and Node.js and retain its verdict, actionable reason, and diagnostic JSON.
---

# Device Compatibility Results and Recovery

> **Important:** SDK metrics are offered for general wellness and informational purposes only. SDK metrics have not been cleared by the FDA and may not be used for medical diagnosis or treatment.

Device Compatibility is an optional SDK operation. It uses the SDK's existing
authentication, selected metrics, and camera selection. It does not change those
settings or automatically start normal measurement. There is no separate service,
configuration argument, or default “test everything” mode. An unset or empty metric
selection uses the SDK's normal default metrics for both measurement and compatibility.

## Run the check

Use your already-configured SDK instance:

| Platform | Get the verdict |
| --- | --- |
| C++ | `const auto verdict = sdk.isDeviceCompatible();` |
| Swift | `let verdict = try await sdk.isDeviceCompatible()` |
| Kotlin | `val verdict = sdk.isDeviceCompatible()` |
| Node.js | `const verdict = await sdk.isDeviceCompatible();` |

The return value is a four-way verdict, **not a Boolean**. Compare it explicitly.
In JavaScript, every verdict string is truthy, including `'blocked'`:

```javascript
const verdict = await sdk.isDeviceCompatible();
if (verdict === 'ready') {
    // The configured workload met the compatibility requirements.
}
```

Obtain camera permission first where required. The check opens the selected
camera and replaces captured pixels with bundled reference imagery. It assesses
processing capacity for the configured metrics, not measurement accuracy or
performance on a particular person's face.

Stable checks can finish after about 20 seconds of observation; otherwise they
continue to about 30 seconds. Setup and cleanup add time. Timing and performance
criteria are controlled by the SDK, with no public duration, FPS, policy, or test-image overrides.
File/custom-frame sources currently return Inconclusive with an explanation rather
than silently testing a different input.

### C++ setup

```cpp
#include <smartspectra/smartspectra.h>

void CheckDevice() {
    using namespace presage::smartspectra;
    SmartSpectraConfig config;
    config.api_key = "YOUR_API_KEY";
    config.requested_metrics = {MetricType::CHEST_BREATHING, MetricType::EDA_TRACE};
    SmartSpectra sdk(config);
    const auto verdict = sdk.isDeviceCompatible();
}
```

C++ blocks until cleanup completes. Run it on a worker thread in a UI application
and retain the SDK until that worker finishes. To request cancellation from another
thread, call `sdk.cancelDeviceCompatibility()`, then wait for the worker to return
before destroying the SDK. Cancellation requests do not bypass cleanup.

### Swift / iOS setup

```swift
import SmartSpectra

@MainActor
func checkDevice() async throws {
    let sdk = SmartSpectraSDK.shared
    sdk.config.apiKey = "YOUR_API_KEY"
    sdk.config.requestedMetrics = SmartSpectraConfig.breathingMetrics
    let verdict = try await sdk.isDeviceCompatible()
}
```

### Android setup

```kotlin
import com.presagetech.smartspectra.SmartSpectraSdk

suspend fun checkDevice(context: android.content.Context) {
    val sdk = SmartSpectraSdk.initialize(context.applicationContext)
    sdk.config.apiKey = "YOUR_API_KEY"
    val verdict = sdk.isDeviceCompatible()
}
```

Use `SmartSpectraSdk.shared` if already initialized. Call from a lifecycle-owned
coroutine. Swift and Kotlin suspend while the check runs; do not add a blocking wait
on the UI thread. Task/coroutine cancellation ends the check and awaits cleanup.

### Node.js setup

```javascript
const { SmartSpectraSDK, MetricType } = require('@smartspectra/node-sdk');

async function checkDevice() {
    const sdk = new SmartSpectraSDK({
        apiKey: process.env.PHYSIOLOGY_API_KEY,
        requestedMetrics: [MetricType.CHEST_BREATHING],
    });
    sdk.useCamera();
    try {
        return await sdk.isDeviceCompatible();
    } finally {
        await sdk.destroy();
    }
}
```

## Retrieve the full result

The check returns the verdict directly. To retrieve the explanation and diagnostic
JSON without running another check, call `sdk.getDeviceCompatibilityResult()`.

| Platform | Saved result |
| --- | --- |
| C++ | `std::optional<DeviceCompatibilityResult>` |
| Swift | `DeviceCompatibilityResult?` |
| Kotlin | `DeviceCompatibilityResult?` |
| Node.js | `DeviceCompatibilityResult` or `undefined` |

The getter is empty before the first check and while a new check runs. Starting a
new check clears the previous result; cancellation or an exception leaves it empty.
A completed check stores its result for every verdict, including Blocked and
Inconclusive. A concurrent rejected call returns Inconclusive without overwriting
the active check's result. Keep a copy yourself if you need earlier results.

`DeviceCompatibilityResult` still contains:

| Field | Meaning |
| --- | --- |
| `verdict` | Ready, Limited, Blocked, or Inconclusive; Node uses lowercase strings. |
| `reason` | Human-readable explanation of the outcome and the recommended next action. |
| `diagnostics` | JSON string containing the detailed native report for troubleshooting. |

Use the verdict for application decisions. Display the reason as text; do not
parse its wording for control flow. Results own their strings and remain usable
after cleanup and SDK destruction.

| Verdict | Meaning |
| --- | --- |
| Ready | The configured workload met its performance requirements. |
| Limited | Sufficient evidence was collected, but performance was below a required threshold. |
| Blocked | Authentication, permission, configuration, or another prerequisite prevented qualification. |
| Inconclusive | The check could not establish compatibility, including busy SDK state, incomplete evidence, or an unsupported selection/input. |

Borderline performance alone does not cause Inconclusive: completed runs use the
exact performance thresholds for Ready versus Limited. Unsupported static-reference
metric selections cannot establish device capacity and are explained as Inconclusive.
A configured camera frame rate below the supported assessment rate also returns
Inconclusive; this does not establish that the device is too slow.

A running measurement prevents compatibility testing, and an active compatibility
test prevents normal measurement startup. Neither operation silently interrupts the
other. Retry after the active operation has finished cleanup. If a previous
measurement left the SDK in an error state, resolve that error and reset the SDK
before retrying; waiting alone does not clear an error.

## Save the diagnostic report

The JSON includes SDK version, requested/enabled metrics, performance comparisons,
frame and output counts, timing, camera context, backend evidence, and diagnostic
events. It contains no API key, input pixels, or measured vital values. Its detailed
schema is support data, not a stable typed application API. Requested/enabled metrics,
input mode, and capture settings describe the assessed workload; reports do not
include policy version identifiers. The SDK version identifies the producing SDK.
Readable `verdictName` and `reasonName` fields describe both pre-check rejections
and completed native runs without requiring private enum definitions.

| Platform | Save the native JSON string |
| --- | --- |
| C++ | `if (auto result = sdk.getDeviceCompatibilityResult()) std::ofstream("compatibility.json") << result->diagnostics;` |
| Swift | `if let result = sdk.getDeviceCompatibilityResult() { try result.diagnostics.write(to: url, atomically: true, encoding: .utf8) }` |
| Kotlin | `sdk.getDeviceCompatibilityResult()?.let { file.writeText(it.diagnostics) }` |
| Node.js | `const result = sdk.getDeviceCompatibilityResult(); if (result) writeFileSync(path, result.diagnostics, { flag: 'wx', mode: 0o600 });` |

Provide the file or URL in your application's storage and handle file I/O errors.
C++ requires `<fstream>`; Node requires `writeFileSync` from `node:fs`. Serialize
neither the JSON string again nor a reconstructed subset if you want the full report.
