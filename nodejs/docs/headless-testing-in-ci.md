---
title: Headless Testing in CI
description: Run a full SmartSpectra measurement in CI by feeding a recorded video through the Node.js SDK.
---

# Headless Testing in CI (Node.js)

See [Headless Testing in CI](../../docs/headless-testing-in-ci.md) for the
cross-platform overview of what's automatable and why. This page covers the
Node.js specifics.

## What you can automate

The Node.js SDK accepts a **recorded video file** in place of a live camera
via `useFile()`, so CI can run a **full video-fed measurement**: the SDK plays
the file through the same pipeline as a live camera, emits `'metrics'` events
as readings arrive, and settles to idle at end-of-file. No camera or display
is needed — a stock `ubuntu-latest`-style runner works.

Keep the assertion **smoke-level**: check that real readings appeared (a
pulse rate and a breathing rate), not their exact values.

## The headless script

A complete runnable check — feed a video, wait for end-of-file, and exit
non-zero if no readings came out:

```js
// headless-smoke.mjs
import {
  SmartSpectraSDK,
  ProcessingStatus,
  breathingMetrics,
  cardioMetrics,
  decodeMetrics,
} from '@smartspectra/node-sdk';

const sdk = new SmartSpectraSDK({
  apiKey: process.env.SMARTSPECTRA_API_KEY,
  requestedMetrics: [...breathingMetrics, ...cardioMetrics],
});

let sawPulse = false;
let sawBreathing = false;

sdk.on('metrics', (buf) => {
  const metrics = decodeMetrics(buf);
  if (metrics.cardio?.pulseRate?.length) sawPulse = true;
  if (metrics.breathing?.rate?.length) sawBreathing = true;
});
sdk.on('error', (code, message, retryable) => {
  console.error(`SmartSpectra error ${code}: ${message} (retryable=${retryable})`);
  process.exitCode = 1;
});

// Resolves when the session settles: playback runs the file through the
// pipeline and transitions back to idle at end-of-file (or to error).
const settled = new Promise((resolve) => {
  let started = false;
  sdk.on('processingStatus', (status) => {
    if (status === ProcessingStatus.kRunning) started = true;
    if (started &&
        (status === ProcessingStatus.kIdle || status === ProcessingStatus.kError)) {
      resolve();
    }
  });
});

sdk.useFile(process.argv[2] ?? './test-assets/face.mp4');
sdk.start();   // non-blocking: playback runs on SDK worker threads
await settled;
await sdk.destroy();

if (!sawPulse || !sawBreathing) {
  console.error('FAIL: no pulse/breathing readings were produced');
  process.exit(1);
}
console.log('OK: pulse and breathing readings appeared');
```

Notes on the shape:

- The script is fully event-driven: `start()` returns immediately, the Node
  event loop stays free while SDK worker threads play the file, and the
  `'processingStatus'` idle transition marks end-of-file.
- The `'error'` listener turns an SDK-level failure (bad key, no network)
  into a non-zero exit instead of a silent zero-readings pass.

## The recorded video

Supply your own short clip — a few seconds of a well-lit, mostly still face,
framed like a real measurement — and keep it in your repo's test assets. Use
a widely-supported container/codec such as MP4 (H.264); the SDK decodes it
with OpenCV. See the [metrics guide](metrics.md) for which metrics to request
and how to read the decoded payloads.

## A CI pipeline, in general terms

1. **Expose the API key** as a job secret.
2. **Install the SDK** — `npm install @smartspectra/node-sdk` pulls in the
   per-platform native runtime; nothing else to install.
3. **Run the script** against your recorded video.
4. **Fail the job** on a non-zero exit.

A minimal, provider-neutral sketch (GitHub Actions):

```yaml
name: smartspectra-nodejs-headless-smoke
on: [push]

jobs:
  headless:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: 20

      - name: Install SmartSpectra
        run: npm install @smartspectra/node-sdk

      - name: Headless measurement smoke
        env:
          SMARTSPECTRA_API_KEY: ${{ secrets.SMARTSPECTRA_API_KEY }}
        run: node headless-smoke.mjs ./test-assets/face.mp4
```

## Limitations

- **No offline mode.** The measurement authenticates against the
  SmartSpectra service, so the CI runner needs network access.
- **Linux runners need glibc 2.35+** (Ubuntu 22.04+, Debian 12+); the native
  runtime is built on Ubuntu 22.04. `ubuntu-latest` qualifies.
- **Smoke, not accuracy.** A recorded-clip run confirms the integration and
  model pipeline end to end; it is not an accuracy benchmark.
