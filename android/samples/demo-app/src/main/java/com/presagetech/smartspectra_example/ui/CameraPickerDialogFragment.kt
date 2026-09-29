// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.presagetech.smartspectra.CameraLensType
import com.presagetech.smartspectra.CameraFacing
import com.presagetech.smartspectra.CameraInfo
import com.presagetech.smartspectra.CameraSelection
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraException
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra_example.R
import com.presagetech.smartspectra_example.userFacingMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Discover on every opening/refresh; keep opaque IDs only for this snapshot. */
internal class CameraPickerDialogFragment : DialogFragment() {
    private val sdk = SmartSpectraSdk.shared
    private var cameras = emptyList<CameraInfo>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var statusText: TextView
    private var discovery: Job? = null
    private var loading = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val padding = (24 * resources.displayMetrics.density).toInt()
        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding / 2)
            addView(TextView(context).apply {
                setText(R.string.demo_select_camera)
                textSize = 20f
            })
            statusText = TextView(context).also { addView(it) }
        }
        adapter = ArrayAdapter(context, android.R.layout.simple_list_item_single_choice)
        val picker = AlertDialog.Builder(context)
            .setCustomTitle(heading)
            .setSingleChoiceItems(adapter, -1) { _, index ->
                cameras.getOrNull(index)?.let { select(CameraSelection.ById(it.id), adapter.getItem(index).orEmpty()) }
            }
            .setPositiveButton(R.string.demo_camera_default, null)
            .setNeutralButton(R.string.demo_camera_refresh, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        sdk.processingStatus.observe(this) { updateEnabled(picker) }
        return picker
    }

    override fun onStart() {
        super.onStart()
        val picker = requireDialog() as AlertDialog
        picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            select(CameraSelection.Default, getString(R.string.demo_camera_default))
        }
        picker.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { refresh() }
        refresh()
    }

    override fun onStop() {
        discovery?.cancel()
        super.onStop()
    }

    private fun updateEnabled(picker: AlertDialog) {
        val idle = sdk.processingStatus.value == ProcessingStatus.IDLE
        picker.listView?.isEnabled = idle && !loading
        picker.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = idle && !loading
        picker.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = canOpen(sdk.processingStatus.value) && !loading
        if (!canOpen(sdk.processingStatus.value)) statusText.setText(R.string.demo_camera_stop_first)
    }

    private fun refresh() {
        val picker = requireDialog() as AlertDialog
        if (!canOpen(sdk.processingStatus.value)) {
            updateEnabled(picker)
            return
        }
        discovery?.cancel()
        loading = true
        cameras = emptyList()
        adapter.clear()
        picker.listView.clearChoices()
        statusText.setText(R.string.demo_camera_loading)
        updateEnabled(picker)
        discovery = lifecycleScope.launch {
            try {
                // A failed start leaves ERROR; await cleanup before changing sources.
                if (sdk.processingStatus.value == ProcessingStatus.ERROR) sdk.stop()
                cameras = SmartSpectraSdk.availableCameras(requireContext())
                adapter.addAll(cameras.map { camera ->
                    val facing = getString(when (camera.facing) {
                        CameraFacing.FRONT -> R.string.demo_camera_front
                        CameraFacing.BACK -> R.string.demo_camera_back
                        CameraFacing.UNKNOWN -> R.string.demo_camera_unknown
                    })
                    val lens = getString(when (camera.lensType) {
                        CameraLensType.WIDE_ANGLE -> R.string.demo_camera_wide_angle
                        CameraLensType.ULTRA_WIDE -> R.string.demo_camera_ultra_wide
                        CameraLensType.TELEPHOTO -> R.string.demo_camera_telephoto
                        CameraLensType.UNKNOWN -> R.string.demo_camera_unknown_lens
                    })
                    getString(R.string.demo_camera_label,
                        camera.name ?: getString(R.string.demo_camera_unnamed), facing, camera.id, lens)
                })
                statusText.setText(if (cameras.isEmpty()) R.string.demo_camera_empty else R.string.demo_camera_hint)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError(error)
            } finally {
                loading = false
                updateEnabled(picker)
            }
        }
    }

    private fun select(selection: CameraSelection, label: String) {
        if (loading) return
        try {
            // The SDK checks again in case processing began after discovery.
            sdk.useCamera(selection)
            Toast.makeText(requireContext(), getString(R.string.demo_camera_selected, label), Toast.LENGTH_SHORT).show()
            dismiss()
        } catch (error: Exception) {
            (requireDialog() as AlertDialog).listView.clearChoices()
            showError(error)
        }
    }

    private fun showError(error: Exception) {
        statusText.text = (error as? SmartSpectraException)?.error?.userFacingMessage(requireContext())
            ?: getString(R.string.demo_camera_failed)
    }

    companion object {
        private const val TAG = "cameraPicker"

        fun canOpen(status: ProcessingStatus?): Boolean =
            status == ProcessingStatus.IDLE || status == ProcessingStatus.ERROR

        fun show(manager: FragmentManager) {
            if (!manager.isStateSaved && manager.findFragmentByTag(TAG) == null) {
                CameraPickerDialogFragment().show(manager, TAG)
            }
        }
    }
}
