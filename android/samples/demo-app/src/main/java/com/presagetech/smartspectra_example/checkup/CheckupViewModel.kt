// CheckupViewModel.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.checkup

import androidx.camera.core.CameraSelector
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.presagetech.smartspectra.CameraPosition
import com.presagetech.smartspectra.SmartSpectraSdk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private const val KEY_CAMERA_POSITION = "cameraPosition"
private const val KEY_CARDIO_MEASUREMENTS_ENABLED = "cardioMeasurementsEnabled"
private const val KEY_FACE_METRICS_ENABLED = "faceMetricsEnabled"

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

    fun setCameraPosition(value: Int) {
        smartSpectraSdk.config.cameraPosition = CameraPosition.fromLensFacing(value)
        _cameraPosition.update { value }
        savedStateHandle[KEY_CAMERA_POSITION] = value
    }

    fun setCardioMeasurementsEnabled(value: Boolean) {
        _cardioMeasurementsEnabled.update { value }
        savedStateHandle[KEY_CARDIO_MEASUREMENTS_ENABLED] = value
    }

    fun setFaceMetricsEnabled(value: Boolean) {
        _faceMetricsEnabled.update { value }
        savedStateHandle[KEY_FACE_METRICS_ENABLED] = value
    }
}
