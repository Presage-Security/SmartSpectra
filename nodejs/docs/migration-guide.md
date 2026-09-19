---
title: Node.js Migration Guide
description: "Migrate Node.js camera selection from deviceIndex to CameraSelection, discover camera IDs, and update stopped-only source selection and reset behavior."
sidebarTitle: Migration Guide
---

# SmartSpectra Node.js SDK Migration Guide

## Node.js SDK v3.4.0 camera selection

The existing `useCamera()` and `useCamera({ deviceIndex: ... })` calls remain
available, with deprecation warnings in TypeScript. They retain index-based
capture and the previous lifecycle behavior. Opt in to the new API by passing an
explicit selection and separate capture options. Camera IDs are opaque and local to the native platform; do not
convert old integer indices to strings or reuse browser device IDs.

```ts
import { CameraSelection, SmartSpectraSDK } from '@smartspectra/node-sdk';

// Discovery requires no SDK instance, authentication, capture, or permission prompt.
const cameras = SmartSpectraSDK.availableCameras();
const sdk = new SmartSpectraSDK({ apiKey: 'YOUR_API_KEY' });

sdk.useCamera(CameraSelection.default);
sdk.useCamera(CameraSelection.front);
sdk.useCamera(CameraSelection.back);
if (cameras.length > 0) {
  sdk.useCamera(CameraSelection.byId(cameras[0].id), {
    width: 1280, height: 720, fps: 30,
  });
}
sdk.start();
```

Discovery returns `CameraInfo` records containing `id`, nullable `name`, `lensType`, and
`facing` (`'front'`, `'back'`, or `'unknown'`). External webcams may report unknown
facing. Visibility depends on platform permissions; a snapshot does not reserve
a camera or guarantee that later capture will succeed.

`lensType` is `'wideAngle'`, `'ultraWide'`, `'telephoto'`, or `'unknown'`.
Classification is best effort; missing metadata and cameras combining multiple
lenses report `'unknown'`. It does not describe digital zoom or change
selection. Use it for picker labels and continue selecting by `id`.

On macOS, default selection uses the first camera in discovery order. Explicit
front/back/ID requests never fall back; an unavailable selection fails startup
with `kInputUnavailable`. Empty IDs, NUL-containing IDs, invalid selectors, and
invalid capture options fail selection with `kConfigurationFailed`.

Native discovery and explicit selectors are currently supported on macOS.
Linux and Windows return `kConfigurationFailed` for these operations; default
capture remains available. The deprecated Node.js `deviceIndex` option and the
older index-based C ABI remain compatible. Do not pass `deviceIndex` in the new
overload's capture options.

### Select sources while stopped

After opting in with `useCamera(selection, options)`, `useCamera`, `useFile`,
and `useCustomInput` reject changes during startup,
processing, asynchronous stop, and shutdown with `kInvalidState`. Recover an
error state with `stop()` or `reset()` before selecting again. A rejected request
preserves the previously selected source. The SDK copies camera selection and
capture settings, so mutating your original options afterward has no effect.

```ts
await sdk.stopAsync();
sdk.useCamera(CameraSelection.back);
sdk.start();
```

Configure a source before calling `start()`, as before. For legacy callers,
`reset()` clears the source, so select one again before restarting. After opting
in with `useCamera(selection, options)`, `stop()` and `reset()` retain the selected
source and its settings; call `start()` to use it again. Calling the deprecated
`useCamera(options)` overload while stopped restores the legacy lifecycle.

After typed selection, `reset()` throws `kInvalidState` while a `stopAsync()` call
is pending. Await that stop before resetting. Legacy callers retain the ability
to request a reset while an asynchronous stop is pending.

### Electron renderer

The renderer API continues to use browser capture and `useMediaStream(stream)`.
These native selectors apply to the package root API, not the renderer entry
point. Keep browser camera IDs and native camera IDs within their respective
capture paths.
