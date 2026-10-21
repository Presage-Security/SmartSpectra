// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
package com.presagetech.smartspectra_minimal
import com.presagetech.smartspectra.compatibility.*
/** Show the actionable result and retain the native JSON for optional support export. */
object CompatibilityPresentation {
    fun canProceed(result: DeviceCompatibilityResult) = result.verdict == DeviceCompatibilityVerdict.READY
    fun title(result: DeviceCompatibilityResult): String = when (result.verdict) {
        DeviceCompatibilityVerdict.READY -> "The configured SDK meets the compatibility requirements."
        DeviceCompatibilityVerdict.LIMITED -> "The configured SDK did not meet the performance requirements."
        DeviceCompatibilityVerdict.BLOCKED -> "A prerequisite prevents assessing compatibility."
        DeviceCompatibilityVerdict.INCONCLUSIVE -> "The check could not establish device compatibility."
    }
    fun action(result: DeviceCompatibilityResult) = result.reason
    fun supportReport(result: DeviceCompatibilityResult) = result.diagnostics
}
