// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
package com.presagetech.smartspectra_minimal

import com.presagetech.smartspectra.readiness.DeviceReadinessVerdict
import com.presagetech.smartspectra.readiness.DeviceReadinessResult
/** Show the verdict and actionable explanation. */
object ReadinessPresentation {
    fun canProceed(result: DeviceReadinessResult) = result.verdict == DeviceReadinessVerdict.READY
    fun title(result: DeviceReadinessResult): String = when (result.verdict) {
        DeviceReadinessVerdict.READY -> "The device meets the configured SDK readiness requirements."
        DeviceReadinessVerdict.LIMITED -> "The configured SDK did not meet the performance requirements."
        DeviceReadinessVerdict.BLOCKED -> "A prerequisite prevents assessing readiness."
        DeviceReadinessVerdict.INCONCLUSIVE -> "The check could not establish device readiness."
    }
    fun action(result: DeviceReadinessResult) = result.reason
}
