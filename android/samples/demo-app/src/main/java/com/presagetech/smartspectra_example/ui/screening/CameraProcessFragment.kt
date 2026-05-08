// CameraProcessFragment.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.ui.screening

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.presagetech.smartspectra.CameraPosition
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraError
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.ValidationStatus
import com.presagetech.smartspectra_example.R
import com.presagetech.smartspectra_example.ui.AmbientLightBrightnessController
import com.presagetech.smartspectra_example.userFacingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

internal class CameraProcessFragment : Fragment() {

    private lateinit var hintText: TextView
    private lateinit var recordingButton: AppCompatTextView
    private lateinit var previewDisplayView: ImageView
    private lateinit var previewIdleDimView: View
    private lateinit var screeningPlotView: ScreeningPlotView
    private lateinit var flipCameraButton: MaterialButton

    private val sdk: SmartSpectraSdk by lazy { SmartSpectraSdk.shared }

    private var ambientLightController: AmbientLightBrightnessController? = null
    private var latestProcessingStatus: ProcessingStatus = ProcessingStatus.IDLE
    private var latestValidationStatus: ValidationStatus? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startProcessing()
        } else {
            previewDisplayView.visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_camera_process_layout, container, false).also {
            previewDisplayView = it.findViewById(R.id.preview_view)
            previewIdleDimView = it.findViewById(R.id.preview_idle_dim)
            hintText = it.findViewById(R.id.text_hint)
            recordingButton = it.findViewById(R.id.button_recording)
            screeningPlotView = it.findViewById(R.id.screeningPlotView)
            flipCameraButton = it.findViewById(R.id.button_flip_camera)
        }

        view.findViewById<ImageButton>(R.id.info_button).setOnClickListener { showInfoDialog() }

        screeningPlotView.bindLifecycleOwner(viewLifecycleOwner)
        flipCameraButton.isVisible = SHOW_CONTROLS_IN_SCREENING_VIEW

        hintText.visibility = View.GONE
        previewDisplayView.visibility = View.GONE

        view.findViewById<Toolbar>(R.id.toolbar).also {
            (requireActivity() as AppCompatActivity).setSupportActionBar(it)
            it.setNavigationIcon(R.drawable.ic_arrow_back)
            it.setNavigationOnClickListener { _ ->
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        sdk.imageOutput.observe(viewLifecycleOwner) { bitmap ->
            previewDisplayView.setImageBitmap(bitmap)
        }
        sdk.processingStatus.observe(viewLifecycleOwner) { processingStatus ->
            onProcessingStatusChanged(processingStatus)
        }
        sdk.validationStatus.observe(viewLifecycleOwner) { status ->
            latestValidationStatus = status
            updateHintText()
        }
        sdk.error.observe(viewLifecycleOwner) { error ->
            renderError(error)
        }

        recordingButton.setOnClickListener {
            toggleProcessing()
        }

        flipCameraButton.setOnClickListener { flipCamera() }

        return view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        ambientLightController = AmbientLightBrightnessController(context) { activity?.window }
        ambientLightController?.start(continuousAdjustment = false)
    }

    override fun onDetach() {
        super.onDetach()
        ambientLightController?.stop()
        ambientLightController = null
    }

    override fun onResume() {
        super.onResume()
        startProcessing()
    }

    private fun startProcessing() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            previewDisplayView.visibility = View.GONE
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        sdk.config.imageOutputEnabled = true
        CoroutineScope(Dispatchers.Main.immediate).launch {
            runCatching { sdk.start() }
        }
        previewDisplayView.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            runCatching { sdk.stop() }
        }
        previewDisplayView.visibility = View.GONE
    }

    private fun showInfoDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Tip")
        builder.setMessage("Please ensure the subject’s face, shoulders, and upper chest are in view and remove any clothing that may impede visibility. Please refer to Instructions For Use for more information.")
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun flipCamera() {
        sdk.config.cameraPosition = when (sdk.config.cameraPosition) {
            CameraPosition.FRONT -> CameraPosition.BACK
            CameraPosition.BACK -> CameraPosition.FRONT
        }
    }

    private fun toggleProcessing() {
        when (sdk.processingStatus.value) {
            ProcessingStatus.IDLE, ProcessingStatus.ERROR -> {
                sdk.config.imageOutputEnabled = true
                CoroutineScope(Dispatchers.Main.immediate).launch {
                    runCatching { sdk.start() }
                }
            }

            ProcessingStatus.RUNNING -> {
                CoroutineScope(Dispatchers.Main.immediate).launch {
                    runCatching { sdk.stop() }
                }
            }

            ProcessingStatus.STARTING, ProcessingStatus.STOPPING, null -> Unit
        }
    }

    private fun onProcessingStatusChanged(status: ProcessingStatus) {
        latestProcessingStatus = status
        when (status) {
            ProcessingStatus.IDLE -> {
                latestValidationStatus = null
                flipCameraButton.isEnabled = true
                recordingButton.isEnabled = true
                recordingButton.text = getString(R.string.start)
                recordingButton.textSize = 20.0f
                recordingButton.setBackgroundResource(R.drawable.record_background)
                updateHintText()
                previewDisplayView.visibility = View.VISIBLE
                previewIdleDimView.visibility = View.VISIBLE
            }

            ProcessingStatus.STARTING -> {
                flipCameraButton.isEnabled = false
                updateHintText()
                previewDisplayView.visibility = View.VISIBLE
                previewIdleDimView.visibility = View.GONE
            }

            ProcessingStatus.RUNNING -> {
                flipCameraButton.isEnabled = false
                recordingButton.isEnabled = true
                recordingButton.text = getString(R.string.stop)
                recordingButton.textSize = 20.0f
                recordingButton.setBackgroundResource(R.drawable.record_background)
                updateHintText()
                previewDisplayView.visibility = View.VISIBLE
                previewIdleDimView.visibility = View.GONE
            }

            ProcessingStatus.STOPPING -> {
                flipCameraButton.isEnabled = false
                recordingButton.isEnabled = false
                recordingButton.text = getString(R.string.stop)
                recordingButton.textSize = 20.0f
                recordingButton.setBackgroundResource(R.drawable.record_background)
                updateHintText()
                previewIdleDimView.visibility = View.GONE
            }

            ProcessingStatus.ERROR -> {
                Timber.e("Presage Processing error")
                flipCameraButton.isEnabled = true
                recordingButton.isEnabled = true
                recordingButton.text = getString(R.string.start)
                recordingButton.textSize = 20.0f
                recordingButton.setBackgroundResource(R.drawable.record_background)
                updateHintText()
                previewDisplayView.visibility = View.VISIBLE
                previewIdleDimView.visibility = View.VISIBLE
            }
        }
    }

    private fun renderError(error: SmartSpectraError?) {
        if (error == null || sdk.processingStatus.value != ProcessingStatus.ERROR) return
        hintText.text = error.userFacingMessage(requireContext())
        hintText.visibility = View.VISIBLE
    }

    private fun updateHintText() {
        if (latestProcessingStatus == ProcessingStatus.ERROR) {
            hintText.text = sdk.error.value?.userFacingMessage(requireContext())
                ?: getString(R.string.monitoring_status_error)
            hintText.visibility = View.VISIBLE
            return
        }

        val hint = latestValidationStatus?.hint.orEmpty()
        if (
            hint.isNotBlank() &&
            latestProcessingStatus in setOf(ProcessingStatus.STARTING, ProcessingStatus.RUNNING)
        ) {
            hintText.text = getString(R.string.monitoring_validation_hint_format, hint)
            hintText.visibility = View.VISIBLE
        } else {
            hintText.visibility = View.GONE
        }
    }

    private companion object {
        private const val SHOW_CONTROLS_IN_SCREENING_VIEW = true
    }
}
