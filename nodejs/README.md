---
title: Node.js SDK
description: Build Node.js apps — including Electron desktop apps — with SmartSpectra via a packaged native runtime loaded through koffi.
---

# @smartspectra/node-sdk

Pure-FFI Node.js (Electron) binding for SmartSpectra vitals measurement.
[koffi](https://koffi.dev) loads the SmartSpectra C ABI shim at runtime —
no native addon, no `binding.gyp`, no `electron-rebuild`, no `node-gyp`.

## Start here

- Electron sample: [electron-quickstart](https://github.com/Presage-Security/SmartSpectra/tree/main/nodejs/samples/electron-quickstart)
- API reference: <https://smartspectra.presagetech.com/docs/nodejs/api-reference>
- Metrics guide: <https://smartspectra.presagetech.com/docs/nodejs/metrics>

## Architecture

```text
┌──────────────────────────┐
│ your Node / Electron app │
└───────────┬──────────────┘
            │ require('@smartspectra/node-sdk')
┌───────────▼──────────────┐
│ js/index.js              │  public surface
│ js/smartspectra.js       │  SmartSpectraSDK class
│ js/ffi.js                │  koffi types + Session
│ js/resolve-native.js     │  shim path resolver
└───────────┬──────────────┘
            │ koffi.load()
┌────────────────────────────────────────┐
│ @smartspectra/node-sdk-<plat>-<arch>/  │  self-contained native runtime,
│   libsmartspectra_capi.*               │  shipped as a dependency package;
│   …bundled runtime libraries…          │  load paths pre-relocated to
│                                        │  @loader_path / $ORIGIN /
└────────────────────────────────────────┘  adjacent-DLL search
```

The native runtime ships in per-platform packages
(`@smartspectra/node-sdk-<plat>-<arch>`) pulled in as dependencies of the main
package. There is **no install script** in the published tarball and no
postinstall download; the binding loads the package matching your machine at
runtime. SDK-thread callbacks are marshalled onto the V8 event loop by koffi
thread-safe trampolines.

## Supported Platforms

| Platform | Status | Notes |
| --- | --- | --- |
| macOS Apple Silicon (`darwin-arm64`) | Supported | Electron and headless Node workflows supported |
| Linux x64 (`linux-x64`) | Supported | Requires glibc 2.35+ |
| Linux ARM64 (`linux-arm64`) | Supported | Requires glibc 2.35+ |
| Windows x64 (`win32-x64`) | Supported | Electron and headless Node workflows supported |

## Common Prerequisites

| Requirement | Version |
| --- | --- |
| Node.js | >= 18 (Node 20 LTS recommended) |
| Electron (optional) | >= 28 |

The native runtime ships in per-platform npm packages pulled in automatically
as dependencies — no install script and no system libraries to install.

Supported platforms: `darwin-arm64`, `linux-x64`, `linux-arm64`, `win32-x64`.

On Linux the native runtime is built on Ubuntu 22.04 (Jammy), so it requires
glibc >= 2.35 — Ubuntu 22.04+, Debian 12+, or any distribution at least that
new. glibc and libstdc++ are provided by your system, not bundled.

## Installation

```bash
npm install @smartspectra/node-sdk
```

Early adopters can track the latest release candidate with the `rc` dist-tag:

```bash
npm install @smartspectra/node-sdk@rc
```

The native packages (`@smartspectra/node-sdk-<platform>-<arch>`) come in as
dependencies and the binding loads the one matching your `platform`/`arch` at
runtime. Each is self-contained — it bundles everything the binding loads at
runtime, with paths already rewritten at publish time, so nothing needs to be on
PATH and no system libraries are required. There is no install script.

> **Note** — `npm install` downloads the native runtime for every supported
> platform (a few hundred MB total), not just your host's. This is expected;
> only the package matching your machine is loaded at runtime.

If your platform is unsupported (no `@smartspectra/node-sdk-<plat>-<arch>` is
published for it), the first `require()` fails with an actionable error naming
the missing package.

## Pick Your Integration Path

- Electron desktop app: use `@smartspectra/node-sdk/main`,
  `@smartspectra/node-sdk/preload`, and `@smartspectra/node-sdk/renderer`.
- Headless or server-side Node process: use `@smartspectra/node-sdk`
  directly and push frames with `useCustomInput()` / `sendFrame()`.
- Runnable reference app: [electron-quickstart](https://github.com/Presage-Security/SmartSpectra/tree/main/nodejs/samples/electron-quickstart)

## Headless Node Quickstart

```ts
import {
  SmartSpectraSDK, PixelFormat, FrameTransform, ProcessingStatus,
  breathingMetrics, cardioMetrics,
  decodeMetrics, setMetricsClass,
} from '@smartspectra/node-sdk';

// Optional: override the default Metrics decoder.
// import { Metrics } from './generated/metrics_pb';
// setMetricsClass(Metrics);

const sdk = new SmartSpectraSDK({
  apiKey: 'YOUR_API_KEY',
  requestedMetrics: [...breathingMetrics, ...cardioMetrics],
});

sdk.on('processingStatus', (status) => console.log('Processing status:', status));
sdk.on('validationStatus', (code, ts, hint) =>
  console.log('Validation:', code, hint, 'at', ts, 'µs'));
sdk.on('metrics', (buf, ts) => {
  const m = decodeMetrics(buf);
  console.log('Metrics at', ts, 'µs');
});
sdk.on('error', (code, message, retryable) =>
  console.error('SmartSpectra error', code, message, 'retryable=', retryable));

sdk.useCustomInput(FrameTransform.kNone);
sdk.start();

// In your capture loop:
sdk.sendFrame(rgbBuf, width, height, width * 3, PixelFormat.kRGB, captureTsUs);

// On shutdown:
await sdk.destroy();
```

## API reference

The full API — `SmartSpectraSDK` constructor options, methods, events, error
codes, and enums — lives in the [API reference](docs/api-reference.md),
generated from the SDK's bundled TypeScript declarations so it tracks the
published package. For which metrics to request and how to read the decoded
payloads, see the [metrics guide](docs/metrics.md).

## Electron integration

Runnable sample at [electron-quickstart](https://github.com/Presage-Security/SmartSpectra/tree/main/nodejs/samples/electron-quickstart).
See its [README](https://github.com/Presage-Security/SmartSpectra/blob/main/nodejs/samples/electron-quickstart/README.md).

The package ships these entry points:

```text
@smartspectra/node-sdk                  → SmartSpectraSDK (low-level, main process)
@smartspectra/node-sdk/main             → bindSmartSpectraIpc(window)
@smartspectra/node-sdk/preload          → preload bridge (contextBridge)
@smartspectra/node-sdk/renderer         → SmartSpectraSDK (renderer-side, MediaStream input)
@smartspectra/node-sdk/messages         → decodeMetrics(buf) + the generated Metrics class
```

### Main process

```ts
import { app, BrowserWindow } from 'electron';
import { bindSmartSpectraIpc } from '@smartspectra/node-sdk/main';

app.whenReady().then(() => {
  const win = new BrowserWindow({
    webPreferences: {
      preload: require.resolve('@smartspectra/node-sdk/preload'),
      contextIsolation: true,
      sandbox: true,
    },
  });
  bindSmartSpectraIpc(win);
  win.loadFile('index.html');
});
```

`bindSmartSpectraIpc` listens on a single private IPC channel, accepts the
MessagePort the preload ships from the renderer, and owns one
`SmartSpectraSDK` per renderer connection. SDK teardown happens
automatically on window close.

### Renderer

```ts
import { SmartSpectraSDK } from '@smartspectra/node-sdk/renderer';
import { breathingMetrics, cardioMetrics } from '@smartspectra/node-sdk';

const sdk = new SmartSpectraSDK({
  apiKey: 'YOUR_KEY',
  requestedMetrics: [...breathingMetrics, ...cardioMetrics],
});

sdk.on('streamAvailable',  (stream) => { videoEl.srcObject = stream; });
sdk.on('metrics',          (buf, ts) => { /* render dashboard */ });
sdk.on('validationStatus', (code, ts, hint) => { /* show user hint */ });
sdk.on('error',            (code, msg, retryable) => { /* surface in UI */ });

await sdk.start();          // SDK acquires the front camera + emits 'streamAvailable'

await sdk.requestInsight('How is my breathing?');
await sdk.stop();           // SDK releases the camera; next start() re-acquires
sdk.destroy();
```

Defaults: front-facing camera at 1280x720 / 30 fps, AE/AWB/focus locked
once the graph reports `Running`. Call `sdk.useMediaStream(stream)` before
`sdk.start()` to override with a virtual camera,
`<canvas>.captureStream()`, `desktopCapturer`, etc. Host-supplied streams
are managed by the host; SDK-acquired streams are released on
`stop()` / `reset()` / `destroy()`.

The renderer uses `MediaStreamTrackProcessor` + `OffscreenCanvas` to
extract RGBA pixels and ships each frame to the main process via the
MessagePort. Graph callbacks flow back through the same port.

Each control call (`start` / `stop` / `reset` / `requestInsight`) waits for
an ack from the main process. If the main process crashes or stalls, the
call rejects after `sendTimeoutMs` (default `30000`) with an `Error` whose
`code` is `'SMARTSPECTRA_IPC_TIMEOUT'`, so the UI surfaces "main process
unresponsive" instead of hanging forever. Pass `sendTimeoutMs` to the
constructor to tune it, or `0` to disable.

### Preload

If the app already has a preload script, require this module from it:

```js
require('@smartspectra/node-sdk/preload');
```

Otherwise point `webPreferences.preload` directly:

```ts
preload: require.resolve('@smartspectra/node-sdk/preload')
```

### Content Security Policy

The renderer runs a Web Worker from a `blob:` URL. If your app sets a CSP,
allow `blob:` in `worker-src`:

```html
<meta http-equiv="Content-Security-Policy"
      content="default-src 'self'; worker-src 'self' blob:;">
```

A `Refused to create a worker from 'blob:…'` console error at `start()`
time means the policy is blocking the worker.

### Permission flow

Camera permission flows through Electron's native handler — the SDK calls
`navigator.mediaDevices.getUserMedia()` (or the host's stream, if supplied
via `useMediaStream()`) and Electron surfaces the OS prompt + indicator
LED. To grant programmatically:

```ts
import { session } from 'electron';

session.defaultSession.setPermissionRequestHandler((wc, permission, callback, details) => {
  // Grant only the camera, and only to your own bundled page — deny everything
  // else so a window that later loads remote content can't auto-grant the camera.
  const url = (details && details.requestingUrl) || (wc && wc.getURL()) || '';
  callback(permission === 'media' && url.startsWith('file://'));
});
```

On macOS, add `NSCameraUsageDescription` to `Info.plist`. Windows and
Linux need no additional entitlements.

## Electron desktop packaging

The installed platform package
`node_modules/@smartspectra/node-sdk-<plat>-<arch>/` ships the full native
closure — every library `libsmartspectra_capi` needs at runtime, with
install_names / RPATHs pre-relocated to `@loader_path` (macOS), `$ORIGIN`
(Linux), or adjacent-directory search (Windows). Include that directory as an
extra resource and you're done.

### electron-builder

Use one block per target OS. electron-builder's `${platform}` macro expands to
the **build host's** platform, not the build target — so a single
`${platform}` entry ships the wrong (or no) closure on a cross-build
(e.g. `electron-builder --win` on a Mac). The per-OS `mac`/`win`/`linux` blocks
are applied only to their matching target and are cross-build-safe:

```jsonc
// package.json
{
  "build": {
    "mac":   { "extraResources": [{ "from": "node_modules/@smartspectra/node-sdk-darwin-${arch}/", "to": "smartspectra/" }] },
    "win":   { "extraResources": [{ "from": "node_modules/@smartspectra/node-sdk-win32-${arch}/",  "to": "smartspectra/" }] },
    "linux": { "extraResources": [{ "from": "node_modules/@smartspectra/node-sdk-linux-${arch}/",  "to": "smartspectra/" }] }
  }
}
```

### electron-forge

```js
// forge.config.js
module.exports = {
  packagerConfig: {
    extraResource: [
      `node_modules/@smartspectra/node-sdk-${process.platform}-${process.arch}/`,
    ],
  },
};
```

> **Cross-builds:** `process.platform`/`process.arch` resolve at config-load
> time to the **host**, not the build target — so this single line is correct
> only for native (per-OS-CI) builds. To cross-build, switch on the target
> (`--platform`/`--arch` passed to `electron-forge package`) and emit the
> matching `node_modules/@smartspectra/node-sdk-<target>/` path.

### macOS code signing

The bundled `.dylib` files are ad-hoc signed so dyld will load them; for
distribution you'll want to re-sign with your own identity. With
electron-builder:

```jsonc
{
  "build": {
    "mac": {
      "hardenedRuntime": true,
      "entitlements": "build/entitlements.mac.plist",
      "extendInfo": { "NSCameraUsageDescription": "Vitals measurement" }
    }
  }
}
```

In `entitlements.mac.plist`, allow loading the bundled dylibs:

```xml
<key>com.apple.security.cs.disable-library-validation</key>
<true/>
```

Alternative: re-sign every `.dylib` under
`node_modules/@smartspectra/node-sdk-darwin-arm64/` with your own identity
before packaging (preferable for notarization-strict deployments).

### Windows code signing

Authenticode-sign each `.dll` under
`node_modules/@smartspectra/node-sdk-win32-x64/` alongside your app's main
executable. Windows resolves adjacent DLLs first, so
placing them in the same directory as `<your-app>.exe` is the simplest
layout.

### Linux

No signing required. The bundled `.so` files have `$ORIGIN` RPATHs so
they resolve siblings without any `LD_LIBRARY_PATH` plumbing.

**glibc floor — Ubuntu 22.04 (Jammy, `GLIBC_2.35`).** The runtime closure is
built on a Jammy base, so a packaged app (e.g. an AppImage) runs on Ubuntu
22.04 and any newer distribution. glibc and the C++ runtime come from the
host system — they are not vendored in the platform package.

## Performance notes

- koffi adds ~100 ns per FFI call — negligible for `sendFrame()` at 30 fps.
- SDK callbacks arrive on worker threads; koffi marshals them onto the V8
  event loop, so listeners run on the main thread.
- In Electron, the renderer-side SDK (`@smartspectra/node-sdk/renderer`)
  captures frames in the renderer and ships them to the main process —
  simplest to wire and fine for typical use. For the lowest latency at high
  resolution, capture in the main process instead (`desktopCapturer` or an
  off-screen renderer), since renderer→main IPC adds frame-time latency that
  scales with resolution.

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `the native runtime package "@smartspectra/node-sdk-<plat>-<arch>" is not installed` on require | platform unsupported, or a partial/offline install dropped the dependency | Confirm your platform is supported, then re-run `npm install` |
| `Cannot open shared object file` / `LoadLibrary failed` on require | platform package present but its bundled closure is incomplete or corrupt | Reinstall the platform package (`npm install`) |
| `kAuthenticationFailed` | API key issue | Check the key + the keychain entitlement on macOS |
| `kNonMonotonicTimestamp` | Wall-clock timestamps | Use `process.hrtime.bigint() / 1000n` (monotonic) |

## Support

- Docs site: <https://smartspectra.presagetech.com/docs/nodejs>
- GitHub issues: <https://github.com/Presage-Security/SmartSpectra/issues>
- Email: <support@presagetech.com>
