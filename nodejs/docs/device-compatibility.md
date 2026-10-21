---
title: Node.js Device Compatibility
description: Check the configured SDK and return a verdict, actionable reason, and diagnostic JSON.
---

# Node.js Device Compatibility

> **Important:** SDK metrics are offered for general wellness and informational purposes only. SDK metrics have not been cleared by the FDA and may not be used for medical diagnosis or treatment.

After configuring the SDK's authentication, metrics, and camera, call:

```javascript
const verdict = await sdk.isDeviceCompatible();
```

Compare the returned verdict explicitly, for example `verdict === 'ready'`.
Every verdict string is truthy, including `'blocked'`; do not use it as a Boolean. Call `sdk.getDeviceCompatibilityResult()`
for the saved explanation and diagnostic JSON. The getter is empty before or during
a check and after an exception; starting a new check clears the previous result. There is no separate configuration input
or public policy/timing override. The check uses the SDK's configured workload,
keeps its settings unchanged, and rejects competing measurement operations.

The selected camera supplies capture pacing; bundled reference imagery replaces
captured pixels. Stable checks may finish after 20 seconds of observation;
otherwise observation continues to approximately 30 seconds. Setup and cleanup
add time. File/custom-frame inputs are currently Inconclusive with an explanation.

Await the result; the check does not block the JavaScript event loop.

See [Device Compatibility Results and Recovery](../../docs/device-compatibility-experience.md)
for complete setup, verdict meanings, execution safety, and JSON export examples.
