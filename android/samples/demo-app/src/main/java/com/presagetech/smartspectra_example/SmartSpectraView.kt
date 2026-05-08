// SmartSpectraView.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout

/**
 * Composite view containing the measurement button and a simple result view.
 * Drop into a layout for a quick integration.
 *
 * SDK errors (unsupported ABI, missing permission, auth failure, etc.)
 * surface on `SmartSpectraSdk.error`, which [SmartSpectraResultView] renders
 * inline — no defensive UI gating needed here.
 */
class SmartSpectraView(
    context: Context,
    attrs: AttributeSet?,
) : LinearLayout(context, attrs) {

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_smart_spectra, this, true)
    }
}
