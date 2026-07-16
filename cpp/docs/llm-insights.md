---
title: LLM Insights
description: Request and receive LLM Insights from the C++ SmartSpectra SDK.
---

# C++ LLM Insights

Platform-specific usage for the C++ SDK. For what LLM Insights are, the
request/response model, required metrics, and the privacy notice, see the
[LLM Insights overview](../../docs/llm-insights/index.md).

## Enable the required metrics

Insights summarize the buffered vitals, so breathing (the default set) and
cardio must both be active:

```cpp
spectra::SmartSpectraConfig config;
config.api_key = my_api_key;
config.AddMetrics(spectra::SmartSpectraConfig::DefaultSupportedMetrics()); // breathing defaults
config.AddMetrics(spectra::SmartSpectraConfig::CardioMetrics());           // pulse, HRV, arterial pressure trace
```

`CardioMetrics()` includes `ARTERIAL_PRESSURE_TRACE`, which drives the on-screen
pulse waveform.

## Receive responses

Register a callback before starting. It receives **both** the auto-fired
periodic vitals insights and on-demand responses; correlate an on-demand reply
by matching `Insight::request_id()` against the ID you got from `RequestInsight`.

```cpp
using presage::smartspectra::Insight;

std::mutex insight_mutex;
smart_spectra.SetOnInsight([&](const Insight& insight) {
  std::lock_guard<std::mutex> lock(insight_mutex); // callback runs on a background thread
  if (insight.has_analysis()) {
    // insight.analysis() — the LLM text; insight.request_id() correlates the reply
  } else if (insight.has_error()) {
    // insight.error() — failure message
  }
});
```

`OnInsightFn` is `std::function<void(const Insight&)>`. The callback is invoked
on a **background thread**, so synchronize any state it shares with your
application.

## Request an insight

Call `RequestInsight` on a running session. The response arrives asynchronously
through the callback above; correlate it via `Insight::request_id()`.

```cpp
[[nodiscard]] SmartSpectraError RequestInsight(
    const std::string& text,
    int32_t* out_request_id = nullptr);
```

```cpp
int32_t request_id = 0;
if (const auto err = smart_spectra.RequestInsight("Summarize the user's stress level.", &request_id);
    !err.ok()) {
  std::cerr << err.FullMessage() << '\n';
}
```

- `text` — the prompt. Combined with the latest buffered metrics when they exist,
  otherwise sent prompt-only.
- `out_request_id` — if non-null, receives the request ID for correlation.
- Returns `kInvalidState` (no active session) or `kProcessingFailed` (dispatch
  failed — e.g. no insight callback registered, or a server error).

## Reading the Insight

Branch on `has_analysis()` / `has_error()` (exactly one is set), read the text
with `analysis()` / `error()`, and correlate with `request_id()`. Every insight
is currently delivered with `type()` == `INSIGHT_TYPE_VITALS` (`SPEECH` and
`COMBINED` are reserved), so use `request_id()`, not `type()`, to distinguish
on-demand replies from auto-fired vitals. Full field documentation is in
[Data Types → Insight](../../docs/data-types.md#insight).

The first auto-fired insight arrives about 15 seconds after the session starts;
allow that much valid measurement before an on-demand request can be grounded in
the user's physiology.

## See also

- [LLM Insights overview](../../docs/llm-insights/index.md)
- [C++ API reference](api-reference.md)
- [Data Types](../../docs/data-types.md)
