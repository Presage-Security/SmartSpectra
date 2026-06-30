// CheckupViewModel.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.checkup

import androidx.camera.core.CameraSelector
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.presagetech.smartspectra.CameraPosition
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.MetricsProto.Measurement
import com.presagetech.smartspectra.proto.MetricsProto.MeasurementWithConfidence
import com.presagetech.smartspectra.proto.MetricsProto.Metrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private const val KEY_CAMERA_POSITION = "cameraPosition"
private const val KEY_CARDIO_MEASUREMENTS_ENABLED = "cardioMeasurementsEnabled"
private const val KEY_FACE_METRICS_ENABLED = "faceMetricsEnabled"
private const val KEY_EDA_MEASUREMENTS_ENABLED = "edaMeasurementsEnabled"

/**
 * Traces accumulated over a measurement, surfaced to [CheckupFragment] so it can
 * render the finished plots once the user returns from the capture screen.
 */
data class CheckupTraces(
    val breathingTrace: List<Measurement> = emptyList(),
    val breathingRate: List<MeasurementWithConfidence> = emptyList(),
    val pulseRate: List<MeasurementWithConfidence> = emptyList(),
    val arterialPressureTrace: List<MeasurementWithConfidence> = emptyList(),
    val edaTrace: List<Measurement> = emptyList(),
)

class CheckupViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val smartSpectraSdk = SmartSpectraSdk.shared

    // select camera (front or back, defaults to front when not set)
    private val _cameraPosition =
        MutableStateFlow(savedStateHandle[KEY_CAMERA_POSITION] ?: CameraSelector.LENS_FACING_FRONT)
    val cameraPosition: StateFlow<Int> = _cameraPosition

    private val _cardioMeasurementsEnabled =
        MutableStateFlow(savedStateHandle[KEY_CARDIO_MEASUREMENTS_ENABLED] ?: false)
    val cardioMeasurementsEnabled: StateFlow<Boolean> = _cardioMeasurementsEnabled

    private val _faceMetricsEnabled =
        MutableStateFlow(savedStateHandle[KEY_FACE_METRICS_ENABLED] ?: false)
    val faceMetricsEnabled: StateFlow<Boolean> = _faceMetricsEnabled

    private val _edaMeasurementsEnabled =
        MutableStateFlow(savedStateHandle[KEY_EDA_MEASUREMENTS_ENABLED] ?: false)
    val edaMeasurementsEnabled: StateFlow<Boolean> = _edaMeasurementsEnabled

    // Finished-measurement traces for the Checkup ("selection") screen.
    //
    // The demo runs capture in a separate Activity (SmartSpectraActivity), so
    // CheckupFragment is stopped while metrics stream and its own
    // lifecycle-bound observer can't accumulate them. The ViewModel outlives
    // that stop, so it observes the SDK directly (observeForever) and buffers
    // the whole measurement; the fragment renders the snapshot when it resumes.
    // This mirrors the iOS demo's CheckupView, which accumulates while
    // `processingStatus == .running` and shows the result when idle.
    private val breathingTraceBuffer = mutableListOf<Measurement>()
    private val breathingRateBuffer = mutableListOf<MeasurementWithConfidence>()
    private val pulseRateBuffer = mutableListOf<MeasurementWithConfidence>()
    private val arterialPressureBuffer = mutableListOf<MeasurementWithConfidence>()
    private val edaBuffer = mutableListOf<Measurement>()

    private val _traces = MutableStateFlow(CheckupTraces())
    val traces: StateFlow<CheckupTraces> = _traces

    private val metricsObserver = Observer<Metrics?> { metrics ->
        // Only accumulate while a measurement is actually running; the full
        // trace is assembled from the windowed packets streamed during the run.
        if (metrics != null && smartSpectraSdk.processingStatus.value == ProcessingStatus.RUNNING) {
            accumulate(metrics)
        }
    }

    private val processingStatusObserver = Observer<ProcessingStatus> { status ->
        // Reset at the start of each measurement so the finished plots only show
        // the current session's data.
        if (status == ProcessingStatus.RUNNING) clearTraces()
    }

    init {
        smartSpectraSdk.metrics.observeForever(metricsObserver)
        smartSpectraSdk.processingStatus.observeForever(processingStatusObserver)
    }

    override fun onCleared() {
        smartSpectraSdk.metrics.removeObserver(metricsObserver)
        smartSpectraSdk.processingStatus.removeObserver(processingStatusObserver)
        super.onCleared()
    }

    fun setCameraPosition(value: Int) {
        smartSpectraSdk.config.cameraPosition = CameraPosition.fromLensFacing(value)
        _cameraPosition.update { value }
        savedStateHandle[KEY_CAMERA_POSITION] = value
    }

    fun setCardioMeasurementsEnabled(value: Boolean) {
        _cardioMeasurementsEnabled.update { value }
        savedStateHandle[KEY_CARDIO_MEASUREMENTS_ENABLED] = value
        if (!value) {
            pulseRateBuffer.clear()
            arterialPressureBuffer.clear()
            emitSnapshot()
        }
    }

    fun setFaceMetricsEnabled(value: Boolean) {
        _faceMetricsEnabled.update { value }
        savedStateHandle[KEY_FACE_METRICS_ENABLED] = value
    }

    fun setEdaMeasurementsEnabled(value: Boolean) {
        _edaMeasurementsEnabled.update { value }
        savedStateHandle[KEY_EDA_MEASUREMENTS_ENABLED] = value
        if (!value) {
            edaBuffer.clear()
            emitSnapshot()
        }
    }

    // Packets carry only the new samples since the last one, so a plain append
    // rebuilds the full trace over the run (same as the iOS demo's CheckupView).
    // Metrics for a section are only present when that section is requested, and
    // toChartEntries() drops non-positive timestamps at render time.
    private fun accumulate(metrics: Metrics) {
        var changed = false

        if (metrics.hasBreathing()) {
            val breathing = metrics.breathing
            if (breathing.upperTraceCount > 0) {
                breathingTraceBuffer += breathing.upperTraceList
                changed = true
            }
            if (breathing.rateCount > 0) {
                breathingRateBuffer += breathing.rateList
                changed = true
            }
        }

        if (metrics.hasCardio()) {
            val cardio = metrics.cardio
            if (cardio.pulseRateCount > 0) {
                pulseRateBuffer += cardio.pulseRateList
                changed = true
            }
            if (cardio.arterialPressureTraceCount > 0) {
                arterialPressureBuffer += cardio.arterialPressureTraceList
                changed = true
            }
        }

        if (metrics.hasEda() && metrics.eda.traceCount > 0) {
            edaBuffer += metrics.eda.traceList
            changed = true
        }

        if (changed) emitSnapshot()
    }

    private fun emitSnapshot() {
        _traces.value = CheckupTraces(
            breathingTrace = breathingTraceBuffer.toList(),
            breathingRate = breathingRateBuffer.toList(),
            pulseRate = pulseRateBuffer.toList(),
            arterialPressureTrace = arterialPressureBuffer.toList(),
            edaTrace = edaBuffer.toList(),
        )
    }

    private fun clearTraces() {
        breathingTraceBuffer.clear()
        breathingRateBuffer.clear()
        pulseRateBuffer.clear()
        arterialPressureBuffer.clear()
        edaBuffer.clear()
        _traces.value = CheckupTraces()
    }
}
