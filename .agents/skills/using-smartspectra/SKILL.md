---
name: using-smartspectra
description: Use when a developer wants to measure vitals (pulse, breathing, ...) from video with the SmartSpectra SDK — the API model, getting a key, choosing metrics, or integrating the SDK on any platform.
---

# Using the SmartSpectra SDK

## What it is

SmartSpectra (Presage Technologies) measures vitals — pulse, breathing, and more — from an
ordinary camera or a recorded video, using nothing but the frames themselves. All metric
computation happens **on-device**, inside the SDK; the SDK calls a Presage backend only to
**authenticate the API key and validate the subscription** (gating), never to compute or score
metrics remotely. A network connection is required for that gating, but metric computation
always runs locally against whatever input source you configure.

The SDK exposes one language-agnostic metrics payload and lifecycle across every binding — C++,
Swift/iOS, Kotlin/Android, and Node/TypeScript. Everything below applies no matter which binding
you use; only surface syntax differs (snake_case vs. camelCase, a callback setter vs. an
observable property).

## Get an API key

1. Register at <https://physiology.presagetech.com/auth/register> (or log in at
   `/auth/login` if you already have an account).
2. Copy the API key from the developer portal.
3. Make it available to your app — the conventional way is an environment variable:

   ```bash
   export SMARTSPECTRA_API_KEY="YOUR_API_KEY"
   ```

Treat the key as a secret: don't commit it, and avoid hardcoding it into an app binary you ship
to end users (an OAuth-backed flow is the production-grade alternative on mobile).

## The lifecycle (same shape on every binding)

Every SmartSpectra binding follows the same sequence:

1. **Create a config** and set:
   - `apiKey` — the key from above.
   - `requestedMetrics` — which metric groups to compute: `cardio`, `breathing`, and so on (see
     [Metrics vocabulary](#metrics-vocabulary) below). Request only what your app displays.
2. **Construct the SDK** from that config.
3. **Subscribe**, before starting, to the streams you care about:
   - **metrics** — the physiological payload, delivered continuously while running.
   - **validation status** — human-readable guidance when the input isn't good enough yet
     (face out of frame, poor lighting, frame rate too low, …).
   - **errors** — typed failures (invalid key, camera unavailable, network failure, …).
4. **Choose an input source** — camera or file (see [Input](#input-camera-or-file) below).
5. **`start()`** begins capture and processing. **`stop()`** ends the session cleanly.
   **`reset()`** tears down and rebuilds the SDK's internal pipeline — use it to recover
   after the SDK reports an unrecoverable error state, since it's more expensive than `start()`.
   For a normal fresh run after a clean `stop()`, just call `start()` again — it reuses the
   existing graph.

That's the whole surface — no pause/resume, and no manual frame-pump loop for the built-in
camera/file sources.

## Metrics vocabulary

Metrics are grouped; request a group on the config, then read that group's fields from the
payload your metrics subscription receives. The groups and what each carries:

- **`cardio`** — pulse rate, a relative arterial-pressure waveform (a *shape*, not a calibrated
  blood-pressure reading), and heart-rate variability (e.g. RMSSD/SDNN).
- **`breathing`** — its own group, not nested under cardio: breathing rate, chest/abdomen
  movement traces, amplitude, and apnea detection.
- **`face`** — landmarks, blinking, talking, and facial-expression detections.
- **`eda`** — electrodermal activity trace (skin conductance).

The group names are the vocabulary you request; the field descriptions above are conceptual, not
literal accessors. The numeric fields — the rates and traces — arrive as a **repeated time-series
of samples**, each sample carrying a `value` alongside `confidence`, `stable`, and a `timestamp`;
for a current reading like pulse, take the **latest sample** and read its `value`. The exact
per-binding field names and accessor syntax are in the docs — read the platform's `metrics` page
and the payload schema at `.../docs/data-types` (see the reference section below).

Metrics you don't request, or aren't authorized for your key, are simply omitted from the
payload rather than raising an error. That's distinct from the authorization *request* itself
failing (for example, the network call that validates your key doesn't succeed) — that surfaces
as a `start()` error, not a silent omission.

## Input: camera or file

- **Camera** — the default and the real-world use case: a live webcam or front camera. A user
  sits in frame, well-lit and reasonably still.
- **Video file** — for automation, scripted demos, or headless runs: point the SDK at a recorded
  video instead of a live camera. Desktop tier only (C++/Node); mobile input is the live camera.

Both sources feed the same metrics/validation/error subscriptions above — swapping the source
doesn't change how you consume output.

## Reference: read the live docs for specifics

The prose above is the durable mental model. For anything exact — signatures, config properties,
enum values, install/link steps, per-platform gotchas, OAuth setup, troubleshooting, or a
**complete working sample app** — **read the official docs at `smartspectra.presagetech.com`.**
They track the shipped SDK and link the maintained per-platform sample apps; fetch the live page
rather than relying on memory or scaffolding an app from scratch, since the SDK evolves.

1. **Start at the index:** fetch `https://smartspectra.presagetech.com/llms.txt` — a plain-text
   list of every doc page with a one-line description. Use it to find the page you need.
2. **Fetch that page**, e.g.
   `https://smartspectra.presagetech.com/docs/<platform>/<topic>`, where:
   - `platform` ∈ `android`, `swift` (iOS), `cpp` (Linux/macOS/Windows), `nodejs`.
   - `topic` ∈ `api-reference`, `metrics`, `option-1-api-key`, `option-2-oauth`,
     `troubleshooting`, `migration-guide`, `headless-mode` (availability varies by platform —
     the index lists what exists).
   - C++ install is under `cpp/linux/…`, `cpp/macos`, `cpp/windows/…`.
   - The complete runnable sample lives on the platform's **setup page** — the API-key/install
     page (`android/option-1-api-key`, `cpp/linux/…`).
3. **Whole corpus at once** (large — prefer a specific page):
   `https://smartspectra.presagetech.com/llms-full.txt`.

Cross-cutting reference (platform-independent): `.../docs/data-types` (the payload schema).
