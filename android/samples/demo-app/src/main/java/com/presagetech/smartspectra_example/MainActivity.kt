// MainActivity.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateViewModelFactory
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra_example.checkup.CheckupFragment
import com.presagetech.smartspectra_example.checkup.CheckupViewModel
import com.presagetech.smartspectra_example.headless.HeadlessProcessingFragment

/**
 * Hosts the two demo flows and preconfigure the SmartSpectra SDK.
 */
class MainActivity : AppCompatActivity() {

    // SmartSpectra SDK settings

    // (Required) Authentication. Only need to use one of the two options: API Key or OAuth below
    // Authentication with OAuth is currently only supported for apps in the Play Store
    // Option 1: (Authentication with API Key) Set the API key. Obtain the API key from https://physiology.presagetech.com. Leave default or remove if you want to use OAuth. OAuth overrides the API key.
    private var apiKey = "YOUR_API_KEY"

    // Option 2: (OAuth) If you want to use OAuth, copy the OAuth config (`presage_services.xml`) from PresageTech's developer portal (<https://physiology.presagetech.com/>) to your src/main/res/xml/ directory.
    // No additional code is needed for OAuth.

    //Check for optional SDK configuration inside of the `CheckupFragment` and `HeadlessProcessingFragment`

    private val smartSpectraSdk: SmartSpectraSdk = SmartSpectraSdk.shared

    val checkupViewModel: CheckupViewModel by viewModels {
        SavedStateViewModelFactory(application, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        configureSmartSpectra()

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_checkup -> {
                    switchFragment(CHECKUP_TAG) { CheckupFragment() }
                    true
                }

                R.id.navigation_headless -> {
                    switchFragment(HEADLESS_TAG) { HeadlessProcessingFragment() }
                    true
                }

                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.navigation_checkup
        }
    }

    private fun configureSmartSpectra() {
        smartSpectraSdk.apply {
            //Required configurations: Authentication
            config.apiKey = apiKey   // Use this if you are authenticating with an API key
            // If OAuth is configured, it will automatically override the API key
        }
    }

    private fun switchFragment(tag: String, factory: () -> Fragment) {
        val fragmentManager = supportFragmentManager
        val existing = fragmentManager.findFragmentByTag(tag)
        val fragment = existing ?: factory()
        fragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
    }

    companion object {
        private const val CHECKUP_TAG = "checkup_fragment"
        private const val HEADLESS_TAG = "headless_fragment"
    }
}
