// MinimalAppTestHelpers.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_minimal

import android.Manifest
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
