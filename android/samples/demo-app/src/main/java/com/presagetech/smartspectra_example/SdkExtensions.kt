// SdkExtensions.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example

import android.content.Context
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraError
import com.presagetech.smartspectra.SmartSpectraSdk

internal val SmartSpectraSdk.cardioMeasurementsEnabled: Boolean
    get() = config.requestedMetrics.orEmpty().any { it in SmartSpectraConfig.cardioMetrics }

internal val SmartSpectraSdk.facialExpressionEnabled: Boolean
    get() = config.requestedMetrics.orEmpty().any { it in SmartSpectraConfig.faceMetrics }

internal val SmartSpectraSdk.edaMeasurementsEnabled: Boolean
    get() = config.requestedMetrics.orEmpty().any { it in SmartSpectraConfig.edaMetrics }

internal fun SmartSpectraError.userFacingMessage(context: Context): String {
    return when (code) {
        SmartSpectraError.Code.AUTHENTICATION_FAILED ->
            context.getString(R.string.smart_spectra_error_authentication_failed)
        SmartSpectraError.Code.CREDIT_EXHAUSTED ->
            context.getString(R.string.smart_spectra_error_credit_exhausted)
        SmartSpectraError.Code.NETWORK_ERROR ->
            context.getString(R.string.smart_spectra_error_network)
        SmartSpectraError.Code.SERVER_ERROR ->
            context.getString(R.string.smart_spectra_error_server)
        SmartSpectraError.Code.CONFIGURATION_FAILED ->
            context.getString(R.string.smart_spectra_error_configuration)
        SmartSpectraError.Code.INPUT_UNAVAILABLE ->
            message.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.smart_spectra_error_input_unavailable)
        SmartSpectraError.Code.INVALID_STATE ->
            context.getString(R.string.smart_spectra_error_invalid_state)
        SmartSpectraError.Code.PROCESSING_FAILED,
        SmartSpectraError.Code.FRAME_CONVERSION_FAILED,
        SmartSpectraError.Code.NON_MONOTONIC_TIMESTAMP,
        SmartSpectraError.Code.TIMESTAMP_GAP,
        -> context.getString(R.string.smart_spectra_error_processing)
    }
}
