---
title: C++ Device Compatibility
description: Check the configured SDK and return a verdict, actionable reason, and diagnostic JSON.
---

# C++ Device Compatibility

> **Important:** SDK metrics are offered for general wellness and informational purposes only. SDK metrics have not been cleared by the FDA and may not be used for medical diagnosis or treatment.

After configuring the SDK's authentication, metrics, and camera, call:

```cpp
const auto verdict = sdk.isDeviceCompatible();
```

Use the returned verdict directly. Call `sdk.getDeviceCompatibilityResult()`
for the saved explanation and diagnostic JSON. The getter is empty before or during
a check and after cancellation/error; starting a new check clears the previous result. There is no separate configuration input
or public policy/timing override. The check uses the SDK's configured workload,
keeps its settings unchanged, and rejects competing measurement operations.

The selected camera supplies capture pacing; bundled reference imagery replaces
captured pixels. Stable checks may finish after 20 seconds of observation;
otherwise observation continues to approximately 30 seconds. Setup and cleanup
add time. File/custom-frame inputs are currently Inconclusive with an explanation.

Call from a worker thread in UI applications: the C++ call blocks until cleanup completes.
To request cancellation from another thread, call `sdk.cancelDeviceCompatibility()`.
Wait for the worker to finish before destroying the SDK. Cancellation clears the saved
result and does not interrupt cleanup.

See [Device Compatibility Results and Recovery](../../docs/device-compatibility-experience.md)
for complete setup, verdict meanings, execution safety, and JSON export examples.
