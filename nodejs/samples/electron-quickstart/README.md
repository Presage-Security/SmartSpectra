# SmartSpectra Electron Quickstart

A cross-platform Electron app that drives the SmartSpectra SDK from a
renderer-side `MediaStream`. Two high-level toggles (Cardio, Face)
layered on the always-on Breathing bundle, and four live line charts
(breathing rate, breathing pleth waveform, pulse rate, arterial
pressure trace) that grow as the metrics buffer fills during
measurement and persist after Stop. Demonstrates the event surface
(`streamAvailable`, `processingStatus`, `validationStatus`, `metrics`,
`error`) and the start / stop / reset / destroy lifecycle.

The Metrics protobuf is decoded via the SDK's
`@smartspectra/node-sdk/messages` subpath — the SDK ships the generated
`Metrics` message class, so the sample doesn't run `pbjs` or carry its
own protobuf codegen.

## Start here

- Docs site: <https://smartspectra.presagetech.com/docs/nodejs>
- GitHub issues: <https://github.com/Presage-Security/SmartSpectra/issues>

| File | Purpose |
| --- | --- |
| [main.js](main.js) | Main-process entry. Creates the `BrowserWindow`, wires the IPC channel, and auto-allows camera permission. |
| [index.html](index.html) | UI shell — camera preview, status badge, vitals cards, control buttons. |
| [styles.css](styles.css) | Minimal dark-mode stylesheet. |
| [renderer.js](renderer.js) | Renderer entry. Uses `SmartSpectraSDK` from `@smartspectra/node-sdk/renderer`, bundled into `dist/renderer.bundle.js` by esbuild. |
| [build/entitlements.mac.plist](build/entitlements.mac.plist) | macOS hardened-runtime entitlements (camera + unsigned-library validation). |

## Prerequisites

| Requirement | Notes |
| --- | --- |
| Node.js 22.12+ | Node 24 LTS recommended. |
| `@smartspectra/node-sdk` | Pulled from npm by `npm install`. It delivers its native runtime through per-platform packages declared as regular dependencies — no install script, no token, no system libraries. |
| A Presage API key | From <https://physiology.presagetech.com/>. Set the `API_KEY` constant in `renderer.js` (replace the `YOUR_API_KEY_HERE` placeholder). |

## Run the sample

```bash
npm install        # pulls @smartspectra/node-sdk + its per-platform native runtime packages
npm start          # bundles the renderer with esbuild, then launches Electron
```

`npm install` resolves `@smartspectra/node-sdk` plus its per-platform
`@smartspectra/node-sdk-<plat>-<arch>` packages, which it declares as **regular
dependencies**. Every platform's package is downloaded (so the install is large)
and the binding loads the one matching your `os`/`cpu` at runtime — no further
setup.

> **VS Code terminal note:** VS Code integrated terminals export
> `ELECTRON_RUN_AS_NODE=1`, which makes Electron run as plain Node and the app
> exit immediately. Launch with it cleared: `env -u ELECTRON_RUN_AS_NODE npm
> start` (or use a non-VS-Code terminal).

## Package for distribution

You depend on **one** package — `@smartspectra/node-sdk`. Its native runtime is
delivered through per-platform `@smartspectra/node-sdk-<plat>-<arch>` packages
declared as regular dependencies, so a single `npm install` brings down **every**
platform's package. electron-builder then bundles only the target's package into
each installer.

### Build for your current platform

Run the script matching the OS you're on:

```bash
npm run dist:mac     # macOS   → dist/SmartSpectra Quickstart-*.dmg
npm run dist:win     # Windows → dist/*.exe (NSIS installer)
npm run dist:linux   # Linux   → dist/*.AppImage
```

The AppImage runs on Ubuntu 22.04 (Jammy, glibc 2.35) and anything newer —
the native runtime is built on Jammy, and glibc/libstdc++ come from the host
rather than being bundled.

Each script just bundles the renderer and runs electron-builder for that target.
Run `npm install` once first: because the per-platform packages are regular
dependencies, that single install leaves **every** platform's native package in
`node_modules`, so the binary electron-builder needs is always present — no
per-target reinstall.

### Cross-build for another platform

A native binary is OS-specific, but the one `npm install` already brought down
the package for **every** platform (they're regular dependencies), so the
target's binary is always present. You build any target from any host just by
running that platform's script — `dist:all` chains all three:

```bash
npm run dist:all     # mac, then win, then linux
```

**Cross-building installers needs the usual electron-builder host tooling**:
`wine` for the Windows NSIS installer, `docker` for the Linux AppImage, and a
**macOS host** for a signed/notarized `.dmg`. electron-builder shells out to
them. For real releases, building each OS on its own CI runner (see below)
avoids this entirely.

### How the native runtime is bundled (so the right binary ships)

Each platform block in [`package.json`](package.json)'s `build` config copies
only **its own** platform package into the installer:

```jsonc
"win":   { "extraResources": [{ "from": "node_modules/@smartspectra/node-sdk-win32-${arch}/", "to": "smartspectra" }] }
```

electron-builder expands `${arch}` to the target arch per build, so the Windows
installer gets the Windows closure and the macOS app gets the macOS closure —
never both. The native packages are kept **out of `app.asar`**
(`"!node_modules/@smartspectra/node-sdk-*/**"` in `files`) because a
`.dll`/`.dylib`/`.so` can't be loaded from inside an asar archive; they land as
real files at `resources/smartspectra/`. At runtime [`main.js`](main.js) points
`SMARTSPECTRA_CAPI_PATH` there before loading the binding.

### Recommended for real releases: build each OS on CI

The simplest, most reliable cross-platform release is an OS matrix where each
runner builds its own platform natively — no `wine`/`docker`, no `--os/--cpu`,
just the single dependency resolving itself:

```yaml
# .github/workflows/release.yml
jobs:
  build:
    strategy:
      matrix:
        os: [macos-latest, windows-latest, ubuntu-latest]
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 20 }
      - run: npm install            # pulls every platform package; this runner bundles its own
      - run: npx electron-builder --publish never
      - uses: actions/upload-artifact@v4
        with: { name: ${{ matrix.os }}, path: dist/ }
```

### macOS code signing

`build/entitlements.mac.plist` grants:

- `com.apple.security.device.camera` — required for `getUserMedia`.
- `com.apple.security.cs.disable-library-validation` — required because
  the SmartSpectra dylibs are not signed by your team identity. The
  alternative is to re-sign each dylib before packaging (preferable for
  notarization-strict deployments).
- `allow-jit` + `allow-unsigned-executable-memory` — standard for the
  Electron / V8 runtime under the hardened runtime.

Add your signing identity to `electron-builder`'s configuration (e.g. via
`CSC_LINK` / `CSC_KEY_PASSWORD` environment variables) before running
`npm run dist:mac` for a real notarized build.

### Windows code signing

Authenticode-sign the DLLs bundled at `resources/smartspectra/`. They load via
`SMARTSPECTRA_CAPI_PATH` (set by `main.js`) with adjacent-DLL search, so they
stay together in that directory — no `PATH` plumbing needed.

## What the sample shows

1. **Camera permission flow** — on macOS the OS prompts the user once on
   first launch; the in-page `getUserMedia` Promise resolves immediately
   thereafter. On Windows and Linux it's granted without a separate OS prompt.
   [main.js](main.js) scopes the grant to the camera (video-only) and to the
   bundled `file://` page.
2. **Renderer-side frame pump (handled by the SDK)** — the renderer SDK
   (`@smartspectra/node-sdk/renderer`) reads `VideoFrame`s from the camera track
   with `MediaStreamTrackProcessor`, draws each into an `OffscreenCanvas` to
   extract RGBA pixels, and structured-clones the buffer to the main process
   over its IPC bridge (Electron's `MessagePortMain` drops `ArrayBuffer`
   transfer lists, so cloning is unavoidable). The sample just calls
   `sdk.start()` — the pump runs for you.
3. **Validation hints** — face position, lighting, and camera-tuning
   states are surfaced live in the preview overlay.
4. **Metrics flow** — each `metrics` event delivers a windowed protobuf
   slice. The sample decodes it via
   `require('@smartspectra/node-sdk/messages').decodeMetrics(buf)`, appends the
   breathing / cardio fields to renderer-side accumulators, and refreshes the
   four live charts plus the Heart Rate / Breathing numeric readouts.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| App exits immediately with `Cannot read properties of undefined (reading 'whenReady')` or `Cannot find module 'electron'` | `ELECTRON_RUN_AS_NODE=1` is set in the environment — common in **VS Code integrated terminals** (VS Code is itself Electron and exports it to child processes). It forces the `electron` binary to run as plain Node, so the `electron` module has no `app`. | Launch with the variable cleared: `env -u ELECTRON_RUN_AS_NODE npm start` (or run from a non-VS-Code terminal). |
| `Failed to load device keys` errors in the main-process terminal when starting with the **Cardio** toggle on | Your API key is not enabled for cardio metrics, so `ARTERIAL_PRESSURE_TRACE` (and related cardio outputs) cannot initialize. | With the **Cardio** toggle off (the default) the app requests only breathing metrics and is not affected. To enable cardio metrics, use an API key provisioned for cardio outputs, then enable the Cardio toggle **before** pressing Start (`requestedMetrics` is fixed per session). |
| `window.__smartspectraBridge is unavailable` | Preload script not wired. | Verify `webPreferences.preload` resolves to `@smartspectra/node-sdk/preload`. |
| `Refused to create a worker from 'blob:…'` | CSP blocks `blob:` workers. | The bundled `index.html` already allows it; remove any overriding CSP from your `index.html`. |
| `Authentication failed` | API key missing or wrong. | Edit `renderer.js`, paste your key into `API_KEY`. |
| Crash on launch (macOS) with `Library validation failed` | Hardened runtime rejected the unsigned SmartSpectra dylibs. | Verify `build/entitlements.mac.plist` includes `com.apple.security.cs.disable-library-validation`, or re-sign the dylibs. |
| `LoadLibrary failed` (Windows) | Native package missing from the build, or `SMARTSPECTRA_CAPI_PATH` unset in the packaged app. | For a packaged app, confirm `resources/smartspectra/` contains the DLLs (it's filled by `win.extraResources`); for dev, reinstall so the platform package is present. |
| `kNonMonotonicTimestamp` errors | Replayed or paused `MediaStream`. | The bundled renderer worker already ratchets timestamps; if you see this from a custom stream source, ensure it's monotonic. |
