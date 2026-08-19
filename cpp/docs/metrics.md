---
title: Configuring Metrics on C++
description: Request pulse, breathing, HRV and EDA metric groups from the SmartSpectra C++ SDK, and read the samples its callbacks deliver.
sidebarTitle: Configuring Metrics
---

# Configuring C++ Metrics

By default, C++ measurements request the breathing metric set. Add pulse rate when your app needs a basic cardio value.

## Breathing and Pulse

### Request Metrics

Request the breathing metrics plus `MetricType::PULSE_RATE` before constructing `SmartSpectra`:

```cpp
#include <smartspectra/messages/metric_types.pb.h>
#include <smartspectra/smartspectra_config.h>

namespace spectra = presage::smartspectra;
using presage::smartspectra::MetricType;

spectra::SmartSpectraConfig config;
config.api_key = "YOUR_API_KEY";
config.requested_metrics = spectra::SmartSpectraConfig::BreathingMetrics();
config.AddMetrics({MetricType::PULSE_RATE});
```

### Read Metrics

Read the latest breathing and pulse samples from `SetOnMetrics`. This complete
example includes the SDK construction and the pulse accessor:

```cpp
#include <smartspectra/messages/metrics.h>
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <glog/logging.h>
#include <utility>

namespace spectra = presage::smartspectra;

spectra::SmartSpectraConfig config;
config.api_key = "YOUR_API_KEY";
config.requested_metrics = spectra::SmartSpectraConfig::BreathingMetrics();
config.AddMetrics(spectra::SmartSpectraConfig::CardioMetrics());

spectra::SmartSpectra spectra(config);
spectra.SetOnMetrics([](const spectra::Metrics& metrics, int64_t) {
    if (metrics.has_breathing() && metrics.breathing().rate_size() > 0) {
        const auto& rate = metrics.breathing().rate(metrics.breathing().rate_size() - 1);
        LOG(INFO) << "Breathing rate: " << rate.value();
    }

    if (metrics.has_cardio() && metrics.cardio().pulse_rate_size() > 0) {
        const auto& pulse = metrics.cardio().pulse_rate(metrics.cardio().pulse_rate_size() - 1);
        LOG(INFO) << "Pulse rate: " << pulse.value();
    }
});
```

If `requested_metrics` is empty, the SDK uses `DefaultSupportedMetrics()`,
which returns `BreathingMetrics()`. Use `BreathingMetrics()` when you are
explicitly composing a breathing request. Cardio fields are empty unless you
request a cardio metric such as `PULSE_RATE`.

Requested metrics are validated against your subscription during SDK startup.
If a metric is not authorized it is omitted from the output — the field is
simply empty, with no error — so treat a persistently empty metric as a
possible authorization gap rather than a signal-quality problem. If the
authorization request itself fails, `Start()` reports an error.

## Metric Update Patterns

`SetOnMetrics` receives the latest SDK metrics payload. Each payload contains the
samples that became available since the previous metrics callback; it is not
guaranteed to contain every requested field every time.

| Metric category | Examples | Expected cadence | Empty behavior |
| --- | --- | --- | --- |
| Peak/event-driven rate metrics | `breathing().rate()`, `cardio().pulse_rate()`, `cardio().hrv()` | Updated when a new physiological event, cycle, or analysis window produces a value | Repeated fields may be empty between valid updates during active capture |
| Frame-driven metrics | `face().expression()`, `face().landmarks()`, `face().blinking()`, `face().talking()`, breathing traces | Updated near device frame cadence, with SDK callbacks rate-limited to about 30 Hz | Usually present more continuously when the metric is enabled and the input signal is valid |

For example, `metrics.cardio().pulse_rate_size()` and
`metrics.breathing().rate_size()` may be `0` between valid updates. This is
expected and does not mean capture stopped or the metric was disabled. By
contrast, face expression samples are frame-driven, so
`metrics.face().expression_size()` can be non-zero on most callbacks while face
metrics are enabled and the face signal is valid.

Recommended UI handling:

- Keep the last valid rate sample in app state and update it only when the
  repeated field contains a new sample.
- Show an initial loading or placeholder state until the first valid sample
  arrives.
- Do not overwrite a displayed pulse rate or breathing rate just because one
  metrics payload has no new sample.
- Clear retained values when a capture session starts, stops, or when your app
  intentionally changes the requested metric set.
- Prefer sample timestamps, and `stable()` when present, to decide whether a
  retained value is fresh enough for your UI.

```cpp
#include <optional>

std::optional<float> last_pulse_rate;

spectra.SetOnMetrics([&last_pulse_rate](const presage::smartspectra::Metrics& metrics, int64_t) {
    if (metrics.has_cardio() && metrics.cardio().pulse_rate_size() > 0) {
        const auto& pulse = metrics.cardio().pulse_rate(metrics.cardio().pulse_rate_size() - 1);
        if (pulse.timestamp() > 0) {
            last_pulse_rate = pulse.value();
            LOG(INFO) << "Pulse rate: " << *last_pulse_rate;
        }
    } else if (!last_pulse_rate.has_value()) {
        LOG(INFO) << "Pulse rate pending";
    }
});
```

## Advanced

Request additional metrics only when your app needs them:

```cpp
config.requested_metrics = spectra::SmartSpectraConfig::BreathingMetrics();
config.AddMetrics({
    MetricType::PULSE_RATE,
    MetricType::ARTERIAL_PRESSURE_TRACE,
    MetricType::HRV,
    MetricType::EDA_TRACE,
    MetricType::FACE_LANDMARKS,
    MetricType::BLINKING,
    MetricType::TALKING,
    MetricType::EXPRESSIONS,
});
```

Read the advanced fields from the same metrics callback:

```cpp
spectra.SetOnMetrics([](const presage::smartspectra::Metrics& metrics, int64_t) {
    if (metrics.has_cardio() && metrics.cardio().arterial_pressure_trace_size() > 0) {
        const auto& pressure = metrics.cardio().arterial_pressure_trace(
            metrics.cardio().arterial_pressure_trace_size() - 1);
        LOG(INFO) << "Arterial pressure trace: " << pressure.value();
    }

    if (metrics.has_cardio() && metrics.cardio().hrv_size() > 0) {
        const auto& hrv = metrics.cardio().hrv(metrics.cardio().hrv_size() - 1);
        LOG(INFO) << "HRV RMSSD: " << hrv.rmssd();
    }

    if (metrics.has_eda() && metrics.eda().trace_size() > 0) {
        const auto& eda = metrics.eda().trace(metrics.eda().trace_size() - 1);
        LOG(INFO) << "EDA trace: " << eda.value();
    }

    if (metrics.has_face() && metrics.face().landmarks_size() > 0) {
        const auto& landmarks = metrics.face().landmarks(metrics.face().landmarks_size() - 1);
        LOG(INFO) << "Face landmark count: " << landmarks.value_size();
    }
    if (metrics.has_face() && metrics.face().blinking_size() > 0) {
        const auto& blinking = metrics.face().blinking(metrics.face().blinking_size() - 1);
        LOG(INFO) << "Blinking: " << blinking.detected();
    }
    if (metrics.has_face() && metrics.face().talking_size() > 0) {
        const auto& talking = metrics.face().talking(metrics.face().talking_size() - 1);
        LOG(INFO) << "Talking: " << talking.detected();
    }
    if (metrics.has_face() && metrics.face().expression_size() > 0) {
        const auto& expression = metrics.face().expression(metrics.face().expression_size() - 1);
        LOG(INFO) << "Expression score count: " << expression.scores_size();
    }

});
```

### Advanced Payload Classes

C++ uses the generated protobuf classes. Requested advanced metrics populate these fields:

```cpp
presage::smartspectra::Metrics {
    Breathing breathing;
    Eda eda;
    Face face;
    Cardio cardio;
}

Cardio {
    repeated MeasurementWithConfidence pulse_rate;
    repeated MeasurementWithConfidence arterial_pressure_trace;
    repeated Hrv hrv;
}

Hrv {
    double rmssd;
    double mean_nn;
    double sdnn;
    double baevsky;
    int64 timestamp;
    float confidence;
    bool stable;
}

Eda {
    repeated Measurement trace;
}

Face {
    repeated Landmarks landmarks;
    repeated DetectionStatus blinking;
    repeated DetectionStatus talking;
    repeated Expression expression;
}
```

EDA may take longer to produce its first sample than breathing or cardio outputs. See [Data Types](../../docs/data-types.md) for the complete protobuf schema.

## Timing and Stability

All measurement samples use `timestamp` values in microseconds. Trace metrics
are produced at frame cadence when the underlying signal is available; lower
rate outputs such as EDA may arrive less frequently.

Measurement types expose a `stable()` flag. Check it before using a sample for
critical decisions or user-facing summaries:

```cpp
if (metrics.has_breathing() && metrics.breathing().rate_size() > 0) {
    const auto& rate = metrics.breathing().rate(metrics.breathing().rate_size() - 1);
    if (rate.stable()) {
        LOG(INFO) << "Stable breathing rate: " << rate.value();
    }
}
```

Face landmark samples also expose `reset()`, which indicates that landmark
tracking was reinitialized:

```cpp
if (metrics.has_face() && metrics.face().landmarks_size() > 0) {
    const auto& landmarks = metrics.face().landmarks(metrics.face().landmarks_size() - 1);
    if (landmarks.reset()) {
        LOG(INFO) << "Face landmark tracking reset";
    }
}
```

## Serialization

Metrics are protobuf messages. Serialize them directly when you need to persist
or transmit the exact SDK payload:

```cpp
std::string binary;
if (metrics.SerializeToString(&binary)) {
    writeMetrics(binary);
}
```

For diagnostics, convert to JSON with protobuf utilities:

```cpp
#include <google/protobuf/util/json_util.h>

std::string json;
google::protobuf::util::JsonPrintOptions options;
options.preserve_proto_field_names = true;

auto status = google::protobuf::util::MessageToJsonString(metrics, &json, options);
if (status.ok()) {
    LOG(INFO) << json;
}
```
