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

## Advanced

Request additional metrics only when your app needs them:

```swift
sdk.config.requestedMetrics = SmartSpectraConfig.breathingMetrics + [
    .pulseRate,
    .arterialPressureTrace,
    .hrv,
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

Face {
    landmarks: [Landmarks]
    blinking: [DetectionStatus]
    talking: [DetectionStatus]
    expression: [Expression]
}
```

See [Data Types](https://smartspectra.presagetech.com/docs/data-types) for the complete protobuf schema.
