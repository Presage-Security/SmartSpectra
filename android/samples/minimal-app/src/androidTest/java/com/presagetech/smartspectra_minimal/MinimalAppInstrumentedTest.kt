// MinimalAppInstrumentedTest.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_minimal

import android.content.pm.ActivityInfo
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
import kotlinx.coroutines.runBlocking
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
        requireApiKeyAvailable()
        grantCameraPermission()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForActivityWindowFocus(scenario)
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
                idleStateReached(scenario)
            }
        }
    }

    /**
     * Stresses the lifecycle state machine: tap Start → wait RUNNING → tap Stop →
     * wait IDLE, repeated [RAPID_CYCLE_COUNT] times back-to-back. Catches races
     * in start/stop transitions, leaked camera bindings between measurements,
     * and listener accumulation.
     */
    @Test
    fun rapidStartStopCyclesStayClean() {
        requireApiKeyAvailable()
        grantCameraPermission()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForActivityWindowFocus(scenario)
            repeat(RAPID_CYCLE_COUNT) { cycle ->
                onView(withId(R.id.toggle_button)).perform(click())

                waitForCondition(
                    "minimal app to reach RUNNING on rapid cycle $cycle",
                    timeoutMs = RAPID_CYCLE_TIMEOUT_MS,
                ) {
                    failOnSdkError("rapid start/stop cycle $cycle (starting)")
                    SmartSpectraSdk.shared.processingStatus.value == ProcessingStatus.RUNNING
                }

                onView(withId(R.id.toggle_button)).perform(click())

                waitForCondition(
                    "minimal app to reach IDLE on rapid cycle $cycle",
                    timeoutMs = RAPID_CYCLE_TIMEOUT_MS,
                ) {
                    failOnSdkError("rapid start/stop cycle $cycle (stopping)")
                    idleStateReached(scenario)
                }
            }
        }
    }

    /**
     * Forces a real orientation change mid-measurement (portrait → landscape →
     * portrait) by toggling [android.app.Activity.requestedOrientation]. Each
     * toggle triggers the same lifecycle path the system uses for a sensor
     * rotation: onPause → onDestroy → onCreate → onResume, with the surface
     * resized to the new aspect. MainActivity.onPause() requests a stop on
     * the way down; the test confirms the SDK settles to IDLE on the new
     * activity instance without an ERROR and without leaving the state
     * machine wedged.
     */
    @Test
    fun realOrientationChangeMidMeasurementSettlesCleanly() {
        requireApiKeyAvailable()
        grantCameraPermission()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                waitForActivityWindowFocus(scenario)
                onView(withId(R.id.toggle_button)).perform(click())

                waitForCondition(
                    "minimal app to enter RUNNING before orientation change",
                    timeoutMs = RAPID_CYCLE_TIMEOUT_MS,
                ) {
                    failOnSdkError("orientation change test (entering RUNNING)")
                    SmartSpectraSdk.shared.processingStatus.value == ProcessingStatus.RUNNING
                }

                // Give the SDK time to actually process frames before we
                // interrupt — a rotation that hits within milliseconds of
                // RUNNING only stresses the start handshake, not a steady-
                // state measurement being torn down. Asserting still-RUNNING
                // afterwards locks in that the warmup itself didn't regress.
                Thread.sleep(OPTIONAL_RUN_DURATION_MS)
                assertStillRunningWithoutSdkError("orientation change test (warmup)")

                // Flip to landscape — recreates the activity in the new
                // orientation. Then flip back to portrait so a real human
                // watching can see both rotations on the device screen and
                // we don't leave the device pinned for subsequent tests.
                rotateAndWaitForRecreate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                rotateAndWaitForRecreate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

                waitForCondition(
                    "minimal app to settle to IDLE after orientation flip",
                    timeoutMs = STOP_SETTLE_TIMEOUT_MS,
                ) {
                    failOnSdkError("orientation change test (after flips)")
                    idleStateReached(scenario)
                }
            } finally {
                runCatching {
                    scenario.onActivity { activity ->
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }
    }

    private fun rotateAndWaitForRecreate(
        scenario: ActivityScenario<MainActivity>,
        orientation: Int,
    ) {
        var preFlipActivity: MainActivity? = null
        scenario.onActivity { preFlipActivity = it }

        scenario.onActivity { activity ->
            activity.requestedOrientation = orientation
        }

        // Activity#requestedOrientation triggers an asynchronous recreate.
        // Wait until ActivityScenario hands us a *different* activity
        // instance and its current rotation matches what we requested.
        waitForCondition(
            "activity to recreate into requested orientation",
            timeoutMs = STOP_SETTLE_TIMEOUT_MS,
        ) {
            var current: MainActivity? = null
            var currentOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            scenario.onActivity {
                current = it
                currentOrientation = it.resources.configuration.orientation
            }
            current != null &&
                current !== preFlipActivity &&
                when (orientation) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ->
                        currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ->
                        currentOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                    else -> true
                }
        }
    }

    /**
     * Verifies that issuing stop() while the SDK is still in STARTING settles
     * cleanly to IDLE without leaking a RUNNING state or surfacing an ERROR.
     * Uses direct [SmartSpectraSdk.stop] so the stop arrives faster than a
     * second button tap could deliver it.
     */
    @Test
    fun stopDuringStartingSettlesToIdle() {
        requireApiKeyAvailable()
        grantCameraPermission()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForActivityWindowFocus(scenario)
            onView(withId(R.id.toggle_button)).perform(click())

            // Wait until the SDK actually enters STARTING — that's the window we
            // want to cancel from. If we slept blindly we'd race the transition.
            waitForCondition(
                "SDK to enter STARTING before issuing stop",
                timeoutMs = STARTING_TRANSITION_TIMEOUT_MS,
            ) {
                SmartSpectraSdk.shared.processingStatus.value == ProcessingStatus.STARTING ||
                    SmartSpectraSdk.shared.processingStatus.value == ProcessingStatus.RUNNING
            }
            // If we already raced past STARTING, that's fine — stop-from-RUNNING
            // is exercised elsewhere. The interesting case is when this fires
            // while the value is still STARTING; on slower devices that holds
            // long enough that this is the common path.
            runBlocking { SmartSpectraSdk.shared.stop() }

            waitForCondition(
                "minimal app to settle to IDLE after stop-during-STARTING",
                timeoutMs = STOP_SETTLE_TIMEOUT_MS,
            ) {
                failOnSdkError("stop during STARTING")
                idleStateReached(scenario)
            }
        }
    }

    /** Opt-in escape for local runs without secrets: set allowTestSkip=true via
     *  `-e allowTestSkip true`. Default is to FAIL. */
    private fun allowTestSkip(): Boolean =
        InstrumentationRegistry.getArguments().getString("allowTestSkip")
            ?.trim()?.equals("true", ignoreCase = true) == true

    /** Fail-closed prerequisite gate. A missing prerequisite FAILS the test by
     *  default so CI can never silently drop this coverage — a skip reads as
     *  green. Opt into skipping with allowTestSkip=true. */
    private fun requireForTest(satisfied: Boolean, what: String) {
        if (satisfied) return
        if (allowTestSkip()) {
            assumeTrue("$what [skipped: allowTestSkip=true]", false)
        }
        org.junit.Assert.fail("$what — set allowTestSkip=true (-e allowTestSkip true) to skip locally.")
    }

    private fun requireApiKeyAvailable() {
        requireForTest(
            BuildConfig.SMARTSPECTRA_API_KEY.isNotBlank(),
            "SMARTSPECTRA_API_KEY (BuildConfig) not set",
        )
    }

    private fun idleStateReached(scenario: ActivityScenario<MainActivity>): Boolean {
        var statusText = ""
        var buttonText = ""
        scenario.onActivity { activity ->
            statusText = activity.findViewById<TextView>(R.id.status_label).text.toString()
            buttonText = activity.findViewById<MaterialButton>(R.id.toggle_button).text.toString()
        }
        return statusText == targetString(R.string.status_idle) &&
            buttonText == targetString(R.string.toggle_start) &&
            SmartSpectraSdk.shared.processingStatus.value == ProcessingStatus.IDLE
    }

    private fun failOnSdkError(context: String) {
        SmartSpectraSdk.shared.error.value?.let { error ->
            throw AssertionError("Unexpected SDK error during $context: $error")
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
        private const val RAPID_CYCLE_COUNT = 5
        private const val RAPID_CYCLE_TIMEOUT_MS = 30_000L
        private const val STARTING_TRANSITION_TIMEOUT_MS = 15_000L
        private const val STOP_SETTLE_TIMEOUT_MS = 30_000L
    }
}
