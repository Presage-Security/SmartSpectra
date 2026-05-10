---
title: Migration Guide
description: Android-specific migration notes for SmartSpectra SDK upgrades.
---

# SmartSpectra Android SDK Migration Guide

> Applies to SmartSpectra Android SDK v3.0 (current: v3.0.0-rc.14).
> Migrating from a v3.0 release-candidate prior to rc.12, or from v2.x.

## Edge Metrics Migration

The `metricsBuffer` pathway has been removed. Android apps should now read all vitals data from `metrics`.

### What Changed

- `MetricsBuffer`-based APIs were removed
- On-device `Metrics` is now the single vitals data source

### Field Mappings

| Old (`metricsBuffer`) | New (`metrics`) |
| --------------------- | --------------- |
| `pulse.rateList` | `cardio.pulseRateList` |
| `pulse.traceList` | `cardio.arterialPressureTraceList` |
| `breathing.rateList` | `breathing.rateList` |
| `breathing.upperTraceList` | `breathing.upperTraceList` |
| `breathing.lowerTraceList` | `breathing.lowerTraceList` |
| `face` | `face` |

### Important

Cardio fields now require explicit opt-in. If your app displays pulse rate, arterial pressure trace, or HRV, enable cardio metrics explicitly.

```kotlin
import com.presagetech.smartspectra.SmartSpectraConfig

val sdk = SmartSpectraSdk.shared
sdk.config.requestedMetrics =
    SmartSpectraConfig.breathingMetrics + SmartSpectraConfig.cardioMetrics
```

**Removed:**

- `sdk.metricsBuffer`
- `setMetricsBufferObserver()`
- `setMetricsBuffer()`
- `clearMetricsBuffer()`

**Replace with:**

```kotlin
// Before:
smartSpectraSdk.metricsBuffer.observe(viewLifecycleOwner) { buffer ->
    val pulse = buffer?.pulse?.rateList?.lastOrNull()?.value
}
```

```kotlin
// After:
smartSpectraSdk.metrics.observe(viewLifecycleOwner) { metrics ->
    val pulse = metrics?.cardio?.pulseRateList?.lastOrNull()?.value
}
```

## Protobuf Java/Kotlin Package Rename

The generated protobuf classes moved from the old Physiology Java package to
the SmartSpectra package:

| Before | After |
| ------ | ----- |
| `com.presage.physiology.proto.*` | `com.presagetech.smartspectra.proto.*` |

Update imports for generated types such as `Metrics`, `MetricType`,
`StatusCode`, and `Insight`:

```kotlin
// Before:
import com.presage.physiology.proto.MetricsProto.Metrics
import com.presage.physiology.proto.MetricTypesProto.MetricType

// After:
import com.presagetech.smartspectra.proto.MetricsProto.Metrics
import com.presagetech.smartspectra.proto.MetricTypesProto.MetricType
```

The proto package also changed from `presage.physiology` to
`presagetech.smartspectra`. The binary wire format is unchanged because field
numbers did not change, but `Any.type_url`, JSON `@type` values, and descriptor
lookups use the fully-qualified message name. Regenerate or re-emit any
persisted `Any`-wrapped or JSON-typed payloads with the new SDK before mixing
old and new builds.

## Processing Status Migration

`SmartSpectraSdk` now exposes the aligned lifecycle model through `processingStatus`.

### New Enum Values

- `IDLE`
- `STARTING`
- `RUNNING`
- `STOPPING`
- `ERROR`

### Enum Mapping

| Previous value | New value |
| -------------- | --------- |
| `IDLE` | `IDLE` |
| `COUNTDOWN` | `STARTING` |
| `RUNNING` | `RUNNING` |
| `PREPROCESSED` | `RUNNING` |
| `DONE` | `RUNNING` |
| `DISABLE` | `IDLE` |
| `ERROR` | `ERROR` |

### Semantic Updates

- `RUNNING` means the pipeline is active and measurement is running
- `STOPPING` is a transient shutdown state on the path back to `IDLE`

```kotlin
// After:
SmartSpectraSdk.shared.processingStatus.observe(viewLifecycleOwner) { status ->
    when (status) {
        ProcessingStatus.IDLE -> showIdleUi()
        ProcessingStatus.STARTING -> showLoadingUi()
        ProcessingStatus.RUNNING -> showRecordingUi()
        ProcessingStatus.STOPPING -> showStoppingUi()
        ProcessingStatus.ERROR -> showErrorUi()
    }
}
```

## UI Surface Removal

The screening UI (Activities, Fragments, custom Views) no longer ships
with the SDK. The SDK is now a pure data/lifecycle plane; integrators
own all UI.

### What was removed from the SDK

- `SmartSpectraView`, `SmartSpectraButton`, `SmartSpectraResultView`
- `SmartSpectraActivity`, `OnboardingTutorialActivity`
- All `ui/screening/...` Fragments and custom Views
  (`CameraProcessFragment`, `ConfigurationErrorFragment`,
  `PermissionsRequestFragment`, `ScreeningPlotView`,
  `ExpressionStatusView`, `FaceMetricsStatusView`)
- `ui/summary/UploadingFragment`
- All UI layouts, drawables, tutorial assets, and screening strings
- The two SDK manifest `<activity>` declarations
- LiveDatas / helpers removed from `SmartSpectraSdk`:
  `timeLeft`, `hintText`, `roundedFps`, `attachPreview`,
  `detachPreview`, `flipCamera`, `resetMetrics`. None were public, and
  the only in-tree consumers were the relocated UI files. The
  internal `SmartSpectraVitalsProcessor` keeps `timeLeft` / `hintText`
  for instrumented tests; the SDK class no longer mirrors them.
- Config flags removed from `SmartSpectraConfig` (all were `internal`):
  `showFps`, `showOutputFps`, `showControlsInScreeningView`
- Derived helpers `cardioMeasurementsEnabled` and
  `facialExpressionEnabled` (replicate app-side from
  `requestedMetrics` if needed)

### Migration paths

#### Option A — Copy the reference UI from the demo-app sample

The sample at `android/samples/demo-app` (in the public
[Presage-Security/SmartSpectra](https://github.com/Presage-Security/SmartSpectra)
repo) mirrors the SDK's old package layout. Copy the relevant files into
your app and adjust `R.*` references:

```text
samples/demo-app/src/main/java/com/presagetech/smartspectra_example/
  SmartSpectraView.kt
  SmartSpectraButton.kt
  SmartSpectraResultView.kt
  ui/
    OnboardingTutorialActivity.kt
    SmartSpectraActivity.kt
    AmbientLightBrightnessController.kt
    screening/...
    summary/...
samples/demo-app/src/main/res/  (layouts, drawables, strings)
samples/demo-app/src/main/AndroidManifest.xml  (activity declarations)
```

#### Option B — Build your own UI against `SmartSpectraSdk`

Recommended for new integrations. The public surface gives you
everything you need:

```kotlin
val sdk = SmartSpectraSdk.shared
sdk.config.apiKey = "YOUR_API_KEY"
sdk.config.cameraPosition = CameraPosition.FRONT
sdk.config.requestedMetrics = SmartSpectraConfig.breathingMetrics + SmartSpectraConfig.cardioMetrics

// Drive the lifecycle from your own button.
lifecycleScope.launch { sdk.start() }
lifecycleScope.launch { sdk.stop() }

// Render preview frames from a public LiveData<Bitmap?>.
sdk.imageOutput.observe(this) { bitmap ->
    findViewById<ImageView>(R.id.preview).setImageBitmap(bitmap)
}

// React to status / metrics / errors.
sdk.processingStatus.observe(this) { renderStatus(it) }
sdk.metrics.observe(this) { renderMetrics(it) }
sdk.error.observe(this) { renderError(it) }
```

See `samples/minimal-app` for the smallest end-to-end headless
example, and `samples/demo-app` for a richer reference UI built on
top of the public surface.

### Lost reference UX

Mid-session camera flip, hint-text overlays, FPS overlay, the
configuration-error route, the bundled onboarding tutorial, and the
in-app EULA modals are no longer part of the SDK. The demo-app sample
keeps a compatible reference UX; integrators can copy it or roll their
own.

## `start()` clears observable state

`SmartSpectraSdk.start()` now resets the observable LiveData surface
(`metrics`, `imageOutput`, `validationStatus`, `error`, `insight`) to
`null` before kicking off processing.

Previously, the screening UI inside the SDK called an internal
`resetMetrics()` from its `onResume`. With the UI moved out and that
internal hook removed, callers re-entering a screening would see the
previous session's values flash on screen until fresh metrics arrived.
Resetting at the lifecycle boundary (`start()`) is the right place —
it keeps the contract uniform regardless of whether the host app
toggles processing in place or navigates away and back.

If your app accumulates derived state from these LiveData (chart
buffers, latest-rate caches, etc.), make sure your own clear logic
runs on `start()` or `processingStatus == STARTING` so it stays in
sync with the SDK's reset. The `samples/demo-app` and
`samples/minimal-app` already follow this pattern.

## Public surface narrowing for cross-platform parity

A small batch of Android-only or asymmetric-with-iOS public API is
now `internal`. None had a non-cosmetic reason to be public; the
changes bring the two SDKs closer to a shared contract.

### Metrics observers → requested metric bundles

Older Android releases did not expose public requested-metric selection. Apps
attached `setMetricsBufferObserver` for pulse and breathing output, and
`setEdgeMetricsObserver` for edge metrics such as dense face landmarks. The
current SDK uses `requestedMetrics` directly. Three companion `@JvmField`
lists on `SmartSpectraConfig` provide the standard bundles:
`breathingMetrics`, `cardioMetrics`, `faceMetrics`. The `Metrics` suffix
matches the C++ SDK's `SmartSpectraConfig::CardioMetrics()` etc.; the
`SmartSpectraConfig` namespace matches the iOS Swift surface.

```kotlin
// Before
sdk.setMetricsBufferObserver { metricsBuffer ->
    val pulse = metricsBuffer.pulse.rateList.lastOrNull()?.value
}

// After
sdk.config.requestedMetrics =
    SmartSpectraConfig.breathingMetrics + SmartSpectraConfig.cardioMetrics
```

### SDK permission screen → host-app responsibility

Older integrations usually relied on `SmartSpectraView` /
`SmartSpectraButton` to present the camera permission flow. Host apps now
drive the runtime camera prompt themselves — the modern pattern is
`ActivityResultLauncher`. The SDK still publishes a
`SmartSpectraError(code = INPUT_UNAVAILABLE, retryable = true)` to
`sdk.error` when the validator flags a missing manifest declaration or when
`start()` fails because the runtime permission is denied.

```kotlin
// Before
setContentView(R.layout.activity_main)
val smartSpectraView = findViewById<SmartSpectraView>(R.id.smart_spectra_view)

// After
private val cameraPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
) { granted -> if (granted) startProcessing() }

private fun startProcessing() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED
    ) {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        return
    }
    lifecycleScope.launch { runCatching { sdk.start() } }
}
```

### ABI checks → SDK-managed error

Older SDK UI checked ABI support before showing the measurement view. The
current SDK owns that check and publishes
`SmartSpectraError(code = CONFIGURATION_FAILED, retryable = false)` to
`sdk.error` when initialization detects an unsupported ABI.

```kotlin
// Before
SmartSpectraView(context, attrs) // SDK view handled unsupported ABI UI

// After
sdk.error.observe(this) { error ->
    if (error?.code == SmartSpectraError.Code.CONFIGURATION_FAILED) {
        showUnsupportedDeviceUi()
    }
}
```
