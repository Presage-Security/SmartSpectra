// SmartSpectraActivity.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.presagetech.smartspectra_example.R
import com.presagetech.smartspectra_example.ui.screening.CameraProcessFragment
import com.presagetech.smartspectra_example.ui.screening.ConfigurationErrorFragment
import com.presagetech.smartspectra_example.ui.screening.PermissionsRequestFragment
import timber.log.Timber

/**
 * This is the MainActivity of SmartSpectra module the project structure is base on SingleActivity
 * structure so we used navigation component to handle navigation between module Fragments.
 *
 * */
internal class SmartSpectraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_layout_nav)
    }

    override fun onResume() {
        super.onResume()
        Timber.w("Resumed Smart Spectra Activity")
        retryPermissionFlow()
    }

    /**
     * Re-evaluates camera permission state, then routes to the correct screen.
     * Safe to call after returning from system settings.
     */
    internal fun retryPermissionFlow() {
        val cameraPermissionGranted = hasCameraPermission()
        if (cameraPermissionGranted) {
            openCameraFragment()
        } else {
            openPermissionsFragment()
        }
    }

    /**
     * Navigates to the permissions screen prompting the user for camera access.
     */
    private fun openPermissionsFragment() {
        openFragment(PermissionsRequestFragment())
    }

    private fun openConfigurationErrorFragment(errorMessage: String) {
        openFragment(ConfigurationErrorFragment.newInstance(errorMessage))
    }

    /**
     * Opens the main camera processing fragment once permissions are granted.
     */
    private fun openCameraFragment() {
        openFragment(CameraProcessFragment())
    }

    /**
     * Replaces the current fragment with [fragment] without adding to the back
     * stack.
     */
    private fun openFragment(fragment: Fragment) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.host_fragment)
        if (currentFragment?.javaClass == fragment.javaClass) {
            return
        }
        Timber.i("Opening fragment: ${fragment::class.java.simpleName}")
        supportFragmentManager.beginTransaction()
            .replace(R.id.host_fragment, fragment)
            .commit()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
