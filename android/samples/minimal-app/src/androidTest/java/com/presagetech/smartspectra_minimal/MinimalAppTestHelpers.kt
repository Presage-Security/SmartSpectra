// MinimalAppTestHelpers.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_minimal

import android.Manifest
import android.app.Activity
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

/**
 * Block until the activity's window reports `hasWindowFocus()`. Espresso's
 * RootViewPicker polls window focus with a hard-coded 10s budget and throws
 * `RootViewWithoutFocusException` if the activity is RESUMED but not yet
 * focused — which is the common state on a freshly booted CI emulator
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
    waitForCondition("activity window to gain focus", timeoutMs) {
        var focused = false
        scenario.onActivity { focused = it.hasWindowFocus() }
        focused
    }
}
