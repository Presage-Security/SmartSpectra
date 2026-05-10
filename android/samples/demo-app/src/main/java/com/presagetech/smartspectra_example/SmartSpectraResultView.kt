// SmartSpectraResultView.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Observer
import com.presagetech.smartspectra.SmartSpectraError
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.MetricsProto.Metrics
import kotlin.math.roundToInt

/**
 * View that displays the latest breathing rate (and pulse rate when cardio
 * is enabled) from the SDK's metrics stream, plus any error messages.
 *
 * Mirrors the iOS `SmartSpectraResultView` behavior: breathing rate is
 * always shown; pulse rate appears only when cardio measurements are enabled.
 */
class SmartSpectraResultView(
    context: Context,
    attrs: AttributeSet?
) : LinearLayout(context, attrs) {
    private var resultTextView: TextView
    private var resultErrorTextView: TextView
    private val sdk: SmartSpectraSdk by lazy { SmartSpectraSdk.shared }

    private val metricsObserver = Observer<Metrics?> { metrics ->
        updateResultText(metrics)
    }
    private val errorObserver = Observer<SmartSpectraError?> { error ->
        updateErrorText(error?.userFacingMessage(context).orEmpty())
    }

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_result, this, true)
        resultTextView = findViewById(R.id.result_text)
        resultErrorTextView = findViewById(R.id.result_error_text)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sdk.metrics.observeForever(metricsObserver)
        sdk.error.observeForever(errorObserver)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sdk.metrics.removeObserver(metricsObserver)
        sdk.error.removeObserver(errorObserver)
    }

    /**
     * Formats the latest breathing (and optionally pulse) rates from the metrics stream.
     * Pulse rate is only shown when cardio measurements are enabled.
     */
    private fun updateResultText(metrics: Metrics?) {
        if (metrics == null) {
            resultTextView.text = context.getString(R.string.result_label_empty)
            return
        }

        val breathingRate = if (metrics.hasBreathing() && metrics.breathing.rateCount > 0) {
            metrics.breathing.rateList.last().value.roundToInt()
        } else {
            0
        }
        val breathingRateText = if (breathingRate == 0) "N/A" else "$breathingRate BPM"

        if (sdk.cardioMeasurementsEnabled) {
            val pulseRate = if (metrics.hasCardio() && metrics.cardio.pulseRateCount > 0) {
                metrics.cardio.pulseRateList.last().value.roundToInt()
            } else {
                0
            }
            val pulseRateText = if (pulseRate == 0) "N/A" else "$pulseRate BPM"
            resultTextView.text = context.getString(R.string.result_label, breathingRateText, pulseRateText)
        } else {
            resultTextView.text = "Breathing Rate: $breathingRateText"
        }
    }

    /**
     * Displays an error message below the results view on the UI thread.
     */
    private fun updateErrorText(errorMessage: String) {
        post {
            if (errorMessage.isEmpty()) {
                resultErrorTextView.visibility = GONE
                resultErrorTextView.text = ""
            } else {
                resultErrorTextView.text = "Error: $errorMessage"
                resultErrorTextView.visibility = VISIBLE
            }
        }
    }
}
