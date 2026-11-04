---
title: SDK Telemetry & Privacy
description: What SmartSpectra session summaries and full device readiness reports contain, authentication, retention, and how to disable telemetry.
---

# SDK Telemetry & Privacy

When telemetry is enabled, the SmartSpectra SDK sends aggregate measurement-session
summaries and full device readiness diagnostic reports. These help diagnose
SDK reliability and device support. Telemetry defaults to on and can be disabled
through the SDK configuration.

## What is collected

Measurement-session summaries contain:

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

## Device Readiness checks

When telemetry is enabled, a readiness check sends its full diagnostic report,
not just its public verdict and reason. The report includes:

- Ready, Limited, Blocked, or Inconclusive verdict, reason and recommended next step.
- Device model, OS version, architecture, SDK version/binding and package origin.
- Requested metrics, input dimensions/frame rate and camera selection information.
- Throughput, timing, resource/performance checks, thresholds and observed values.
- Diagnostic messages, stage/backend names, policy explanations and failure details.
- A wall-clock event timestamp and simulator/reference-input indicators.

Diagnostic messages are free text and can include technical context such as paths
or errors supplied by platform components. Do not treat them as guaranteed free
of identifying information. Diagnostic reports are compressed for transmission;
compression does not remove fields or anonymize them.

Readiness telemetry uses the same opt-out as session telemetry. Reporting is
best-effort and requires authentication. Results are available to the application
without waiting for network delivery. Missing telemetry never implies a passing
readiness check.

## Data boundaries and authentication

Telemetry does not attach raw video/image frames or measured physiological values.
Performance measurements in a readiness report describe device capability,
not a person's vital signs. Session summaries do not contain free-text diagnostic
messages; readiness reports do.

Requests use SDK account authentication and device-authentication headers. Although
the readiness report does not add an account ID or stable phone ID as a report
field, authentication and service request correlation can associate requests with
an account. Do not describe this collection as anonymous or categorically
unlinked to identity.

## Storage and retention

Full readiness reports are stored in private diagnostic storage. Operational
logs contain summary metadata and archive references rather than the full report.
Reports and access logs currently have no automatic expiration configured; deletion
and retention are governed by Presage's applicable policies. A fixed retention
period must not be inferred from a CI test artifact's expiration.

Telemetry requests are limited to 256 KiB both compressed and decompressed; a full
readiness diagnostic report is limited to 128 KiB. Oversized events are not
accepted. The request limit also applies to measurement-session summaries.

## Turning it off

Telemetry is opt-out through configuration on supported SDKs. It defaults to on;
when disabled, the SDK starts no telemetry session and transmits nothing.
Set `enableTelemetry = false` on the SDK config:
`SmartSpectraConfig::enable_telemetry` in C++, `enableTelemetry` in Swift/iOS,
Kotlin/Android and Node, and `SmartSpectraConfig.EnableTelemetry` in the
[.NET wrapper for Windows](https://smartspectra.presagetech.com/docs/cpp/windows/windows-dotnet).

Telemetry is sent over the same authenticated, TLS-pinned channel the SDK uses
for its other server requests, when authenticated server access is available — never to a separate or
unauthenticated destination. Delivery is
best-effort: an event that cannot be sent is dropped and never affects the
measurement session.

## Your responsibilities

If you distribute an application that embeds the SDK, disclose this collection to
your users and complete your app-store data-collection declarations accordingly,
consistent with Presage's
[Privacy Policy](https://api.physiology.presagetech.com/privacypolicy).
