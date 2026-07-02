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

## Metric Update Patterns

`SmartSpectraSdk.metrics` publishes the latest SDK metrics payload. Each payload
contains the samples that became available since the previous metrics update; it
is not guaranteed to contain every requested field every time.

| Metric category | Examples | Expected cadence | Empty/null behavior |
| --- | --- | --- | --- |
| Peak/event-driven rate metrics | `breathing.rateList`, `cardio.pulseRateList`, `cardio.hrvList` | Updated when a new physiological event, cycle, or analysis window produces a value | Lists may be empty between valid updates during active capture |
| Frame-driven metrics | `face.expressionList`, `face.landmarksList`, `face.blinkingList`, `face.talkingList`, breathing trace lists | Updated near device frame cadence, with SDK callbacks rate-limited to about 30 Hz | Usually present more continuously when the metric is enabled and the input signal is valid |

For example, `metrics?.cardio?.pulseRateList?.lastOrNull()?.value` and
`metrics?.breathing?.rateList?.lastOrNull()?.value` may temporarily evaluate to
`null` between valid updates. This is expected and does not mean capture stopped
or the metric was disabled. By contrast, face expression samples are frame-driven,
so `metrics?.face?.expressionList?.lastOrNull()` can appear continuously while
face metrics are enabled and the face signal is valid.

Recommended UI handling:

- Keep the last valid rate sample in app state and update it only when the list
  contains a new sample.
- Show an initial loading or placeholder state until the first valid sample
  arrives.
- Do not overwrite a displayed pulse rate or breathing rate with `null` only
  because one metrics payload has no new sample.
- Clear retained values when a capture session starts, stops, or when your app
  intentionally changes the requested metric set.
- Prefer sample timestamps, and `stable` when present, to decide whether a
  retained value is fresh enough for your UI.

```kotlin
var lastPulseRate: Double? = null

sdk.metrics.observe(viewLifecycleOwner) { metrics ->
    val pulse = metrics
        ?.cardio
        ?.pulseRateList
        ?.lastOrNull { it.timestamp > 0 }
        ?.value

    if (pulse != null) {
        lastPulseRate = pulse
        pulseRateLabel.text = String.format(Locale.ROOT, "%.0f bpm", pulse)
    } else if (lastPulseRate == null) {
        pulseRateLabel.text = "-- bpm"
    }
}
```

## Advanced

Request additional metrics only when your app needs them:

```kotlin
sdk.config.requestedMetrics =
    SmartSpectraConfig.breathingMetrics + listOf(
        MetricType.PULSE_RATE,
        MetricType.ARTERIAL_PRESSURE_TRACE,
        MetricType.HRV,
        MetricType.EDA_TRACE,
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

    val edaTrace = metrics?.eda?.traceList?.lastOrNull()?.value

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
    eda: Eda
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

Eda {
    traceList: List<Measurement>
}

Face {
    landmarksList: List<Landmarks>
    blinkingList: List<DetectionStatus>
    talkingList: List<DetectionStatus>
    expressionList: List<Expression>
}
```

EDA may take longer to produce its first sample than breathing or cardio outputs. See [Data Types](../../docs/data-types.md) for the complete protobuf schema.
