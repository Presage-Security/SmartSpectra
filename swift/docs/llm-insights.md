---
title: LLM Insights on Swift
description: Ask natural-language questions about a measurement and receive LLM Insights through the SmartSpectra Swift SDK, alongside the metrics stream.
sidebarTitle: LLM Insights
---

# Swift LLM Insights

Platform-specific usage for the Swift SDK. For what LLM Insights are, the
request/response model, required metrics, and the privacy notice, see the
[LLM Insights overview](../../docs/llm-insights/index.md).

## Enable the required metrics

Insights summarize the buffered vitals, so breathing (the default set) and
cardio must both be active. Assign both bundles to `requestedMetrics`:

```swift
sdk.config.requestedMetrics =
    SmartSpectraConfig.breathingMetrics + SmartSpectraConfig.cardioMetrics
```

`cardioMetrics` includes `.arterialPressureTrace`, which drives the on-screen
pulse waveform. When `requestedMetrics` is left unset the SDK measures breathing
only.

## Receive responses

`SmartSpectraSDK` is `@Observable` (`@MainActor`). The latest insight is exposed
as an observable property, updated on the main actor:

```swift
public internal(set) var insight: Insight?
```

Observe it like any other observable state — read it in a SwiftUI `body`, or
react to changes:

```swift
.onChange(of: sdk.insight) { _, insight in
    guard let insight else { return }
    switch insight.result {
    case .analysis(let text): // the LLM text
        show(text)
    case .error:              // insight.error holds the message
        show("The insights service is currently unavailable.")
    case .none:
        break
    }
}
```

The same property delivers **both** the auto-fired periodic vitals insights and
on-demand responses. Match `insight.requestID` against the value returned by
`requestInsight` to tell them apart (auto-fired vitals won't match a request you
made). Every insight is currently delivered with `type == .vitals`, so correlate
on `requestID`, not `type`.

## Request an insight

Call `requestInsight` on a running session (it throws otherwise). It returns the
request ID used to correlate the asynchronous response:

```swift
@discardableResult
public func requestInsight(_ text: String) throws -> Int32
```

```swift
do {
    pendingRequestId = try sdk.requestInsight("Summarize my current vital signs and flag anything unusual.")
} catch {
    // e.g. processing not active
}
```

The prompt is combined with the latest buffered metrics when they exist,
otherwise sent prompt-only.

## Reading the Insight

`Insight` is a generated SwiftProtobuf struct. There are **no** `hasAnalysis` /
`hasError` accessors — switch on the `result` oneof:

- `insight.result` → `.analysis(String)` on success, `.error(String)` on failure,
  or `nil`. (The convenience `insight.analysis` / `insight.error` properties
  return `""` for the case that isn't set.)
- `insight.requestID` (`Int32`, note the capital `ID`) correlates the reply.
- `insight.type` is currently always `.vitals` (the `.speech` and `.combined`
  cases are reserved and not emitted today).

Full field documentation is in
[Data Types → Insight](../../docs/data-types.md#insight).

The first auto-fired insight arrives about 15 seconds after the session starts;
allow that much valid measurement before an on-demand request can be grounded in
the user's physiology.

## See also

- [LLM Insights overview](../../docs/llm-insights/index.md)
- [Swift API reference](api-reference.md)
- [Data Types](../../docs/data-types.md)
