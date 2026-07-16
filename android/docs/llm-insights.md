---
title: LLM Insights
description: Request and receive LLM Insights from the Android SmartSpectra SDK.
---

# Android LLM Insights

Platform-specific usage for the Android SDK. For what LLM Insights are, the
request/response model, required metrics, and the privacy notice, see the
[LLM Insights overview](../../docs/llm-insights/index.md).

The insight API lives on the `SmartSpectraSdk` singleton
(`SmartSpectraSdk.shared`).

## Enable the required metrics

Insights summarize the buffered vitals, so breathing (the default set) and
cardio must both be active. Assign both bundles to `requestedMetrics`:

```kotlin
sdk.config.requestedMetrics = buildList {
    addAll(SmartSpectraConfig.breathingMetrics)
    addAll(SmartSpectraConfig.cardioMetrics)
}
```

`cardioMetrics` includes `MetricType.ARTERIAL_PRESSURE_TRACE`, which drives the
on-screen pulse waveform. When `requestedMetrics` is unset the SDK measures
breathing only.

## Receive responses

Insights are delivered through a `LiveData`, observed on the **main thread**. It
carries **both** the auto-fired periodic vitals insights and on-demand
responses:

```kotlin
public val insight: LiveData<Insight?>
```

```kotlin
sdk.insight.observe(viewLifecycleOwner) { insight ->
    if (insight == null) return@observe
    if (insight.requestId != pendingRequestId) return@observe // correlate the reply
    val text = when {
        insight.hasAnalysis() -> insight.analysis
        else -> getString(R.string.insights_chat_error)
    }
    showMessage(text)
}
```

Match `insight.requestId` against the value returned by `requestInsight` to tell
on-demand replies apart from auto-fired vitals (which won't match a request you
made). Every insight is currently delivered with `type` == `INSIGHT_TYPE_VITALS`,
so correlate on `requestId`, not `type`.

## Request an insight

Call `requestInsight` on a running session. It returns the request ID used to
correlate the asynchronous response:

```kotlin
public fun requestInsight(text: String): Int
```

```kotlin
pendingRequestId = runCatching { sdk.requestInsight("Summarize the user's stress level.") }
    .onFailure { /* dispatch failed */ }
    .getOrNull()
```

The prompt is combined with the latest buffered metrics when they exist,
otherwise sent prompt-only.

## Reading the Insight

`Insight` is the generated proto type
(`com.presagetech.smartspectra.proto.InsightsProto.Insight`). Branch on
`insight.hasAnalysis()` / `insight.hasError()` (exactly one is set), read the
text with `insight.analysis` / `insight.error`, and correlate with
`insight.requestId`. (Every insight is currently typed `INSIGHT_TYPE_VITALS`, so
use `requestId`, not `type`, to distinguish replies.) Full field documentation is
in [Data Types → Insight](../../docs/data-types.md#insight).

The first auto-fired insight arrives about 15 seconds after the session starts;
allow that much valid measurement before an on-demand request can be grounded in
the user's physiology. The service may return no insight for a given request, in
which case the observer is not called.

## See also

- [LLM Insights overview](../../docs/llm-insights/index.md)
- [Android API reference](api-reference.md)
- [Data Types](../../docs/data-types.md)
