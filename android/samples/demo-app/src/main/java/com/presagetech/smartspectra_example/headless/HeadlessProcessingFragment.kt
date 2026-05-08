// HeadlessProcessingFragment.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.headless

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.presagetech.smartspectra_example.ui.screening.ScreeningPlotView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.presagetech.smartspectra.CameraPosition
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraError
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.ValidationStatus
import com.presagetech.smartspectra_example.MainActivity
import com.presagetech.smartspectra_example.R
import com.presagetech.smartspectra_example.userFacingMessage
import kotlinx.coroutines.launch

/**
 * Implementation of the headless demo using [SmartSpectraSdk] with [ScreeningPlotView].
 */
class HeadlessProcessingFragment : Fragment() {
    private val viewModel
        get() = (requireActivity() as MainActivity).checkupViewModel

    private lateinit var startStopButton: MaterialButton
    private lateinit var previewSwitch: SwitchMaterial
    private lateinit var previewImage: ImageView
    private lateinit var statusHintText: TextView
    private lateinit var errorText: TextView
    private lateinit var fabInsightsChat: FloatingActionButton
    // ScreeningPlotView for rendering pulse, breathing, and blood pressure plots during continuous measurements.
    private lateinit var vitalsView: ScreeningPlotView

    // SmartSpectra SDK settings
    // define front or back camera to use
    private val sdk by lazy { SmartSpectraSdk.shared.apply {
        config.cameraPosition = CameraPosition.FRONT
    } }

    private var isMonitoring = false
    private var latestProcessingStatus: ProcessingStatus = ProcessingStatus.IDLE
    private var latestValidationStatus: ValidationStatus? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startMonitoring()
        } else {
            updateStartButtonState()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_headless_processing, container, false)
        startStopButton = view.findViewById(R.id.button_start_stop)
        previewSwitch = view.findViewById(R.id.switch_preview)
        previewImage = view.findViewById(R.id.headless_preview_image)
        statusHintText = view.findViewById(R.id.status_hint_text)
        errorText = view.findViewById(R.id.error_text)
        vitalsView = view.findViewById(R.id.headless_vitals_view)
        fabInsightsChat = view.findViewById(R.id.fab_insights_chat)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vitalsView.bindLifecycleOwner(viewLifecycleOwner)
        applySharedMetricSettingsToUi()
        statusHintText.visibility = View.GONE
        sdk.processingStatus.observe(viewLifecycleOwner) { updateProcessingStatus(it) }
        sdk.validationStatus.observe(viewLifecycleOwner) { status ->
            latestValidationStatus = status
            updateStatusText()
        }
        sdk.error.observe(viewLifecycleOwner) { error ->
            updateErrorState(error)
        }
        sdk.imageOutput.observe(viewLifecycleOwner) { bitmap ->
            previewImage.setImageBitmap(bitmap)
            previewImage.visibility =
                if (bitmap != null && previewSwitch.isChecked) View.VISIBLE else View.GONE
        }
        sdk.config.imageOutputEnabled = previewSwitch.isChecked
        updateStartButtonState()

        startStopButton.setOnClickListener {
            if (isMonitoring) stopMonitoring() else startMonitoring()
        }

        previewSwitch.setOnCheckedChangeListener { _, isChecked ->
            sdk.config.imageOutputEnabled = isChecked
            if (!isChecked) {
                previewImage.setImageBitmap(null)
                previewImage.visibility = View.GONE
            } else if (sdk.imageOutput.value != null) {
                previewImage.visibility = View.VISIBLE
            }
        }

        fabInsightsChat.setOnClickListener {
            InsightsChatBottomSheet().show(childFragmentManager, InsightsChatBottomSheet.TAG)
        }
    }

    override fun onPause() {
        super.onPause()
        stopMonitoring()
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        applyRequestedMetrics()
        sdk.config.imageOutputEnabled = previewSwitch.isChecked
        lifecycleScope.launch {
            runCatching { sdk.start() }
                .onFailure {
                    isMonitoring = false
                    startStopButton.text = getString(R.string.smart_spectra_headless_start)
                    updateStartButtonState()
                }
        }
    }

    private fun applyRequestedMetrics() {
        sdk.config.requestedMetrics = buildList {
            addAll(SmartSpectraConfig.breathingMetrics)
            if (viewModel.cardioMeasurementsEnabled.value) {
                addAll(SmartSpectraConfig.cardioMetrics)
            }
            if (viewModel.faceMetricsEnabled.value) {
                addAll(SmartSpectraConfig.faceMetrics)
            }
        }
    }

    private fun applySharedMetricSettingsToUi() {
        vitalsView.setCardioMeasurementsEnabled(viewModel.cardioMeasurementsEnabled.value)
        vitalsView.setFacialExpressionEnabled(viewModel.faceMetricsEnabled.value)
    }

    private fun stopMonitoring() {
        lifecycleScope.launch {
            runCatching { sdk.stop() }
            finalizeStoppedUi()
        }
    }

    private fun finalizeStoppedUi() {
        sdk.config.imageOutputEnabled = false
        latestValidationStatus = null
        updateStatusText()
        previewSwitch.isChecked = false
        previewImage.setImageBitmap(null)
        previewImage.visibility = View.GONE
        isMonitoring = false
        startStopButton.text = getString(R.string.smart_spectra_headless_start)
        updateStartButtonState()
    }

    private fun finalizeStoppedUiPreservingError() {
        sdk.config.imageOutputEnabled = false
        previewSwitch.isChecked = false
        previewImage.setImageBitmap(null)
        previewImage.visibility = View.GONE
        isMonitoring = false
        startStopButton.text = getString(R.string.smart_spectra_headless_start)
        updateStartButtonState()
    }

    private fun updateErrorState(error: SmartSpectraError?) {
        errorText.isVisible = error != null
        errorText.text = if (error != null) {
            error.userFacingMessage(requireContext())
        } else {
            getString(R.string.smart_spectra_headless_error_fallback)
        }
        updateStartButtonState()
    }

    private fun updateStartButtonState() {
        val inputBlocked = sdk.error.value?.code == SmartSpectraError.Code.INPUT_UNAVAILABLE
        val canInteract = !inputBlocked || isMonitoring
        startStopButton.isEnabled = canInteract
        startStopButton.alpha = if (canInteract) 1.0f else 0.6f
    }

    private fun updateProcessingStatus(status: ProcessingStatus) {
        latestProcessingStatus = status
        updateStatusText()
        when (status) {
            ProcessingStatus.RUNNING -> {
                isMonitoring = true
                startStopButton.text = getString(R.string.smart_spectra_headless_stop)
                updateStartButtonState()
            }
            ProcessingStatus.ERROR -> finalizeStoppedUiPreservingError()
            else -> if (!isMonitoring) {
                startStopButton.text = getString(R.string.smart_spectra_headless_start)
                updateStartButtonState()
            }
        }
    }

    private fun updateStatusText() {
        val hint = latestValidationStatus?.hint.orEmpty()
        if (hint.isBlank()) {
            statusHintText.visibility = View.GONE
        } else {
            statusHintText.text = getString(R.string.smart_spectra_headless_status_format, hint)
            statusHintText.visibility = View.VISIBLE
        }
    }
}
