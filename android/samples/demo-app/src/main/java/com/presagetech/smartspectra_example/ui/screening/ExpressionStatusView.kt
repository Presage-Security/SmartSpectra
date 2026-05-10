// ExpressionStatusView.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.ui.screening

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import com.presagetech.smartspectra_example.R
import java.util.Locale

/**
 * View that displays the detected facial expression with confidence.
 * Shows "last valid expression" behavior: displays "--" when no valid score.
 */
class ExpressionStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val expressionName: TextView
    private val expressionConfidence: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.expression_status, this, true)
        expressionName = findViewById(R.id.expressionName)
        expressionConfidence = findViewById(R.id.expressionConfidence)
        reset()
    }

    /**
     * Updates the expression display with the given name and confidence.
     *
     * @param name The expression name (e.g., "Happy", "Neutral"). Null shows "--".
     * @param confidence The confidence score in percent (0.0 to 100.0). Values <= 0 show "--".
     */
    fun setExpression(name: String?, confidence: Float) {
        expressionName.text = name ?: "--"

        expressionConfidence.text = if (confidence > 0) {
            String.format(Locale.ROOT, context.getString(R.string.expression_confidence_format), confidence)
        } else {
            context.getString(R.string.expression_confidence_default)
        }
    }

    /**
     * Resets the view to its default state showing "--" for both values.
     */
    fun reset() {
        expressionName.text = "--"
        expressionConfidence.text = context.getString(R.string.expression_confidence_default)
    }
}
