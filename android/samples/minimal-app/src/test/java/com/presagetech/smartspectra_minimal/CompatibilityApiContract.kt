// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
package com.presagetech.smartspectra_minimal
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.compatibility.DeviceCompatibilityResult
import com.presagetech.smartspectra.compatibility.DeviceCompatibilityVerdict
// Compiles against the minified published release SDK.
internal suspend fun readinessApiContract(sdk: SmartSpectraSdk): DeviceCompatibilityResult {
    sdk.isDeviceCompatible()
    val result = requireNotNull(sdk.getDeviceCompatibilityResult())
    val verdict: DeviceCompatibilityVerdict = result.verdict
    val reason: String = result.reason
    val diagnostics: String = result.diagnostics
    return DeviceCompatibilityResult(verdict, reason, diagnostics)
}
