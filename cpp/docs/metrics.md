---
title: Configuring Metrics
description: Request and read SmartSpectra metrics from the C++ SDK.
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

Read the latest breathing and pulse samples from `SetOnMetrics`:

```cpp
#include <smartspectra/messages/metrics.h>
#include <smartspectra/smartspectra.h>
#include <glog/logging.h>
#include <utility>

spectra::SmartSpectra spectra(config);
spectra.SetOnMetrics([](const presage::smartspectra::Metrics& metrics, int64_t) {
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
If a metric is not authorized, it is omitted from the output. If the
authorization request fails, `Start()` reports an error.

## Advanced

Request additional metrics only when your app needs them:

```cpp
config.requested_metrics = spectra::SmartSpectraConfig::BreathingMetrics();
config.AddMetrics({
    MetricType::PULSE_RATE,
    MetricType::ARTERIAL_PRESSURE_TRACE,
    MetricType::HRV,
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
}

Face {
    repeated Landmarks landmarks;
    repeated DetectionStatus blinking;
    repeated DetectionStatus talking;
    repeated Expression expression;
}
```

See [Data Types](https://smartspectra.presagetech.com/docs/data-types) for the complete protobuf schema.

## Timing and Stability

All measurement samples use `timestamp` values in microseconds. Trace metrics
are produced at frame cadence when the underlying signal is available.

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
