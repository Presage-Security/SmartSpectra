---
title: Troubleshooting LLM Insights Errors
description: The two LLM Insights error surfaces, common failure modes, retry guidance, and how to surface errors in your UI.
---

# LLM Insights: Error Handling & Troubleshooting

LLM Insights can fail in two distinct places, and telling them apart is the key
to handling them correctly. This page covers the two error surfaces, the common
failure modes and which surface each shows up on, how to retry, and how to
surface errors in your UI. For the feature itself — concepts, the
request/response model, configuration, and privacy — see the
[LLM Insights overview](index.md).

## The two error surfaces

| Surface | When | Meaning | Delivered as |
| --- | --- | --- | --- |
| **1. Request failed to send** | Synchronously, from the request call | The request never left the device | A non-OK status / thrown error from `RequestInsight` |
| **2. Response carries an error** | Asynchronously, on the response sink | The request was sent, but the server failed | An `Insight` with `error` set instead of `analysis` |

> **A successful request call does not mean the insight was delivered.** The
> network request is asynchronous: `RequestInsight` returns as soon as the request
> is *enqueued*, not when the server replies. Anything that goes wrong on the
> wire — connectivity, timeouts, rate limits, server errors — comes back later
> on **Surface 2**, not from the request call.

### Surface 1 — the request failed to send

This surface is synchronous and local: the request was rejected before it was
dispatched, so no response will ever arrive for it. In the C++ SDK the call
returns a `SmartSpectraError`; the exact codes are:

- **`kInvalidState`** — there is no active session. Call `Start()` first (or the
  session was already stopped).
- **`kProcessingFailed`** — the request could not be dispatched. Causes: the
  prompt exceeds the **2048-byte** size limit (see
  [Writing prompts](writing-prompts.md#keep-in-mind)); no response sink was
  registered before requesting; the SDK could not reach the server to establish
  the session (this is the one case where a *network* failure surfaces
  synchronously); or the session is shutting down.

How each platform reports a failed request:

| Platform | Mechanism |
| --- | --- |
| [C++](../../cpp/docs/llm-insights.md) | `RequestInsight` returns `SmartSpectraError` — check `err.ok()`, read `err.FullMessage()` |
| [Android](../../android/docs/llm-insights.md) | `requestInsight` throws — wrap in `runCatching { … }.onFailure { … }` |
| [Swift](../../swift/docs/llm-insights.md) | `requestInsight(_:) throws` — use `do { try … } catch { … }` |
| [Node.js](../../nodejs/docs/llm-insights.md) | `requestInsight` throws — use `try { … } catch { … }` |

### Surface 2 — the response carries an error

This surface is asynchronous: the request was sent and the reply arrived on your
response sink, but it carries an `error` message instead of `analysis`. The
`analysis` and `error` fields are a `result` oneof — **exactly one is set** —
so branch on which is present. The error string typically includes an HTTP
status code (for example, `429` or `503`), or a transport error.

How each platform reports a response-level error:

| Platform | Mechanism |
| --- | --- |
| [C++](../../cpp/docs/llm-insights.md) | `insight.has_error()` → `insight.error()` |
| [Android](../../android/docs/llm-insights.md) | `insight.hasError()` → `insight.error` |
| [Swift](../../swift/docs/llm-insights.md) | `insight.result` → `.error(String)` (or `insight.error`) |
| [Node.js](../../nodejs/docs/llm-insights.md) | decode the buffer → `insight.error` (the `result` oneof) |

### A third outcome: no response at all

A request can also produce **no response** — the sink is simply never called.
This is not an error; it is how the service signals "nothing to analyze" (for
example, when a session has already produced its analysis and a further request
adds nothing). Because there is no callback and no error, your UI must not wait
forever — see
[Retry patterns and best practices](#retry-patterns-and-best-practices).

## Response error status codes

When a response carries an `error` (Surface 2), the message includes an
HTTP status. Use it to decide whether to retry:

| Status | Meaning | Retry? |
| --- | --- | --- |
| `400` | Malformed request payload | No — fix the request |
| `401` | API key or OAuth setup is missing or invalid | No — fix your credentials |
| `403` | Key is not authorized for this resource | No — check account access |
| `404` / `410` | The insights session expired | Retry |
| `408` | The server timed out handling the request | Yes — back off and retry |
| `429` | Quota or credits exhausted, or rate limited | Back off; if out of credits, top up |
| `500` / `502` / `503` / `504` | Internal server error | Yes — retry with backoff |

`4xx` statuses mean the request or account needs attention — retrying unchanged
won't help, except `404` / `410`, which are safe to retry.
`5xx` statuses are server-side and transient. An out-of-credits `429` is fixed by
topping up in the [Developer Admin Portal](https://physiology.presagetech.com/auth/login).

Not every failure has an HTTP status: a transport-level failure (connection
refused, host unreachable, client-side timeout), or an error reporting that the
server returned no analysis, also arrives as an `error` — treat both as
transient and retry with backoff.

## Common failure modes

| Failure mode | Surface | What you observe | What to do |
| --- | --- | --- | --- |
| No active session | 1 | `kInvalidState` / thrown | `Start()` a session before requesting |
| Prompt exceeds the size limit | 1 | `kProcessingFailed` / thrown | Shorten the prompt to **≤ 2048 bytes** (see [Writing prompts](writing-prompts.md#keep-in-mind)) |
| No response sink registered | 1 | `kProcessingFailed` / thrown | Register the sink **before** the first request |
| Can't reach server to establish the session | 1 | `kProcessingFailed` / thrown | Check connectivity and API key; safe to retry |
| Network unavailable / server unreachable (after dispatch) | 2 | `error` with a transport message | Retry with backoff |
| Request timed out | 2 | `error` (times out after ~30 s) | Retry with backoff |
| Server returned an error status | 2 | `error` including an HTTP status | Look it up in [Response error status codes](#response-error-status-codes) |
| Empty / insufficient metrics buffer | neither | On-demand request is sent prompt-only; auto-fired vitals are skipped | Allow ~15 s of measurement to warm up the buffer |
| Nothing to analyze | neither | Sink is never called | Use a UI timeout (below); don't block indefinitely |

## Retry patterns and best practices

**The SDK does not retry insight dispatches** — there is no automatic retry or
backoff. Retrying is your application's responsibility, and the right policy
depends on the surface:

- **Surface 1, `kInvalidState`** — do **not** retry blindly. Ensure a session is
  running (`Start()`) and only then request. Retrying against a stopped session
  will just fail again.
- **Surface 1, `kProcessingFailed`** — usually transient (session or
  connectivity). Make sure a response sink is registered, then retry once or
  twice with a short delay.
- **Surface 2, transient errors** (network unavailable, timeout, `5xx`, or an
  error reporting no analysis was returned) — retry with **exponential backoff**.
- **Surface 2, `429`** — back off aggressively; if it means credits are
  exhausted rather than rate limiting, top up instead of retrying.
- **Surface 2, permanent errors** (`400` bad request, `401`/`403` auth) — do
  **not** retry as-is. Fix the prompt, API key, or account first.

Additional practices:

- **Debounce user-triggered requests.** Disable the trigger (button, etc.) while
  a request is pending, since rapid back-to-back requests are all sent — and
  repeated requests within a single session may not each produce a reply, so
  extra requests can waste quota without adding analyses.
- **Always set a UI timeout.** A request can produce no response at all (the
  "nothing to analyze" outcome above). A failed request resolves within ~30 seconds
  via Surface 2, but a "nothing to analyze" result never calls back — so add your
  own pending-state timeout and clear the spinner.
- **Correlate with the request ID.** Match the `request_id` on each `Insight`
  against the ID returned by your request so a late or failed reply updates the
  right pending item (and so you can ignore auto-fired vitals, which won't match
  a request you made).

## Surfacing errors in your UI

Two reference C++ samples show both surfaces end to end. Use them as templates:

- **`smartspectra/cpp/samples/insights_example/main.cc`** — a minimal OpenCV
  app. It renders *something* for every outcome: `[request failed] <message>`
  when the request call fails (Surface 1), `[error] <message>` when a response
  carries an error (Surface 2), `[empty response]` when a reply has neither
  field set, and a `[waiting for response…]` placeholder while a request is in
  flight. Handling all outcomes — including the empty one — is the pattern to
  copy.
- **`smartspectra/cpp/samples/winui3_example/`** (see
  `SmartSpectraWinUI/MainWindow.xaml.cpp`) — a production-shaped WinUI 3 app. It
  maps typed error codes to friendly strings (for example, authentication →
  "Authentication failed. Check your API key.", credits → "Account credits
  exhausted.", network → "Network issue — please try again."), disables the
  "Ask AI" button and shows "Analyzing…" while a request is pending, and
  re-enables it when a response or a synchronous failure arrives.

Distilling both into a checklist:

- **Map error codes/messages to friendly strings** for the user; keep the raw
  message in your logs.
- **Show a pending state** and **disable the trigger** while a request is in
  flight.
- **Re-enable and clear the pending state on *every* terminal outcome** —
  success, error, **and an empty/absent response.** If you only clear it on
  success and error, a "nothing to analyze" reply leaves your control stuck.
- **Add a timeout** for the no-response case so the UI recovers on its own.
- **Marshal UI updates onto the UI thread.** In C++ the insight callback runs on
  a **background thread**; Swift delivers on the main actor and Android on the
  main thread, but if you fan out to your own handlers, keep UI writes on the UI
  thread.

## Common symptoms and fixes

### `RequestInsight` returns `kInvalidState` (or the call throws immediately)

The session isn't running. Call `Start()` and wait for the session to be active
before requesting.

---

### `RequestInsight` returns `kProcessingFailed` (or the call throws immediately)

Either no response sink was registered, or the SDK couldn't reach the server to
establish the session. Register your insight sink **before** the first request,
verify network connectivity and that your API key is valid, then retry.

---

### A response's `error` includes an HTTP status

Look the status up in [Response error status codes](#response-error-status-codes).
In short: `4xx` means the request or account needs fixing (retrying unchanged
won't help, except `404` / `410`), while `5xx`, transport failures, and errors
reporting the server returned no analysis are transient — retry with backoff.

---

### No insight ever arrives

Three benign causes, in order of likelihood: (1) the metrics buffer hasn't
warmed up yet — wait ~15 seconds after `Start()`; (2) no response sink was
registered, so replies have nowhere to go — register it before starting; (3) the
server had nothing to analyze and returned no result, so the sink was
deliberately not called. Because (3) is silent, always back a pending request
with a UI timeout.

> **Important:** LLM Insights are generated by a large language model and can be inaccurate, incomplete, or misleading — language models sometimes produce confident but false statements ("hallucinations"). Treat every insight as general wellness information, not medical advice, and confirm anything important before acting on it. The SDK metrics themselves are offered for general wellness and informational purposes only; they have not been cleared by the FDA and may not be used for medical diagnosis or treatment.

## Getting Help

- Email: [support@presagetech.com](mailto:support@presagetech.com)
- [Submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues)
- [Docs and FAQ](https://smartspectra.presagetech.com)
- [Developer Admin Portal](https://physiology.presagetech.com/auth/login)
