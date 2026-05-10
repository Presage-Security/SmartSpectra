// FaceMetricsStatusView.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.ui.screening

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.presagetech.smartspectra_example.R
import androidx.core.graphics.toColorInt

/**
 * View that displays real-time blinking and talking detection status.
 * Shows visual indicators with color-coded backgrounds.
 */
class FaceMetricsStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val blinkingContainer: LinearLayout
    private val blinkingIcon: TextView
    private val blinkingStatus: TextView

    private val talkingContainer: LinearLayout
    private val talkingIcon: TextView
    private val talkingStatus: TextView

    private val activeTextColor = "#FF8C00".toColorInt()
    private val inactiveTextColor = "#2E7D32".toColorInt()

    private val blinkingActiveBackground: GradientDrawable
    private val blinkingInactiveBackground: GradientDrawable
    private val talkingActiveBackground: GradientDrawable
    private val talkingInactiveBackground: GradientDrawable

    init {
        LayoutInflater.from(context).inflate(R.layout.face_metrics_status, this, true)

        blinkingContainer = findViewById(R.id.blinkingContainer)
        blinkingIcon = findViewById(R.id.blinkingIcon)
        blinkingStatus = findViewById(R.id.blinkingStatus)

        talkingContainer = findViewById(R.id.talkingContainer)
        talkingIcon = findViewById(R.id.talkingIcon)
        talkingStatus = findViewById(R.id.talkingStatus)

        blinkingActiveBackground = createRoundedBackground("#FFA500", 0.15f)
        blinkingInactiveBackground = createRoundedBackground("#4CAF50", 0.15f)
        talkingActiveBackground = createRoundedBackground("#FFA500", 0.15f)
        talkingInactiveBackground = createRoundedBackground("#4CAF50", 0.15f)

        reset()
    }

    /**
     * Updates the blinking status display.
     *
     * @param detected Whether blinking is currently detected
     */
    fun setBlinkingStatus(detected: Boolean) {
        if (detected) {
            blinkingIcon.text = "👁️"
            blinkingStatus.text = "Blinking"
            blinkingStatus.setTextColor(activeTextColor)
            blinkingContainer.background = blinkingActiveBackground
        } else {
            blinkingIcon.text = "👁️"
            blinkingStatus.text = "Eyes Open"
            blinkingStatus.setTextColor(inactiveTextColor)
            blinkingContainer.background = blinkingInactiveBackground
        }
    }

    /**
     * Updates the talking status display.
     *
     * @param detected Whether talking is currently detected
     */
    fun setTalkingStatus(detected: Boolean) {
        if (detected) {
            talkingIcon.text = "🎤"
            talkingStatus.text = "Talking"
            talkingStatus.setTextColor(activeTextColor)
            talkingContainer.background = talkingActiveBackground
        } else {
            talkingIcon.text = "🔇"
            talkingStatus.text = "Silent"
            talkingStatus.setTextColor(inactiveTextColor)
            talkingContainer.background = talkingInactiveBackground
        }
    }

    /**
     * Creates a rounded rectangle drawable with the specified color and opacity.
     *
     * @param colorHex The color in hex format (e.g., "#FF0000")
     * @param alpha The opacity (0.0 to 1.0)
     * @return A GradientDrawable with rounded corners
     */
    private fun createRoundedBackground(colorHex: String, alpha: Float): GradientDrawable {
        val color = colorHex.toColorInt()
        val alphaInt = (alpha * 255).toInt()
        val colorWithAlpha = Color.argb(alphaInt, Color.red(color), Color.green(color), Color.blue(color))

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * context.resources.displayMetrics.density // 8dp rounded corners
            setColor(colorWithAlpha)
        }
    }

    /**
     * Resets the view to its default state.
     */
    fun reset() {
        setBlinkingStatus(false)
        setTalkingStatus(false)
    }
}
