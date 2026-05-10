// SignalGraphView.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_minimal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.min

class SignalGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private companion object {
        const val GRID_ALPHA = 12
        const val GLOW_ALPHA = 68
        const val LINE_ALPHA = 170
        const val FILL_ALPHA = 52
    }

    private val samples = ArrayDeque<Float>()
    private val maxPoints = 200
    private val inset = 10f
    private var graphColor = ContextCompat.getColor(context, R.color.sample_coral)

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(GRID_ALPHA, 255, 255, 255)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        context.obtainStyledAttributes(attrs, R.styleable.SignalGraphView).use { typedArray ->
            graphColor = typedArray.getColor(
                R.styleable.SignalGraphView_graphColor,
                graphColor,
            )
        }
        syncPaintColors()
    }

    fun appendValues(values: List<Float>) {
        values.forEach { value ->
            samples.addLast(value)
            while (samples.size > maxPoints) {
                samples.removeFirst()
            }
        }
        invalidate()
    }

    fun reset() {
        samples.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val list = samples.toList()
        if (list.size < 2) return

        val drawLeft = inset
        val drawTop = inset
        val drawRight = width - inset
        val drawBottom = height - inset
        val drawWidth = drawRight - drawLeft
        val drawHeight = drawBottom - drawTop

        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            val y = drawTop + drawHeight * (1f - fraction)
            canvas.drawLine(drawLeft, y, drawRight, y, gridPaint)
        }

        var minValue = Float.POSITIVE_INFINITY
        var maxValue = Float.NEGATIVE_INFINITY
        list.forEach {
            minValue = min(minValue, it)
            maxValue = max(maxValue, it)
        }
        val range = if (maxValue - minValue == 0f) 1f else maxValue - minValue

        val linePath = Path()
        list.forEachIndexed { index, value ->
            val x = drawLeft + drawWidth * index / (list.size - 1)
            val normalized = (value - minValue) / range
            val y = drawBottom - normalized * drawHeight
            if (index == 0) {
                linePath.moveTo(x, y)
            } else {
                linePath.lineTo(x, y)
            }
        }

        val fillPath = Path(linePath).apply {
            lineTo(drawRight, drawBottom)
            lineTo(drawLeft, drawBottom)
            close()
        }
        fillPaint.shader = LinearGradient(
            0f,
            drawTop,
            0f,
            drawBottom,
            Color.argb(FILL_ALPHA, Color.red(graphColor), Color.green(graphColor), Color.blue(graphColor)),
            Color.argb(0, Color.red(graphColor), Color.green(graphColor), Color.blue(graphColor)),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, glowPaint)
        canvas.drawPath(linePath, linePaint)
    }

    private fun syncPaintColors() {
        linePaint.color = Color.argb(
            LINE_ALPHA,
            Color.red(graphColor),
            Color.green(graphColor),
            Color.blue(graphColor),
        )
        glowPaint.color = Color.argb(
            GLOW_ALPHA,
            Color.red(graphColor),
            Color.green(graphColor),
            Color.blue(graphColor),
        )
    }
}
