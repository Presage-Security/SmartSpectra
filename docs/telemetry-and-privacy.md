---
title: SDK Telemetry & Privacy
description: What the SmartSpectra SDK's aggregate, opt-out session telemetry collects, how to disable it, and the privacy guarantees behind it.
---

# SDK Telemetry & Privacy

The SmartSpectra SDK can report a small, aggregate diagnostic summary once per
measurement session. It exists so we can measure release quality across the
devices and conditions the SDK actually runs on — which platforms work well,
where sessions fail, and under what conditions. It is designed to be lightweight
and privacy-preserving.

## What is collected

A single aggregate summary per session, containing only:

- **SDK build** — SDK version (with a short commit appended for non-release
  builds) and package origin.
- **Runtime** — platform, OS version, device model, CPU architecture, SDK
  binding.
- **Session shape** — source kind (camera / video file / custom input), outcome
  (completed / stopped / reset / error), and the *number* of metrics requested.
- **Performance** — frame throughput counts, input frame-rate statistics,
  startup-latency milestones, flow-limiter throughput, and produced-metric
  counts.
- **Quality** — counts of on-device validation states (e.g. lighting or motion
  warnings) and, for a failed session, a coarse error code.
- A wall-clock timestamp for the session.

## What is NOT collected

The telemetry is aggregate-only. It never includes:

- Raw video or image frames.
- Measured metric values of any kind — pulse rate, breathing rate, HRV, the
  relative arterial pressure waveform, and every other metric the SDK computes.
- File paths, prompts, or free-text error messages.
- User identifiers or stable device identifiers.

Because it carries no stable identifiers and no measurement values, it is not
linked to a user's identity and is not used for tracking. For Apple App Store
privacy labels this maps to the *Performance Data* and *Product Interaction*
types; for Google Play Data Safety it corresponds to *App info and performance*
and *App activity* — in both cases not linked to identity and not used for
tracking.

## Turning it off

Telemetry is opt-out through configuration on supported SDKs. It defaults to on;
when disabled, the SDK starts no telemetry session and transmits nothing.
Set `enableTelemetry = false` on the SDK config:
`SmartSpectraConfig::enable_telemetry` in C++, `enableTelemetry` in Swift/iOS,
Kotlin/Android and Node, and `SmartSpectraConfig.EnableTelemetry` in the
[.NET wrapper for Windows](https://smartspectra.presagetech.com/docs/cpp/windows/windows-dotnet).

Telemetry is sent over the same authenticated, TLS-pinned channel the SDK uses
for its other server requests, and only while the SDK has an active server
session — never to a separate or unauthenticated destination. Delivery is
best-effort: a summary that cannot be sent is dropped and never affects the
measurement session.

## Your responsibilities

If you distribute an application that embeds the SDK, disclose this collection to
your users and complete your app-store data-collection declarations accordingly,
consistent with Presage's
[Privacy Policy](https://api.physiology.presagetech.com/privacypolicy).
