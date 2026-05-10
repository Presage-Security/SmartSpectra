---
title: Migration Guide
description: Migration notes for SmartSpectra Swift SDK upgrades.
---

# SmartSpectra Swift SDK Migration Guide

> Applies to SmartSpectra Swift SDK v3.0 (current: v3.0.0-rc.14).
> Migrating from a v3.0 release-candidate prior to rc.13, or from v2.x.

## Protobuf Type Renames

The Swift SDK's protobuf-generated types previously carried a `Presage_Physiology_` prefix derived from the proto package. The prefix has been stripped at the protoc-gen-swift level (`option swift_prefix = "";`), so all proto types are now exposed under their bare names.

### Quick reference

| Before | After |
| --- | --- |
| `Presage_Physiology_Metrics` | `Metrics` |
| `Presage_Physiology_Insight` | `Insight` |
| `Presage_Physiology_InsightType` | `InsightType` |
| `Presage_Physiology_FeatureType` | `FeatureType` |
| `Presage_Physiology_MetricType` | `MetricType` |
| `Presage_Physiology_Measurement` | `Measurement` |
| `Presage_Physiology_MeasurementWithConfidence` | `MeasurementWithConfidence` |
| `Presage_Physiology_DetectionStatus` | `DetectionStatus` |
| `Presage_Physiology_ExpressionType` | `ExpressionType` |
| `Presage_Physiology_StatusValue` | `StatusValue` |
| `Presage_Physiology_StatusCode` | `StatusCode` |

The same rule applies to every other type the proto schema exposes (`Pulse`, `Breathing`, `Trace`, `Strict`, `Face`, `Landmarks`, `Point2dFloat`, …).

### What to change

Replace any reference to a `Presage_Physiology_*` symbol with the bare name. A repo-wide search-and-replace is sufficient:

```sh
sed -i '' 's/Presage_Physiology_//g' <your-source-files>
```

### Name collisions

Bare names can collide with types in modules the consumer also imports. Known collisions today:

- `Measurement` collides with `Foundation.Measurement<UnitType>`.
- `Trace` collides with `os.Trace` (OSLog signpost APIs).

In files that import the colliding module, qualify the SmartSpectra type at the use site:

```swift
import Foundation
import SmartSpectra

var breathingTrace: [SmartSpectra.Measurement] = []
let pulseTrace: SmartSpectra.Trace = ...
```

Everything else (`MeasurementWithConfidence`, `Pulse`, `Insight`, `Strict`, …) doesn't collide with anything in the standard Apple modules today — leave those bare.

Wire format is unchanged (`swift_prefix` only affects Swift codegen).

## Package Rename

The Swift SDK module and SPM product were renamed from `SmartSpectraSwiftSDK` to `SmartSpectra`. Update every import and the shared SDK type:

```swift
// Before:
import SmartSpectraSwiftSDK
let sdk = SmartSpectraSwiftSDK.shared

// After:
import SmartSpectra
let sdk = SmartSpectraSDK.shared
```

Most call sites should move from `SmartSpectraSwiftSDK.shared` to
`SmartSpectraSDK.shared`.

## Edge Metrics Migration

The `metricsBuffer` pathway has been removed. Swift apps should now read vitals data from `metrics` on `SmartSpectraSDK.shared`.

### What Changed

- `metricsBuffer` and `$metricsBuffer` were removed
- on-device `metrics` is now the vitals data source
- public configuration now lives on `sdk.config`

### Field Mappings

| Old (`metricsBuffer`) | New (`metrics`) |
| --------------------- | --------------- |
| `pulse.rate` | `cardio.pulseRate` |
| `breathing.rate` | `breathing.rate` |
| `pulse.trace` | `cardio.arterialPressureTrace` |
| `breathing.upperTrace` | `breathing.upperTrace` |

### Important

Cardio fields now require explicit opt-in through requested metrics configuration.

```swift
import SmartSpectra

let sdk = SmartSpectraSDK.shared
sdk.config.requestedMetrics = SmartSpectraConfig.breathingMetrics + SmartSpectraConfig.cardioMetrics
```

**Removed:**

- `sdk.metricsBuffer`
- `sdk.$metricsBuffer`
- `MetricsBuffer`

**Replace with:**

```swift
// Before:
import SmartSpectraSwiftSDK

let sdk = SmartSpectraSwiftSDK.shared
sdk.$metricsBuffer.sink { buffer in
    // read buffer.pulse.rate, buffer.breathing.rate, ...
}
```

```swift
// After:
import SmartSpectra

let sdk = SmartSpectraSDK.shared

if let metrics = sdk.metrics {
    // read metrics.cardio.pulseRate, metrics.breathing.rate, ...
}
```

## Public Configuration Surface

- authentication should be set through `sdk.config.apiKey`
- metric selection should be set through `sdk.config.requestedMetrics`
- camera selection should be set through `sdk.config.cameraPosition`
- preview frame publishing should be controlled through `sdk.config.imageOutputEnabled`

### Removed

- `sdk.setApiKey("...")`
- `sdk.setCameraPosition(...)`
- `sdk.setImageOutputEnabled(...)`

### Replace With

| Old | New |
| --- | --- |
| `sdk.setApiKey("...")` | `sdk.config.apiKey = "..."` |
| `sdk.setCameraPosition(.front)` | `sdk.config.cameraPosition = .front` |
| `sdk.setImageOutputEnabled(true)` | `sdk.config.imageOutputEnabled = true` |
| no public metric-selection API | `sdk.config.requestedMetrics = [...]` |

```swift
// Before:
let sdk = SmartSpectraSwiftSDK.shared
sdk.setApiKey("YOUR_API_KEY")
sdk.setCameraPosition(.front)
sdk.setImageOutputEnabled(true)
```

```swift
// After:
let sdk = SmartSpectraSDK.shared
sdk.config.apiKey = "YOUR_API_KEY"
sdk.config.requestedMetrics = [.breathingRate, .pulseRate, .faceLandmarks]
sdk.config.cameraPosition = .front
sdk.config.imageOutputEnabled = true
```

## Headless Lifecycle Migration

```swift
// Before:
let vitalsProcessor = SmartSpectraVitalsProcessor.shared

vitalsProcessor.startProcessing { error in
    if let error {
        print(error.localizedDescription)
    }
}

vitalsProcessor.stopProcessing()
```

```swift
// After:
let sdk = SmartSpectraSDK.shared

try await sdk.start()
try await sdk.stop()
```

## Observable State Consolidation

| Old | New |
| --- | --- |
| `sdk.metricsBuffer` | `sdk.metrics` |
| `vitalsProcessor.processingStatus` | `sdk.processingStatus` |
| `vitalsProcessor.lastStatusCode` + `vitalsProcessor.statusHint` | `sdk.validationStatus?.code` + `sdk.validationStatus?.hint` |
| `sdk.resultErrorText` | `sdk.error?.message` |
| `vitalsProcessor.imageOutput` | `sdk.imageOutput` |

## Validation Status

```swift
// Before:
Text(vitalsProcessor.statusHint)
```

```swift
// After:
if let validationStatus = sdk.validationStatus {
    showBanner(validationStatus.hint)

    switch validationStatus.code {
    case .ok:
        break
    case .noFaceFound, .multipleFacesFound, .faceNotCentered,
        .faceSizeOutOfRange, .tooDark, .tooBright,
        .chestNotVisible, .cameraTuning:
        break
    }
}
```

## Error Model

```swift
// Before:
if !sdk.resultErrorText.isEmpty {
    let message = sdk.resultErrorText
    showError(message)
}
```

```swift
// After:
if let error = sdk.error {
    showError(error.message)

    if error.retryable {
        showRetryAction()
    }
}
```

## Processing Status Migration

### Type Rename

```swift
// Before:
let status: PresageProcessingStatus = vitalsProcessor.processingStatus

// After:
let status: ProcessingStatus = sdk.processingStatus
```

### Lifecycle Cases

- `idle`
- `starting`
- `running`
- `stopping`
- `error`

### Case Mapping

| Previous case | New case |
| ------------- | -------- |
| `idle` | `idle` |
| `starting` | `starting` |
| `processing` | `running` |
| `processed` | `idle` |
| `error` | `error` |

### Example Update

```swift
// Before:
if vitalsProcessor.processingStatus == .idle || vitalsProcessor.processingStatus == .processed {
    showResults()
}
```

```swift
// After:
if sdk.processingStatus == .idle {
    showResults()
}
```

## Observable Migration

`SmartSpectraSDK` and `SmartSpectraConfig` moved from `ObservableObject` + `@Published` to the Swift 5.9 `@Observation` macro. The Combine-style `$` publishers (`sdk.$metrics`, `sdk.$error`, `sdk.$processingStatus`, `sdk.$imageOutput`, `sdk.$validationStatus`, `sdk.$insight`) are gone. The properties themselves remain and now track automatically in SwiftUI views.

### SwiftUI Views

Drop `@ObservedObject` / `@StateObject` wrappers on `sdk`. SwiftUI re-renders when tracked properties change:

```swift
// Before:
struct MyView: View {
    @ObservedObject private var sdk = SmartSpectraSwiftSDK.shared

    var body: some View {
        Text("Status: \(sdk.processingStatus)")
    }
}
```

```swift
// After:
struct MyView: View {
    private let sdk = SmartSpectraSDK.shared

    var body: some View {
        Text("Status: \(sdk.processingStatus)")
    }
}
```

For side effects on property change, use `.onChange(of:)` instead of `.onReceive($X)` or manual `.sink { }`:

```swift
// Before:
.onReceive(sdk.$metricsBuffer) { buffer in
    guard let buffer else { return }
    appendToChart(buffer.breathing.rate)
}
```

```swift
// After:
.onChange(of: sdk.metrics) { _, metrics in
    guard let metrics else { return }
    appendToChart(metrics.breathing.rate)
}
```

### UIKit Consumers

UIKit has no `.onChange(of:)` equivalent. Replace Combine subscriptions with `withObservationTracking`, re-armed after each change:

```swift
// Before:
sdk.$metricsBuffer
    .receive(on: DispatchQueue.main)
    .sink { [weak self] buffer in
        self?.update(buffer)
    }
    .store(in: &cancellables)
```

```swift
// After:
private func observeMetrics() {
    withObservationTracking {
        _ = sdk.metrics
    } onChange: { [weak self] in
        Task { @MainActor [weak self] in
            guard let self else { return }
            self.update(self.sdk.metrics)
            self.observeMetrics()
        }
    }
}
```

A generic helper keeps multiple keypath observations tidy:

```swift
private func observeSDK<T>(
    _ keyPath: KeyPath<SmartSpectraSDK, T>,
    _ handler: @escaping (T) -> Void
) {
    withObservationTracking {
        _ = sdk[keyPath: keyPath]
    } onChange: { [weak self] in
        Task { @MainActor [weak self] in
            guard let self else { return }
            handler(self.sdk[keyPath: keyPath])
            self.observeSDK(keyPath, handler)
        }
    }
    handler(sdk[keyPath: keyPath])
}

// Usage:
observeSDK(\.metrics) { [weak self] metrics in
    guard let self, let metrics else { return }
    self.update(metrics)
}
observeSDK(\.processingStatus) { [weak self] status in
    self?.updateStatus(status)
}
```

### Non-SwiftUI `@Observable` Classes

View models that consume SDK state should mark themselves `@MainActor @Observable`. Combine `.sink` on `sdk.$X` inside those view models migrates to the same `withObservationTracking` re-arm pattern shown above.

## Configuration Access

Configuration is now reached through the SDK instance only:

```swift
// Before:
let sdk = SmartSpectraSwiftSDK.shared
sdk.setApiKey("YOUR_API_KEY")
sdk.setCameraPosition(.front)
```

```swift
// After:
let sdk = SmartSpectraSDK.shared
sdk.config.apiKey = "YOUR_API_KEY"
sdk.config.requestedMetrics = SmartSpectraConfig.cardioMetrics + SmartSpectraConfig.breathingMetrics
```

Older releases did not expose public `SmartSpectraConfig` access; configuration
was applied through methods on `SmartSpectraSwiftSDK.shared`. Current releases
make `sdk.config` the single source of truth and prevent the class of bug where
views observed one config instance while the SDK held another.

### Custom SDK Instances

`SmartSpectraSDK` and `SmartSpectraConfig` now expose public initializers for callers that want an isolated SDK instance (for tests or advanced integrations):

```swift
let customConfig = SmartSpectraConfig()
customConfig.apiKey = "…"
let sdk = SmartSpectraSDK(config: customConfig)
```

Most apps should keep using `SmartSpectraSDK.shared` — it's the intended entry point, matching the `URLSession.shared` pattern.

#### What multi-instance gives you today

**Isolated per instance:**

- `@Observable` state (`metrics`, `error`, `processingStatus`, `validationStatus`, `insight`, `imageOutput`)
- `config` — each SDK has its own
- SwiftUI views bound via `init(sdk:)` or `.smartSpectraSDK(_:)` render against the correct instance

**Still process-global (not isolated):**

- Authentication — the underlying auth handler is a singleton, so setting `apiKey` on one instance affects the auth state that every instance sees
- Camera — only one `AVCaptureSession` can be active at a time on iOS, and it is owned process-wide
- The preprocessing runtime — only one instance can drive an *active measurement* at a time

In practice this means custom instances are useful for **tests** (isolated state per test), **side-by-side UI** (show state from two SDKs without either actively processing), or **sequential lifecycles** (stop one SDK, start another). Two simultaneous live measurements on two SDK instances is not supported yet.

#### Binding a custom instance into a SwiftUI hierarchy

The SDK no longer ships SwiftUI views or environment-binding helpers. Custom instances flow into your views the same way any `@Observable` does — pass them directly, or define your own environment key:

```swift
@main
struct MyApp: App {
    @State private var sdk = SmartSpectraSDK(config: customConfig)

    var body: some Scene {
        WindowGroup {
            ContentView(sdk: sdk)
        }
    }
}
```

For an environment-key pattern, see `samples/demo-app/UI/SDKEnvironmentKey.swift` — a tiny helper that defines `\.smartSpectraSDK` and `.smartSpectraSDK(_:)` for the moved screening views to read against. Copy it if you want the same pattern in your own app.

Hosts that just use `SmartSpectraSDK.shared` need no binding at all.

## SwiftUI Surface Removal

The SDK no longer ships any SwiftUI views or environment helpers. Removed from the `SmartSpectra` module:

- `SmartSpectraView`, `SmartSpectraButtonView`, `SmartSpectraResultView`
- The screening overlay / plot / processing views
- Onboarding, tutorial, legal, and web views
- `ContinuousVitalsPlotView` and its `TraceLineView` / `VitalSection` helpers
- `StartupRecovery` helper
- `\.smartSpectraSDK` environment key and `.smartSpectraSDK(_:)` view modifier

### What's still public

- ``SmartSpectraSDK`` and ``SmartSpectraConfig`` — the data and lifecycle plane.
- The full observable surface: `metrics`, `imageOutput`, `processingStatus`, `validationStatus`, `error`, `insight`, plus `try await sdk.start() / sdk.stop()`.
- All proto types (`Metrics`, `Measurement`, `MeasurementWithConfidence`, etc.) and their `TimeStamped` / `appendProtoArray` extensions.

### What you need to do

If you used `SmartSpectraView()` as a one-line integration point, copy the reference implementation from the demo-app sample at [`samples/demo-app/UI/`](https://github.com/Presage-Security/SmartSpectra/tree/main/swift/samples/demo-app/UI) into your project. The folder mirrors the previous SDK layout (`Components/`, `Screening/`, `Legal/`, `Web/`) and wires through public-only SDK API:

- `ScreeningViewModel` calls `try await sdk.start()` / `sdk.stop()` instead of the (removed) `processor.startProcessing/stopProcessing`.
- `SDKExtensions.swift` recomputes `cardioMeasurementsEnabled` / `facialExpressionEnabled` from the public `requestedMetrics` surface.
- `SDKEnvironmentKey.swift` defines a sample-local `\.smartSpectraSDK` environment key + `.smartSpectraSDK(_:)` modifier — copy it if you want the same SwiftUI binding pattern.
- `StartupRecovery` takes an explicit `videoInputEnabled` argument; the host tracks it locally rather than reading SDK config.
- Brand color lives in `BrandColor.swift` — customize for your product theme.
- Tutorial images load from the host app's main bundle (no `bundle: .module`); the demo-app's `Assets.xcassets/tutorial_image*.imageset` entries can be copied as-is.

If you only used ``ContinuousVitalsPlotView``, the same folder includes a sample-local copy at `samples/demo-app/UI/Screening/ContinuousVitalsPlotView.swift` (with `TraceLineView.swift` and `VitalSection.swift`). Drop the three files into your project, add the `SDKExtensions.swift` derived flags, and the view works unchanged.

```swift
// Before:
import SwiftUI
import SmartSpectra

struct ContentView: View {
    private let sdk = SmartSpectraSDK.shared

    init() { sdk.config.apiKey = "…" }

    var body: some View {
        SmartSpectraView()
    }
}
```

```swift
// After (option 1 — keep the screening flow by copying the sample):
import SwiftUI
import SmartSpectra

struct ContentView: View {
    private let sdk = SmartSpectraSDK.shared

    init() { sdk.config.apiKey = "…" }

    var body: some View {
        // SmartSpectraView is now a sample-local view copied from
        // samples/demo-app/UI/Components/SmartSpectraView.swift.
        SmartSpectraView()
    }
}
```

```swift
// After (option 2 — bring your own UI, drive the SDK directly):
import SwiftUI
import SmartSpectra

struct ContentView: View {
    private let sdk = SmartSpectraSDK.shared

    init() { sdk.config.apiKey = "…" }

    var body: some View {
        VStack {
            if let image = sdk.imageOutput {
                Image(uiImage: image).resizable().aspectRatio(contentMode: .fit)
            }
            Text("Status: \(String(describing: sdk.processingStatus))")
            Button(sdk.processingStatus == .running ? "Stop" : "Start") {
                Task {
                    if sdk.processingStatus == .running {
                        try? await sdk.stop()
                    } else {
                        try? await sdk.start()
                    }
                }
            }
        }
    }
}
```

### Why this changed

The shipped UI embedded opinionated decisions every customer wanted to override (full-screen vs sheet presentation, onboarding policy, legal-document hosting URL, theming, plot styling). Maintaining that as stable SDK API forced every customer to fight the same set of defaults. Moving every SwiftUI piece into a sample lets each project fork the part it cares about while the SDK owns only the data and lifecycle plane.

## `@MainActor` Isolation

`SmartSpectraSDK` and `SmartSpectraConfig` are now `@MainActor`-isolated. Access from non-main contexts requires the standard hop:

```swift
// From a background task:
await MainActor.run {
    sdk.config.apiKey = "…"
}
```

SwiftUI `View` bodies, UIKit `UIViewController` methods, and `XCTestCase`-subclass methods marked `@MainActor` access the SDK directly without extra ceremony. Unit tests that mutate SDK or config state should annotate the test class with `@MainActor`.

## Metric bundles moved to `SmartSpectraConfig`

Older Swift releases did not expose public requested-metric bundles. Current
releases provide three public `nonisolated static let`s on
`SmartSpectraConfig`: `breathingMetrics`, `cardioMetrics`, `faceMetrics`. The
namespace and `Metrics` suffix match the Android SDK's
`SmartSpectraConfig.breathingMetrics` companion field and the C++ SDK's
`SmartSpectraConfig::CardioMetrics()` static method.

```swift
// Before
// No public requested-metric selection API.

// After
sdk.config.requestedMetrics =
    SmartSpectraConfig.breathingMetrics + SmartSpectraConfig.cardioMetrics
```
