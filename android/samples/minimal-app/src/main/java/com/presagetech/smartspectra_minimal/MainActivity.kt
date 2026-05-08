// MainActivity.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_minimal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.presagetech.smartspectra.CameraPosition
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraSdk
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val sdk by lazy { SmartSpectraSdk.shared }

    // Set your API key from https://physiology.presagetech.com
    private val apiKey = "YOUR_API_KEY"

    private lateinit var previewImage: ImageView
    private lateinit var heartRateLabel: TextView
    private lateinit var breathingRateLabel: TextView
    private lateinit var breathingGraphView: SignalGraphView
    private lateinit var bloodPressureGraphView: SignalGraphView
    private lateinit var statusLabel: TextView
    private lateinit var toggleButton: MaterialButton
    private lateinit var insightLabel: TextView
    private lateinit var insightButton: MaterialButton

    private var pendingInsightRequestId: Int? = null
    private var latestBreathingTimestamp: Long = Long.MIN_VALUE
    private var latestPressureTimestamp: Long = Long.MIN_VALUE

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startProcessing()
        } else {
            statusLabel.text = getString(R.string.status_permission_required)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sdk.config.apiKey = apiKey
        sdk.config.imageOutputEnabled = true
        sdk.config.cameraPosition = CameraPosition.FRONT
        sdk.config.requestedMetrics = SmartSpectraConfig.breathingMetrics + SmartSpectraConfig.cardioMetrics
        setContentView(R.layout.activity_main)

        previewImage = findViewById(R.id.preview_image)
        heartRateLabel = findViewById(R.id.heart_rate_label)
        breathingRateLabel = findViewById(R.id.breathing_rate_label)
        breathingGraphView = findViewById(R.id.breathing_graph_view)
        bloodPressureGraphView = findViewById(R.id.blood_pressure_graph_view)
        statusLabel = findViewById(R.id.status_label)
        toggleButton = findViewById(R.id.toggle_button)
        insightLabel = findViewById(R.id.insight_label)
        insightButton = findViewById(R.id.insight_button)

        toggleButton.setOnClickListener { toggleProcessing() }
        insightButton.setOnClickListener { requestInsight() }

        bindSdk()
        renderInitialState()
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch {
            when (sdk.processingStatus.value) {
                ProcessingStatus.RUNNING,
                ProcessingStatus.STARTING,
                ProcessingStatus.STOPPING,
                -> runCatching { sdk.stop() }
                else -> Unit
            }
        }
    }

    private fun bindSdk() {
        sdk.processingStatus.observe(this) { updateProcessingStatus(it) }
        sdk.error.observe(this) { error ->
            if (error != null) {
                statusLabel.text = getString(R.string.status_error, error.message)
            }
        }
        sdk.imageOutput.observe(this) { bitmap ->
            previewImage.setImageBitmap(bitmap)
        }
        sdk.metrics.observe(this) { metrics ->
            if (metrics == null) return@observe

            if (metrics.hasCardio()) {
                val pulse = metrics.cardio.pulseRateList
                    .lastOrNull { it.timestamp > 0 }
                    ?.value
                    ?.roundToInt()
                if (pulse != null) {
                    heartRateLabel.text = getString(R.string.heart_rate_value_format, pulse)
                }
            }

            if (metrics.hasBreathing()) {
                if (metrics.breathing.rateCount > 0) {
                    val breathingRate = metrics.breathing.rateList.last().value.roundToInt()
                    breathingRateLabel.text = getString(
                        R.string.breathing_rate_value_format,
                        breathingRate,
                    )
                }

                val newBreathingSamples = metrics.breathing.upperTraceList
                    .filter { it.timestamp > latestBreathingTimestamp }
                if (newBreathingSamples.isNotEmpty()) {
                    latestBreathingTimestamp = newBreathingSamples.last().timestamp
                    breathingGraphView.appendValues(newBreathingSamples.map { it.value })
                }
            }

            if (metrics.hasCardio()) {
                val newPressureSamples = metrics.cardio.arterialPressureTraceList
                    .filter { it.timestamp > latestPressureTimestamp }
                if (newPressureSamples.isNotEmpty()) {
                    latestPressureTimestamp = newPressureSamples.last().timestamp
                    bloodPressureGraphView.appendValues(newPressureSamples.map { it.value })
                }
            }
        }

        sdk.insight.observe(this) { insight ->
            if (insight == null || insight.requestId != pendingInsightRequestId) return@observe

            insightLabel.text = when {
                insight.hasAnalysis() -> insight.analysis
                else -> getString(R.string.insight_error)
            }
            pendingInsightRequestId = null
            insightButton.isEnabled = true
            insightButton.text = getString(R.string.insight_button)
        }
    }

    private fun renderInitialState() {
        resetMeasurementUi()
        statusLabel.text = getString(R.string.status_idle)
        insightButton.isEnabled = true
    }

    private fun toggleProcessing() {
        when (sdk.processingStatus.value) {
            ProcessingStatus.RUNNING -> lifecycleScope.launch { runCatching { sdk.stop() } }
            ProcessingStatus.STARTING, ProcessingStatus.STOPPING -> Unit
            else -> startProcessing()
        }
    }

    private fun startProcessing() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        lifecycleScope.launch {
            resetMeasurementUi()
            runCatching { sdk.start() }
                .onFailure {
                    statusLabel.text = getString(
                        R.string.status_error,
                        it.message ?: getString(R.string.status_error_fallback),
                    )
                }
        }
    }

    private fun requestInsight() {
        if (sdk.processingStatus.value != ProcessingStatus.RUNNING) return
        insightButton.isEnabled = false
        insightButton.text = getString(R.string.insight_loading)
        pendingInsightRequestId = runCatching {
            sdk.requestInsight(getString(R.string.insight_prompt))
        }.onFailure {
            insightLabel.text = getString(R.string.insight_error)
            insightButton.isEnabled = true
            insightButton.text = getString(R.string.insight_button)
        }.getOrNull()
    }

    private fun updateProcessingStatus(status: ProcessingStatus?) {
        when (status) {
            ProcessingStatus.IDLE -> {
                previewImage.setImageBitmap(null)
                statusLabel.text = getString(R.string.status_idle)
                toggleButton.text = getString(R.string.toggle_start)
                toggleButton.isEnabled = true
            }
            ProcessingStatus.STARTING -> {
                statusLabel.text = getString(R.string.status_starting)
                toggleButton.text = getString(R.string.toggle_starting)
                toggleButton.isEnabled = false
            }
            ProcessingStatus.RUNNING -> {
                statusLabel.text = getString(R.string.status_running)
                toggleButton.text = getString(R.string.toggle_stop)
                toggleButton.isEnabled = true
            }
            ProcessingStatus.STOPPING -> {
                statusLabel.text = getString(R.string.status_stopping)
                toggleButton.text = getString(R.string.toggle_stopping)
                toggleButton.isEnabled = false
            }
            ProcessingStatus.ERROR -> {
                previewImage.setImageBitmap(null)
                toggleButton.text = getString(R.string.toggle_start)
                toggleButton.isEnabled = true
            }
            null -> Unit
        }
    }

    private fun resetGraphs() {
        latestBreathingTimestamp = Long.MIN_VALUE
        latestPressureTimestamp = Long.MIN_VALUE
        breathingGraphView.reset()
        bloodPressureGraphView.reset()
    }

    private fun resetMeasurementUi() {
        resetGraphs()
        previewImage.setImageBitmap(null)
        heartRateLabel.text = getString(R.string.heart_rate_placeholder)
        breathingRateLabel.text = getString(R.string.breathing_rate_placeholder)
        insightLabel.text = getString(R.string.insight_placeholder)
        pendingInsightRequestId = null
        insightButton.text = getString(R.string.insight_button)
        insightButton.isEnabled = true
    }
}
