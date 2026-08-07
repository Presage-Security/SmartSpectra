// MinimalAppTestHelpers.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_minimal

import android.Manifest
import android.app.Activity
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry

internal fun grantCameraPermission() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val packageName = instrumentation.targetContext.packageName
    runCatching {
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.CAMERA)
    }.onFailure {
        instrumentation.uiAutomation
            .executeShellCommand("pm grant $packageName ${Manifest.permission.CAMERA}")
            .close()
    }
}

internal fun waitForCondition(
    waitReason: String,
    timeoutMs: Long = 30_000L,
    condition: () -> Boolean,
) {
    val startedAt = System.currentTimeMillis()
    while (!condition()) {
        if (System.currentTimeMillis() - startedAt > timeoutMs) {
            throw AssertionError("Timed out after ${timeoutMs}ms waiting for $waitReason")
        }
        Thread.sleep(100)
    }
}

private fun executeShellCommand(command: String) {
    InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand(command)
        .close()
}

private fun readShellCommand(command: String): String =
    ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).use { it.readBytes().decodeToString() }

/**
 * Force the screen awake and the keyguard away before a focus-dependent test.
 *
 * A dozing display leaves the keyguard owning window focus while the activity
 * still reports RESUMED, so `hasWindowFocus()` never turns true. Unattended
 * test devices and emulators are prone to this: the screen-off timeout and
 * "stay awake" flag live in per-device state, not in the app, so a given
 * machine may or may not have them set, and the display has ample time to doze
 * between test phases.
 *
 * Deliberately transient — no global device setting is written, so running the
 * suite against a real phone leaves its power configuration untouched.
 */
internal fun ensureScreenOnAndUnlocked() {
    runCatching {
        executeShellCommand("input keyevent KEYCODE_WAKEUP")
        executeShellCommand("wm dismiss-keyguard")
    }
}

/** Window/keyguard state, so a focus timeout names its own cause. */
private fun windowFocusDiagnostics(): String = runCatching {
    val dump = readShellCommand("dumpsys window").lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("mCurrentFocus") || it.startsWith("isKeyguardShowing") }
        .distinct()
        .take(4)
        .joinToString("; ")
    dump.ifBlank { "no mCurrentFocus/isKeyguardShowing lines in dumpsys window" }
}.getOrElse { "window state unavailable (${it.message})" }

/**
 * Block until the activity's window reports `hasWindowFocus()`. Espresso's
 * RootViewPicker polls window focus with a hard-coded 10s budget and throws
 * `RootViewWithoutFocusException` if the activity is RESUMED but not yet
 * focused — which is the common state on a freshly booted emulator
 * (API 36 in particular), where system dialogs, the launcher pip, and
 * lingering boot animations can hold focus longer than that.
 *
 * Call this once right after `ActivityScenario.launch(...)` and before the
 * first `onView(...).perform(...)`; the rest of the test stays in Espresso.
 */
internal fun <A : Activity> waitForActivityWindowFocus(
    scenario: ActivityScenario<A>,
    timeoutMs: Long = 30_000L,
) {
    ensureScreenOnAndUnlocked()

    // Per-activity, transient, and — unlike a one-shot KEYCODE_WAKEUP — still
    // in force if the display tries to doze again later in a long test.
    runCatching {
        scenario.onActivity { activity ->
            activity.setTurnScreenOn(true)
            activity.setShowWhenLocked(true)
        }
    }

    try {
        waitForCondition("activity window to gain focus", timeoutMs) {
            var focused = false
            scenario.onActivity { focused = it.hasWindowFocus() }
            focused
        }
    } catch (timeout: AssertionError) {
        // Bare "timed out waiting for focus" is indistinguishable from a dozen
        // causes; attach what actually held focus.
        throw AssertionError("${timeout.message} — ${windowFocusDiagnostics()}", timeout)
    }
}
