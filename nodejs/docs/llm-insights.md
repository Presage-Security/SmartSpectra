---
title: LLM Insights on Node.js
description: Ask natural-language questions about a measurement and receive LLM Insights through the SmartSpectra Node.js SDK, alongside the metrics stream.
sidebarTitle: LLM Insights
---

# Node.js LLM Insights

Platform-specific usage for the Node.js SDK (`@smartspectra/node-sdk`). For what
LLM Insights are, the request/response model, required metrics, and the privacy
notice, see the [LLM Insights overview](../../docs/llm-insights/index.md).

The main-process (`@smartspectra/node-sdk`) and Electron renderer
(`@smartspectra/node-sdk/renderer`) entry points expose the same API; only the
call/return shapes differ (noted below).

## Enable the required metrics

Insights summarize the buffered vitals, so breathing (the default set) and
cardio must both be active. Select them via `requestedMetrics`:

```js
import { SmartSpectraSDK, breathingMetrics, cardioMetrics } from '@smartspectra/node-sdk';

const sdk = new SmartSpectraSDK({
  apiKey: 'YOUR_KEY',
  requestedMetrics: [...breathingMetrics, ...cardioMetrics],
});
```

`cardioMetrics` includes `ARTERIAL_PRESSURE_TRACE`, which drives the pulse
waveform. Omitting `requestedMetrics` measures breathing only.

## Request an insight

Call `requestInsight` on a configured session. It returns the request ID used to
correlate the asynchronous response:

```js
// main process:      requestInsight(text: string): number
// renderer process:  requestInsight(text: string): Promise<number>
const requestId = sdk.requestInsight('Summarize my current vital signs and flag anything unusual.'); // await in the renderer
```

The prompt is combined with the latest buffered metrics when they exist,
otherwise sent prompt-only.

## Receive responses

Register a single `'insight'` callback. It receives **both** the auto-fired
periodic vitals insights and on-demand responses:

```js
sdk.on('insight', (buf, requestId) => {
  // buf is a serialized `presage.smartspectra.Insight` protobuf.
  // The Node SDK does not ship a decoder — decode it with your own protobuf
  // type built from the Insight schema (see Data Types below).
  const insight = Insight.decode(buf); // e.g. a protobufjs-generated type you supply
  const text = insight.analysis || insight.error;
});
```

- `buf` is the serialized `Insight` message — a `Buffer` in the main process, a
  `Uint8Array` in the renderer.
- `requestId` is also passed as a plain number, so you can route responses
  without decoding.
- `on('insight', …)` registers a single callback (a second call replaces it).

## Reading the Insight

Decode `buf` using the [Insight schema](../../docs/data-types.md#insight), then
read the fields. With a protobufjs type the fields are camelCase — `analysis`
and `error` are a `result` oneof (exactly one is set), with `requestId`,
`processedAt`, and `type` alongside. Every insight is currently delivered with
`type` == `INSIGHT_TYPE_VITALS`, so correlate on-demand replies via `requestId`,
not `type`.

The first auto-fired insight arrives about 15 seconds after processing starts;
allow that much valid measurement before an on-demand request can be grounded in
the user's physiology. Note that the service may return no insight for a given
request (for example when nothing new is worth surfacing), in which case the
callback does not fire.

## See also

- [LLM Insights overview](../../docs/llm-insights/index.md)
- [Node.js API reference](api-reference.md)
- [Data Types](../../docs/data-types.md)
