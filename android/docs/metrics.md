---
title: Configuring Metrics
description: Request and read SmartSpectra metrics from the Android SDK.
---

# Configuring Android Metrics

By default, Android measurements request the breathing metric set. Add pulse rate when your app needs a basic cardio value.

## Breathing and Pulse

### Request Metrics

Request the default breathing metrics plus `PULSE_RATE` before calling `start()`:

```kotlin
import com.presagetech.smartspectra.proto.MetricTypesProto.MetricType
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraSdk

val sdk = SmartSpectraSdk.shared
sdk.config.requestedMetrics =
    SmartSpectraConfig.breathingMetrics + listOf(MetricType.PULSE_RATE)
```

### Read Metrics

Read the latest breathing and pulse samples from `SmartSpectraSdk.metrics`:

```kotlin
sdk.metrics.observe(viewLifecycleOwner) { metrics ->
    val breathingRate = metrics?.breathing?.rateList?.lastOrNull()?.value
    val chestTrace = metrics?.breathing?.upperTraceList?.lastOrNull()?.value
    val abdomenTrace = metrics?.breathing?.lowerTraceList?.lastOrNull()?.value
    val pulseRate = metrics?.cardio?.pulseRateList?.lastOrNull()?.value
}
```

Set `requestedMetrics = null` to return to the default breathing-only set. Cardio fields are empty unless you request a cardio metric such as `PULSE_RATE`.

## Advanced

Request additional metrics only when your app needs them:

```kotlin
sdk.config.requestedMetrics =
    SmartSpectraConfig.breathingMetrics + listOf(
        MetricType.PULSE_RATE,
        MetricType.ARTERIAL_PRESSURE_TRACE,
        MetricType.HRV,
        MetricType.FACE_LANDMARKS,
        MetricType.BLINKING,
        MetricType.TALKING,
        MetricType.EXPRESSIONS,
    )
```

Read the advanced fields from the same metrics stream:

```kotlin
sdk.metrics.observe(viewLifecycleOwner) { metrics ->
    val pressureTrace = metrics?.cardio?.arterialPressureTraceList?.lastOrNull()?.value
    val hrvRmssd = metrics?.cardio?.hrvList?.lastOrNull()?.rmssd

    val faceLandmarks = metrics?.face?.landmarksList?.lastOrNull()?.valueList
    val blinking = metrics?.face?.blinkingList?.lastOrNull()?.detected
    val talking = metrics?.face?.talkingList?.lastOrNull()?.detected
    val expression = metrics?.face?.expressionList?.lastOrNull()
}
```

### Advanced Payload Classes

Android uses the generated protobuf classes. Requested advanced metrics populate these fields:

```kotlin
Metrics {
    breathing: Breathing
    face: Face
    cardio: Cardio
}

Cardio {
    pulseRateList: List<MeasurementWithConfidence>
    arterialPressureTraceList: List<MeasurementWithConfidence>
    hrvList: List<Hrv>
}

Hrv {
    rmssd: Double
    meanNn: Double
    sdnn: Double
    baevsky: Double
    timestamp: Long
    confidence: Float
}

Face {
    landmarksList: List<Landmarks>
    blinkingList: List<DetectionStatus>
    talkingList: List<DetectionStatus>
    expressionList: List<Expression>
}
```

See [Data Types](https://smartspectra.presagetech.com/docs/data-types) for the complete protobuf schema.
