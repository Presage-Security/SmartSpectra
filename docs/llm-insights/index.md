---
title: LLM Insights Overview
sidebarTitle: Overview
description: Conceptual overview, common API interface, required configuration, and privacy notice for the SmartSpectra LLM Insights feature.
---

# LLM Insights

LLM Insights turn the physiological metrics the SDK computes on-device (pulse,
heart-rate variability, breathing) into natural-language analysis produced by a
large language model. The SDK buffers a rolling window of metrics, sends that
window — optionally with a prompt you supply — to the Presage 3.0 Analytics
Gateway, and delivers the model's reply to your application as an `Insight`.

This page is the primary reference for the feature: the concepts, the common API
shape, configuration, and privacy. For exact signatures and runnable examples,
follow the link to your platform's guide.

## How it works

LLM Insights follow a request/response model. There are two ways a request is
dispatched, and both deliver their response through the same sink:

- **Auto-fired vitals** — once a session is running, the SDK automatically
  dispatches a vitals snapshot of the accumulated metrics buffer every
  **15 seconds of processing** (no prompt).
- **On-demand** — you request an insight with a prompt. If the metrics buffer
  already holds vitals at dispatch time, the request is **combined** (your
  prompt plus the latest metrics); if the buffer is still empty, it is
  **prompt-only**.

The physiological metrics are what make the analysis specific to the user: the
model receives the buffered metric series, not just your prompt text, so it can
ground its response in the measured pulse, HRV, and breathing. Responses are
asynchronous — there is no blocking call that returns an analysis inline.

## Common API interface

Every platform exposes the same two operations, in a platform-idiomatic form:

1. **Request an insight** — submit a prompt on a running session. It returns a
   **request ID** that identifies the reply. The prompt is combined with the
   latest buffered metrics when they exist, otherwise sent prompt-only.
2. **Receive responses** — register a single sink that receives **all** insight
   responses (both auto-fired vitals and on-demand replies). Each response is an
   `Insight` carrying either `analysis` (success) or `error` (failure), plus a
   `request_id`. Correlate an on-demand reply with the request that produced it
   by matching its `request_id` (auto-fired vitals won't match a request you
   made). Every insight is currently delivered with type `INSIGHT_TYPE_VITALS`
   (`SPEECH`/`COMBINED` are reserved), so use `request_id`, not `type`, to tell
   them apart. The `Insight` type is documented in
   [Data Types → Insight](../data-types.md#insight).

How each platform surfaces these:

| Platform | Request | Receive responses |
| --- | --- | --- |
| [C++](../../cpp/docs/llm-insights.md) | `RequestInsight(text, &request_id)` | `SetOnInsight(callback)` |
| [Android](../../android/docs/llm-insights.md) | `requestInsight(text): Int` | `insight: LiveData<Insight?>` |
| [Swift](../../swift/docs/llm-insights.md) | `requestInsight(_:) throws -> Int32` | observable `insight` property |
| [Node.js](../../nodejs/docs/llm-insights.md) | `requestInsight(text): number` | `on('insight', …)` event |

See your platform's guide for exact signatures, setup, and examples:
[C++](../../cpp/docs/llm-insights.md) ·
[Android](../../android/docs/llm-insights.md) ·
[Swift](../../swift/docs/llm-insights.md) ·
[Node.js](../../nodejs/docs/llm-insights.md).

For guidance on writing prompts the model can act on, see
[Writing Effective Prompts](writing-prompts.md).

When a request fails to send or a response carries an error, see
[LLM Insights: Error Handling & Troubleshooting](troubleshooting.md)
for the two error surfaces, common failure modes, retry guidance, and UI
patterns.

## Required metrics configuration

Insights are only meaningful when the metrics they summarize are being computed:

- **Breathing (defaults) and cardio must both be active.** The SDK buffers pulse
  rate, HRV, and breathing rate for the insight payload, so those metric groups
  must be enabled. Each platform guide shows the exact configuration call.
- **`ARTERIAL_PRESSURE_TRACE` is available for visualization.** It drives the
  on-screen pulse waveform and is part of the cardio metrics.
- **Allow warm-up time before insights are meaningful.** The metrics buffer
  starts empty and fills as valid measurement accumulates. The first auto-fired
  vitals insight is dispatched about **15 seconds** after the session starts,
  and an on-demand request made before the buffer has filled is sent prompt-only
  (no metrics). Wait for at least that long before expecting analysis grounded
  in the user's physiology.

## Privacy & data notice

When an insight is dispatched, the SDK sends the following off-device to
Presage, which forwards it to the LLM:

- The **buffered metric values** — an allowlisted subset of the computed
  metrics (pulse rate, heart-rate variability, and breathing rate) as a
  columnar series.
- The **prompt text** you supply, when present.
- Request metadata (a session ID, request origin, and request ID).

**Raw video and facial imagery are never transmitted** — only the derived
numeric metric values and your prompt leave the device for this feature.

> **Important:** LLM Insights are generated by a large language model and can be inaccurate, incomplete, or misleading — language models sometimes produce confident but false statements ("hallucinations"). Treat every insight as general wellness information, not medical advice, and confirm anything important before acting on it. The SDK metrics themselves are offered for general wellness and informational purposes only; they have not been cleared by the FDA and may not be used for medical diagnosis or treatment.

## Getting Help

- Email: [support@presagetech.com](mailto:support@presagetech.com)
- [Submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues)
- [Docs and FAQ](https://smartspectra.presagetech.com)
- [Developer Admin Portal](https://physiology.presagetech.com/auth/login)
