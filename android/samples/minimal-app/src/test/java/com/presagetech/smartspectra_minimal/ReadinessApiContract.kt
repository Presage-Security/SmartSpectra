// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
package com.presagetech.smartspectra_minimal
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.readiness.DeviceReadinessResult
import com.presagetech.smartspectra.readiness.DeviceReadinessVerdict
// Compiles against the minified published release SDK.
internal suspend fun readinessApiContract(sdk: SmartSpectraSdk): DeviceReadinessResult {
    val result = sdk.checkDeviceReadiness()
    val verdict: DeviceReadinessVerdict = result.verdict
    val reason: String = result.reason
    return DeviceReadinessResult(verdict, reason)
}
