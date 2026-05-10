// PermissionsRequestFragment.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.ui.screening

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.presagetech.smartspectra_example.R

internal class PermissionsRequestFragment: Fragment() {

    private lateinit var requestButton: View
    private lateinit var settingsButton: View
    private var hasRequestedCameraPermission = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        updatePermissionUiState()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_permissions_layout, container, false).also {
            requestButton = it.findViewById(R.id.button_allow)
            settingsButton = it.findViewById(R.id.button_open_settings)
        }

        requestButton.setOnClickListener {
            requestPermissionDialog()
        }
        settingsButton.setOnClickListener {
            openPermissionsSettings()
        }

        updatePermissionUiState()
        return view
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUiState()
    }

    /** Launches the Android camera permission dialog. */
    private fun requestPermissionDialog() {
        hasRequestedCameraPermission = true
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    /** Opens the system settings screen for this app. */
    private fun openPermissionsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", requireContext().packageName, null)
        intent.data = uri
        requireContext().startActivity(intent)
    }

    private fun updatePermissionUiState() {
        if (hasCameraPermission()) return

        // Keep in-app permission request available for all denied states
        // (including "Ask every time"), and show settings as an additional
        // fallback when Android indicates rationale should no longer be shown.
        requestButton.visibility = View.VISIBLE
        val shouldSuggestSettings = hasRequestedCameraPermission &&
            !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        settingsButton.visibility = if (shouldSuggestSettings) View.VISIBLE else View.GONE
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
