// MinimalAppInstrumentedTest.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_minimal

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraSdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MinimalAppInstrumentedTest {

    @Test
    fun minimalAppLaunches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(R.string.status_idle),
                    activity.findViewById<TextView>(R.id.status_label).text.toString(),
                )
                assertEquals(
                    activity.getString(R.string.toggle_start),
                    activity.findViewById<MaterialButton>(R.id.toggle_button).text.toString(),
                )
            }
        }
    }

    @Test
    fun tappingStartWithSuppliedApiKeyRunsThenStops() {
        assumeTrue(
            "Skipping optional minimal app start/stop check without SMARTSPECTRA_API_KEY",
            BuildConfig.SMARTSPECTRA_API_KEY.isNotBlank(),
        )
        grantCameraPermission()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.toggle_button)).perform(click())

            waitForCondition("minimal app to enter RUNNING with supplied API key") {
                var statusText = ""
                scenario.onActivity { activity ->
                    statusText = activity.findViewById<TextView>(R.id.status_label).text.toString()
                }

                val sdk = SmartSpectraSdk.shared
                sdk.error.value?.let { error ->
                    throw AssertionError("Unexpected SDK error while starting minimal app: $error")
                }

                statusText == targetString(R.string.status_running) &&
                    sdk.processingStatus.value == ProcessingStatus.RUNNING
            }

            Thread.sleep(OPTIONAL_RUN_DURATION_MS)
            assertStillRunningWithoutSdkError("minimal app run with supplied API key")

            onView(withId(R.id.toggle_button)).perform(click())

            waitForCondition("minimal app to stop after supplied API key run") {
                var statusText = ""
                var buttonText = ""
                scenario.onActivity { activity ->
                    statusText = activity.findViewById<TextView>(R.id.status_label).text.toString()
                    buttonText = activity.findViewById<MaterialButton>(R.id.toggle_button).text.toString()
                }

                statusText == targetString(R.string.status_idle) &&
                    buttonText == targetString(R.string.toggle_start) &&
                    SmartSpectraSdk.shared.processingStatus.value == ProcessingStatus.IDLE
            }
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun assertStillRunningWithoutSdkError(context: String) {
        val sdk = SmartSpectraSdk.shared
        val error = sdk.error.value
        assertTrue(
            "Expected SDK to still be RUNNING without error during $context: " +
                "status=${sdk.processingStatus.value}, error=$error",
            error == null && sdk.processingStatus.value == ProcessingStatus.RUNNING,
        )
    }

    private companion object {
        private const val OPTIONAL_RUN_DURATION_MS = 5_000L
    }
}
