---
title: Configuring Metrics
description: Request and read SmartSpectra metrics from the Swift SDK.
---

# Configuring iOS Metrics

By default, Swift SDK measurements request the breathing metric set. Add pulse rate when your app needs a basic cardio value.

## Breathing and Pulse

### Request Metrics

Request the default breathing metrics plus `.pulseRate` before calling `start()`:

```swift
import SmartSpectra

let sdk = SmartSpectraSDK.shared
sdk.config.requestedMetrics = SmartSpectraConfig.breathingMetrics + [.pulseRate]
```

### Read Metrics

Read the latest breathing and pulse samples from `SmartSpectraSDK.metrics`:

```swift
private let sdk = SmartSpectraSDK.shared

if let metrics = sdk.metrics {
    let breathingRate = metrics.breathing.rate.last?.value
    let chestTrace = metrics.breathing.upperTrace.last?.value
    let abdomenTrace = metrics.breathing.lowerTrace.last?.value
    let pulseRate = metrics.cardio.pulseRate.last?.value
}
```

Set `requestedMetrics = nil` to return to the default breathing-only set. Cardio fields are empty unless you request a cardio metric such as `.pulseRate`.

## Metric Update Patterns

`SmartSpectraSDK.metrics` exposes the latest SDK metrics payload. Each payload
contains the samples that became available since the previous metrics update; it
is not guaranteed to contain every requested field every time.

| Metric category | Examples | Expected cadence | Empty/null behavior |
| --- | --- | --- | --- |
| Peak/event-driven rate metrics | `metrics.breathing.rate`, `metrics.cardio.pulseRate`, `metrics.cardio.hrv` | Updated when a new physiological event, cycle, or analysis window produces a value | Arrays may be empty between valid updates during active capture |
| Frame-driven metrics | `metrics.face.expression`, `metrics.face.landmarks`, `metrics.face.blinking`, `metrics.face.talking`, breathing traces | Updated near device frame cadence, with SDK callbacks rate-limited to about 30 Hz | Usually present more continuously when the metric is enabled and the input signal is valid |

For example, `sdk.metrics?.cardio.pulseRate.last?.value` and
`sdk.metrics?.breathing.rate.last?.value` may temporarily evaluate to `nil`
between valid updates. This is expected and does not mean capture stopped or the
metric was disabled. By contrast, face expression samples are frame-driven, so
`sdk.metrics?.face.expression.last` can appear continuously while face metrics
are enabled and the face signal is valid.

Recommended UI handling:

- Keep the last valid rate sample in app state and update it only when the array
  contains a new sample.
- Show an initial loading or placeholder state until the first valid sample
  arrives.
- Do not overwrite a displayed pulse rate or breathing rate with `nil` only
  because one metrics payload has no new sample.
- Clear retained values when a capture session starts, stops, or when your app
  intentionally changes the requested metric set.
- Prefer sample timestamps, and `stable` when present, to decide whether a
  retained value is fresh enough for your UI.

```swift
private var lastPulseRate: Double?

func pulseRateText(from metrics: Metrics?) -> String {
    if let pulse = metrics?.cardio.pulseRate.last(where: { $0.timestamp > 0 })?.value {
        lastPulseRate = pulse
        return "\(Int(pulse.rounded())) bpm"
    }

    guard let lastPulseRate else { return "-- bpm" }
    return "\(Int(lastPulseRate.rounded())) bpm"
}
```

## Advanced

Request additional metrics only when your app needs them:

```swift
sdk.config.requestedMetrics = SmartSpectraConfig.breathingMetrics + [
    .pulseRate,
    .arterialPressureTrace,
    .hrv,
    .edaTrace,
    .faceLandmarks,
    .blinking,
    .talking,
    .expressions,
]
```

Read the advanced fields from the same metrics object:

```swift
if let metrics = sdk.metrics {
    let pressureTrace = metrics.cardio.arterialPressureTrace.last?.value
    let hrvRmssd = metrics.cardio.hrv.last?.rmssd

    let edaTrace = metrics.eda.trace.last?.value

    let faceLandmarks = metrics.face.landmarks.last?.value
    let blinking = metrics.face.blinking.last?.detected
    let talking = metrics.face.talking.last?.detected
    let expression = metrics.face.expression.last
}
```

### Advanced Payload Types

The Swift SDK uses the generated Swift protobuf types. Requested advanced metrics populate these fields:

```swift
Metrics {
    breathing: Breathing
    eda: Eda
    face: Face
    cardio: Cardio
}

Cardio {
    pulseRate: [MeasurementWithConfidence]
    arterialPressureTrace: [MeasurementWithConfidence]
    hrv: [Hrv]
}

Hrv {
    rmssd: Double
    meanNn: Double
    sdnn: Double
    baevsky: Double
    timestamp: Int64
    confidence: Float
}

Eda {
    trace: [Measurement]
}

Face {
    landmarks: [Landmarks]
    blinking: [DetectionStatus]
    talking: [DetectionStatus]
    expression: [Expression]
}
```

EDA may take longer to produce its first sample than breathing or cardio outputs. See [Data Types](https://smartspectra.presagetech.com/docs/data-types) for the complete protobuf schema.
