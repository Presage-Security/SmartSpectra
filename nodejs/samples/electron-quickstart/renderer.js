// renderer.js
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
//
// Renderer entry point for the SmartSpectra Electron quickstart. Bundled
// into dist/renderer.bundle.js by esbuild (see package.json#scripts.build).
//
// Two high-level toggles (Cardio, Face) layered on top of the always-on
// Breathing bundle, plus four live line charts that grow as the metrics
// buffer fills and persist after Stop.
//
// Replace API_KEY below with the value from your Presage developer
// portal, or read it from a secure store of your choice.

'use strict';

const {
    SmartSpectraSDK,
    ProcessingStatus,
    ValidationCode,
    breathingMetrics,
    cardioMetrics,
    faceMetrics,
} = require('@smartspectra/node-sdk/renderer');

// Generated protobuf decoder shipped by the SDK itself. The `messages`
// subpath is renderer-safe (no koffi / FFI on its require graph), so
// esbuild folds it into dist/renderer.bundle.js cleanly.
const { decodeMetrics } = require('@smartspectra/node-sdk/messages');

// Chart.js auto-registers all controllers/scales/elements when imported
// via the default `chart.js` entry, so the line charts below work
// out-of-the-box with no extra Chart.register() calls.
const { Chart } = require('chart.js/auto');

// === Replace with your real API key from https://physiology.presagetech.com/ ===
// For a production app, fetch the key from OS secure storage (Keychain on
// macOS, DPAPI on Windows, libsecret on Linux) or from a signed config
// blob your backend serves — don't bake it into the renderer bundle.
const API_KEY = 'YOUR_API_KEY_HERE';
if (API_KEY === 'YOUR_API_KEY_HERE') {
    console.warn(
        '[sample] API_KEY is the placeholder value — edit renderer.js and ' +
        'paste your real key from https://physiology.presagetech.com/ ' +
        'before Start. The SDK will fail to authenticate until you do.');
}

// DOM handles ---------------------------------------------------------------
const $ = (id) => document.getElementById(id);
const els = {
    preview:           $('preview'),
    statusBadge:       $('status-badge'),
    validation:        $('validation'),
    errorBanner:       $('error-banner'),
    errorCode:         $('error-code'),
    errorMessage:      $('error-message'),
    readoutPulse:      $('readout-pulse'),
    readoutBreathing:  $('readout-breathing'),
    btnStart:          $('btn-start'),
    btnStop:           $('btn-stop'),
    btnReset:          $('btn-reset'),
    cardioToggle:      $('m-cardio'),
    faceToggle:        $('m-face'),
    panelPulseRate:    $('panel-pulse-rate'),
    panelArterial:     $('panel-arterial-pressure'),
};

// Look-up tables for the ProcessingStatus / ValidationCode integers so we
// can render readable labels without hard-coding the values.
const STATUS_NAMES = Object.fromEntries(
    Object.entries(ProcessingStatus).map(([k, v]) => [v, k.replace(/^k/, '')]),
);
const VALIDATION_NAMES = Object.fromEntries(
    Object.entries(ValidationCode).map(([k, v]) => [v, k.replace(/^k/, '')]),
);

// Chart factory --------------------------------------------------------------
// Each chart uses { x: seconds, y: value } parsed objects. We disable
// `parsing` and `animation` so the per-event redraw (~30 fps with all
// bundles enabled) stays cheap. Each `metrics` event delivers a cumulative
// Metrics protobuf so we simply replace the dataset wholesale instead of
// appending.
function makeChart(canvasId, yLabel) {
    const ctx = document.getElementById(canvasId).getContext('2d');
    return new Chart(ctx, {
        type: 'line',
        data: {
            datasets: [{
                data:            [],
                borderColor:     '#e3635f',
                backgroundColor: '#e3635f',
                borderWidth:     1.5,
                pointRadius:     0,
                tension:         0,
            }],
        },
        options: {
            animation:    false,
            parsing:      false,
            responsive:   true,
            maintainAspectRatio: false,
            plugins: {
                legend:  { display: false },
                tooltip: { enabled: false },
            },
            scales: {
                x: {
                    type:  'linear',
                    title: { display: true, text: 'seconds', color: '#7a8095' },
                    ticks: {
                        color:    '#7a8095',
                        maxTicksLimit: 8,
                        // One tick label per second; Chart.js still draws
                        // sub-second gridlines but the labels stay sparse.
                        stepSize: 1,
                    },
                    grid: { color: '#2a2f3d' },
                },
                y: {
                    title: { display: !!yLabel, text: yLabel || '', color: '#7a8095' },
                    ticks: { color: '#7a8095' },
                    grid:  { color: '#2a2f3d' },
                },
            },
        },
    });
}

const charts = {
    breathingRate:     makeChart('chart-breathing-rate',     'BR/min'),
    breathingTrace:    makeChart('chart-breathing-trace',    null),
    pulseRate:         makeChart('chart-pulse-rate',         'BPM'),
    arterialPressure:  makeChart('chart-arterial-pressure',  null),
};

function tsToNumber(ts) {
    if (ts == null) return 0;
    if (typeof ts === 'number') return ts;
    if (typeof ts === 'bigint') return Number(ts);
    // protobufjs Long: { low, high, unsigned } with .toNumber().
    if (typeof ts.toNumber === 'function') return ts.toNumber();
    return Number(ts);
}

// Drop a chart back to an empty dataset (called on Start / Reset). Also
// clears the renderer-side accumulators so the next session starts from a
// clean slate.
function clearCharts() {
    clearAccumulated();
    for (const c of Object.values(charts)) {
        c.data.datasets[0].data = [];
        c.update('none');
    }
    els.readoutPulse.textContent     = '—';
    els.readoutBreathing.textContent = '—';
}

// Hide / show the two cardio panels based on the toggle. The placeholder
// overlay inside each panel handles the dimmed message; we just flip a
// class so CSS can do the rest.
function applyCardioVisibility() {
    const on = els.cardioToggle.checked;
    els.panelPulseRate.classList.toggle('disabled', !on);
    els.panelArterial.classList.toggle('disabled', !on);
}

// State counters -------------------------------------------------------------
let metricsCount = 0;
// Timestamp of the first Measurement we receive — used as the chart x-axis
// origin so seconds count from session start, not from the Unix epoch.
let timeOriginUs = null;
// Live MediaStream — surfaced by the SDK through the 'streamAvailable' event
// once start() has acquired the camera. The host app owns rendering it to the
// preview; the SDK owns the lifecycle (release on stop/destroy).
let activeStream = null;

// SDK is constructed on first Start so the metric toggles take effect each
// session. `requestedMetrics` is fixed at construction time on the SDK, so
// Stop + destroy + rebuild is the path to apply changes.
let sdk = null;

function readRequestedMetrics() {
    const codes = [...breathingMetrics];
    if (els.cardioToggle.checked) codes.push(...cardioMetrics);
    if (els.faceToggle.checked)   codes.push(...faceMetrics);
    return codes;
}

function lastValue(arr) {
    if (!arr || arr.length === 0) return null;
    return arr[arr.length - 1].value;
}

// Each `metrics` event delivers the WINDOWED slice since the previous
// emit — the C++ AggregatedEdgeMetricsCalculator calls
// `metered_metrics_.Clear()` after each packet. To build a growing
// line chart we APPEND the slices into renderer-side accumulator
// arrays.
//
// The `accumulatedMetrics` event looks like a tempting shortcut but
// only carries data when `enableAccumulatedOutput` is true AND the
// side packet has propagated correctly; replacing wholesale from it
// silently produces empty charts if anything drops along the way.

// Per-metric accumulators. Each entry is a decoded `Measurement` (we
// keep the proto shape so the chart datapoint conversion below can read
// `.timestamp` and `.value`). Reset on Start.
const accumulated = {
    breathingRate:    [],
    breathingTrace:   [],
    pulseRate:        [],
    arterialPressure: [],
};

function clearAccumulated() {
    accumulated.breathingRate.length    = 0;
    accumulated.breathingTrace.length   = 0;
    accumulated.pulseRate.length        = 0;
    accumulated.arterialPressure.length = 0;
}

// Append decoded Measurements to one of the accumulators and refresh
// the matching chart from scratch (Chart.js needs the full data array
// every redraw; we don't trust incremental dataset mutation across
// `update('none')` calls).
function appendAndRedraw(buffer, chart, newMeasurements, t0Us) {
    if (!newMeasurements || newMeasurements.length === 0) return;
    for (const m of newMeasurements) buffer.push(m);
    const points = new Array(buffer.length);
    for (let i = 0; i < buffer.length; i++) {
        const m = buffer[i];
        points[i] = { x: (tsToNumber(m.timestamp) - t0Us) / 1_000_000, y: m.value };
    }
    chart.data.datasets[0].data = points;
    chart.update('none');
}

function handleMetrics(metrics) {
    // Pin the chart x-axis origin to the first Measurement we ever see
    // (any field), so seconds count from session start.
    const firstTs = (
        metrics.breathing?.rate?.[0]?.timestamp        ??
        metrics.breathing?.upperTrace?.[0]?.timestamp  ??
        metrics.cardio?.pulseRate?.[0]?.timestamp      ??
        metrics.cardio?.arterialPressureTrace?.[0]?.timestamp
    );
    if (timeOriginUs === null && firstTs != null) {
        timeOriginUs = tsToNumber(firstTs);
    }
    const t0 = timeOriginUs ?? 0;

    appendAndRedraw(accumulated.breathingRate,    charts.breathingRate,    metrics.breathing?.rate,                t0);
    appendAndRedraw(accumulated.breathingTrace,   charts.breathingTrace,   metrics.breathing?.upperTrace,          t0);
    appendAndRedraw(accumulated.pulseRate,        charts.pulseRate,        metrics.cardio?.pulseRate,              t0);
    appendAndRedraw(accumulated.arterialPressure, charts.arterialPressure, metrics.cardio?.arterialPressureTrace,  t0);

    // Live numeric readouts: latest sample in the windowed slice (NOT
    // the accumulator) so the readouts track the most-recent estimate
    // without lagging behind as buffers grow.
    const br = lastValue(metrics.breathing?.rate);
    if (br != null) els.readoutBreathing.textContent = br.toFixed(0);
    const pr = lastValue(metrics.cardio?.pulseRate);
    if (pr != null) els.readoutPulse.textContent = pr.toFixed(0);
}

function buildSdk() {
    const requested = readRequestedMetrics();
    console.log('[sample] requestedMetrics =', requested);

    sdk = new SmartSpectraSDK({
        apiKey: API_KEY,
        requestedMetrics: requested,
        // Leave accumulated output disabled — the renderer appends the
        // windowed `metrics` event payloads to its own accumulator
        // arrays for both growing-line charts and live readouts.
        enableAccumulatedOutput: false,
    });

    sdk.on('processingStatus', (status) => {
        const label = STATUS_NAMES[status] || `Status(${status})`;
        console.log(`[sample] processingStatus -> ${label} (${status})`);
        els.statusBadge.textContent = label;
        els.statusBadge.className = `status status-${label.toLowerCase()}`;
        // Authoritative button state per ProcessingStatus (each branch sets
        // all three so state never leaks across transitions).
        if (status === ProcessingStatus.kStarting) {
            // Startup can stall before the watchdog fires, so keep it
            // abortable: Start stays disabled (already starting), Stop
            // cancels, Reset waits for a terminal state.
            // SmartSpectra::Stop() is valid from kStarting.
            els.btnStart.disabled = true;
            els.btnStop.disabled = false;
            els.btnReset.disabled = true;
        } else if (status === ProcessingStatus.kRunning) {
            els.btnStart.disabled = true;
            els.btnStop.disabled = false;
            els.btnReset.disabled = true;
        } else if (status === ProcessingStatus.kStopping) {
            // Mid-teardown: nothing is actionable until it settles to Idle.
            els.btnStart.disabled = true;
            els.btnStop.disabled = true;
            els.btnReset.disabled = true;
        } else if (status === ProcessingStatus.kError) {
            els.btnStart.disabled = true;
            els.btnStop.disabled = true;
            els.btnReset.disabled = false;
        } else if (status === ProcessingStatus.kIdle || status === ProcessingStatus.kUninitialized) {
            // Start re-enables on idle so the user can re-arm without
            // needing a preview stream — the SDK acquires its own camera
            // inside start() and surfaces it via 'streamAvailable'.
            els.btnStart.disabled = false;
            els.btnStop.disabled = true;
            els.btnReset.disabled = false;
        }
    });

    sdk.on('validationStatus', (code, timestampUs, hint) => {
        const name = VALIDATION_NAMES[code] || `Code(${code})`;
        console.log(`[sample] validationStatus ${name} (${code}) ts=${timestampUs} hint=${hint || '-'}`);
        els.validation.textContent = `${name}${hint ? ' — ' + hint : ''}`;
        els.validation.className = 'hint ' + (
            code === ValidationCode.kOk ? 'hint-ok' :
            code === ValidationCode.kCameraTuning ? 'hint-warn' :
            'hint-err'
        );
    });

    // The windowed `metrics` event drives both the live numeric readouts
    // AND the growing chart lines (via per-event append to local
    // accumulator arrays inside handleMetrics).
    sdk.on('metrics', (buf, timestampUs) => {
        metricsCount++;
        try {
            const decoded = decodeMetrics(buf);
            handleMetrics(decoded);
        } catch (e) {
            console.error(`[sample] failed to decode Metrics #${metricsCount} (${buf.byteLength}B ts=${timestampUs}):`, e);
        }
    });

    sdk.on('error', (code, message, retryable) => {
        console.error(`[sample] error code=${code} retryable=${retryable}: ${message}`);
        els.errorBanner.hidden = false;
        els.errorCode.textContent = code;
        els.errorMessage.textContent = retryable ? `${message} (retryable)` : message;
    });

    // The SDK acquires the default front-facing camera on start() and
    // surfaces it here so the host app can show a preview. This event
    // also fires on every Start after a Stop, so the preview rebinds
    // automatically across sessions.
    sdk.on('streamAvailable', (stream) => {
        activeStream = stream;
        els.preview.srcObject = stream;
        // Electron's autoplay policy doesn't always honor `<video autoplay>`
        // when srcObject is assigned after page load — kick playback
        // explicitly. Swallow the rejection (only thrown if the OS denies
        // playback, which the muted policy already handles).
        els.preview.play().catch(() => {});
    });
}

function setTogglesEnabled(enabled) {
    els.cardioToggle.disabled = !enabled;
    els.faceToggle.disabled = !enabled;
}

// UI handlers ----------------------------------------------------------------
async function startMeasurement() {
    els.btnStart.disabled = true;
    els.errorBanner.hidden = true;
    setTogglesEnabled(false);
    metricsCount = 0;
    timeOriginUs = null;
    clearCharts();
    try {
        if (!sdk) buildSdk();
        // The SDK acquires the camera itself and emits 'streamAvailable'
        // with the live MediaStream — see the listener in buildSdk(). Host
        // apps that need a custom camera, virtual source, or
        // desktopCapturer feed can call sdk.useMediaStream(...) before
        // start() to override.
        await sdk.start();
    } catch (err) {
        els.errorBanner.hidden = false;
        els.errorCode.textContent = err.code ?? 'JS';
        els.errorMessage.textContent = err.message;
        els.btnStart.disabled = false;
        setTogglesEnabled(true);
    }
}

async function stopMeasurement() {
    els.btnStop.disabled = true;
    try { if (sdk) await sdk.stop(); } catch (err) {
        console.error('stop() failed:', err);
    }
    // The SDK released the camera inside stop() — drop the local preview
    // reference too. The next Start will re-acquire and refire
    // 'streamAvailable'.
    activeStream = null;
    els.preview.srcObject = null;
    // Destroy the SDK so the next Start picks up any toggle changes —
    // requestedMetrics is fixed at construction time.
    if (sdk) {
        try { sdk.destroy(); } catch { /* already destroyed */ }
        sdk = null;
    }
    els.btnStart.disabled = false;
    setTogglesEnabled(true);
    // Charts intentionally persist after Stop so the user can review the
    // final waveform.
}

async function resetSession() {
    els.btnReset.disabled = true;
    try {
        if (sdk) await sdk.reset();
        els.errorBanner.hidden = true;
        // reset() releases the camera; the next Start will reacquire.
        activeStream = null;
        els.preview.srcObject = null;
        els.btnStart.disabled = false;
        clearCharts();
    } catch (err) {
        console.error('reset() failed:', err);
        els.btnReset.disabled = false;
    }
}

els.btnStart.addEventListener('click', startMeasurement);
els.btnStop.addEventListener('click',  stopMeasurement);
els.btnReset.addEventListener('click', resetSession);
els.cardioToggle.addEventListener('change', applyCardioVisibility);

// Initial layout — the two cardio panels start dimmed until the user
// flips the Cardio toggle on.
applyCardioVisibility();

// Clean shutdown when the window closes so the main-process SDK joins its
// worker threads before the Electron process tears the renderer down.
window.addEventListener('beforeunload', () => {
    if (sdk) { try { sdk.destroy(); } catch { /* already destroyed */ } }
});
