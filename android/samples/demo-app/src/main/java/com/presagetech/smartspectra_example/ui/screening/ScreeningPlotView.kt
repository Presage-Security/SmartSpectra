// ScreeningPlotView.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.ui.screening

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.presagetech.smartspectra.proto.MetricsProto.Measurement
import com.presagetech.smartspectra.proto.MetricsProto.MeasurementWithConfidence
import com.presagetech.smartspectra.proto.MetricsProto.Metrics
import com.presagetech.smartspectra_example.R
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra_example.cardioMeasurementsEnabled
import com.presagetech.smartspectra_example.facialExpressionEnabled
import com.presagetech.smartspectra_example.util.displayName
import com.presagetech.smartspectra_example.util.toChartEntries
import java.util.Locale
import kotlin.math.roundToInt

/**
 * View that renders pulse, breathing, and blood pressure plots during continuous measurements.
 * Use [bindLifecycleOwner] to start observing metrics.
 */
class ScreeningPlotView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val breathingRateTitle: TextView
    private val breathingRateValue: TextView
    private val bloodPressureTitle: TextView
    private val pulseRateValue: TextView
    private val breathingPlot: LineChart
    private val bloodPressurePlot: LineChart
    private val faceMetricsStatusView: FaceMetricsStatusView
    private val expressionStatusView: ExpressionStatusView

    private val breathingTraces = mutableListOf<Measurement>()
    private val arterialPressureTrace = mutableListOf<MeasurementWithConfidence>()

    // Coalesce chart redraws to one per animation frame. Metrics arrive at ~30 Hz
    // and each MPAndroidChart invalidate is expensive; without coalescing the UI
    // thread can't keep up alongside the camera preview ImageView updates.
    private var chartsDirty = false
    private val chartRedrawRunnable = Runnable {
        chartsDirty = false
        renderCharts()
    }

    private lateinit var breathingDataSet: LineDataSet
    private lateinit var bloodPressureDataSet: LineDataSet

    private var breathingRate: Int = 0
    private var cardioMeasurementsEnabledOverride: Boolean? = null
    private var facialExpressionEnabledOverride: Boolean? = null

    private val sdk: SmartSpectraSdk by lazy {
        SmartSpectraSdk.shared
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.screening_plot, this, true)
        orientation = VERTICAL

        breathingRateTitle = findViewById(R.id.breathingRateTitle)
        breathingRateValue = findViewById(R.id.breathingRateValue)
        bloodPressureTitle = findViewById(R.id.bloodPressureTitle)
        pulseRateValue = findViewById(R.id.pulseRateValue)
        breathingPlot = findViewById(R.id.breathingPlot)
        bloodPressurePlot = findViewById(R.id.bloodPressurePlot)
        faceMetricsStatusView = findViewById(R.id.faceMetricsStatus)
        expressionStatusView = findViewById(R.id.expressionStatus)

        breathingDataSet = createDataSet(Color.BLUE)
        bloodPressureDataSet = createDataSet(ContextCompat.getColor(context, R.color.purple))

        setupChart(breathingPlot)
        setupChart(bloodPressurePlot)

        breathingPlot.data = LineData(breathingDataSet)
        bloodPressurePlot.data = LineData(bloodPressureDataSet)
    }

    fun setCardioMeasurementsEnabled(enabled: Boolean) {
        cardioMeasurementsEnabledOverride = enabled
        updateCardioVisibility()
        if (!enabled) {
            updateChart(bloodPressurePlot, bloodPressureDataSet, emptyList())
        }
    }

    fun setFacialExpressionEnabled(enabled: Boolean) {
        facialExpressionEnabledOverride = enabled
        updateExpressionVisibility()
        updateFaceMetricsVisibility()
    }
    /**
     * Starts observing metrics from [SmartSpectraSdk] using the provided lifecycle.
     */
    fun bindLifecycleOwner(lifecycleOwner: LifecycleOwner) {
        // Clear displayed vitals when the lifecycle stops (e.g. navigation away, session end)
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                clearDisplayedVitals()
            }
        })

        // Observe metrics LiveData for breathing, cardio, expression, and face metrics data
        sdk.metrics.observe(lifecycleOwner) { metrics ->
            updateCardioVisibility()
            updateExpressionVisibility()
            updateFaceMetricsVisibility()

            if (metrics == null) return@observe

            // Update breathing rate and trace
            if (metrics.hasBreathing()) {
                val breathing = metrics.breathing
                if (breathing.rateCount > 0) {
                    breathingRate = breathing.rateList.last().value.roundToInt()
                    updateBreathingRateDisplay()
                }

                // Update breathing trace buffer; the actual chart redraw is
                // coalesced to one per animation frame in scheduleChartRedraw.
                if (breathing.upperTraceCount > 0) {
                    val newSamples = breathing.upperTraceList
                        .filter { it.timestamp > 0 }
                    mergeData(breathingTraces, newSamples, maxSize = 400) { it.timestamp }
                    scheduleChartRedraw()
                }
            }

            // Update cardio traces and pulse rate when enabled
            if (cardioMeasurementsEnabled) {
                updateCardioTraces(metrics)
            }

            // Update face metrics (blinking/talking) when enabled
            if (facialExpressionEnabled) {
                updateFaceMetricsFromMetrics(metrics)
            }

            // Update expression when enabled
            // "Last valid expression" behavior: only update when valid data present
            // (skip update if no expression data to prevent flicker)
            if (facialExpressionEnabled) {
                updateExpressionFromMetrics(metrics)
            }
        }
    }

    /**
     * Updates the breathing rate display.
     */
    private fun updateBreathingRateDisplay() {
        val rateText = if (breathingRate > 0) "$breathingRate bpm" else "-- bpm"
        breathingRateValue.text = rateText
    }

    /**
     * Updates the visibility of the cardio section based on cardio enabled state.
     */
    private fun updateCardioVisibility() {
        val visibility = if (cardioMeasurementsEnabled) View.VISIBLE else View.GONE
        bloodPressureTitle.visibility = visibility
        pulseRateValue.visibility = visibility
        bloodPressurePlot.visibility = visibility

        if (!cardioMeasurementsEnabled) {
            arterialPressureTrace.clear()
            pulseRateValue.text = "-- bpm"
        }
    }

    /**
     * Updates the visibility of the expression section based on face metrics enabled state.
     */
    private fun updateExpressionVisibility() {
        val visibility = if (facialExpressionEnabled) View.VISIBLE else View.GONE
        expressionStatusView.visibility = visibility

        if (!facialExpressionEnabled) {
            expressionStatusView.reset()
        }
    }

    /**
     * Updates expression display from the latest metrics.
     * Implements "last valid expression" behavior: only updates when valid expression data
     * is present. Skips update (retains previous display) when packet has no expression data,
     * preventing UI flicker from inconsistent aggregator packets.
     */
    private fun updateExpressionFromMetrics(metrics: Metrics) {
        if (!metrics.hasFace()) return

        val face = metrics.face
        if (face.expressionCount == 0) return

        // Get the latest expression entry
        val latestExpression = face.expressionList.lastOrNull() ?: return
        if (latestExpression.scoresCount == 0) return

        // Find the highest-confidence expression score (confidence must be > 0, matching Swift)
        val topScore = latestExpression.scoresList
            .filter { it.confidence > 0 }
            .maxByOrNull { it.confidence } ?: return

        // Only update if we have a valid expression type with a displayable name
        val displayName = topScore.type.displayName ?: return

        expressionStatusView.setExpression(displayName, topScore.confidence)
    }

    /**
     * Updates cardio traces from the latest metrics.
     */
    private fun updateCardioTraces(metrics: Metrics) {
        if (!metrics.hasCardio()) return

        val cardio = metrics.cardio

        if (cardio.arterialPressureTraceCount > 0) {
            val newSamples = cardio.arterialPressureTraceList
                .filter { it.timestamp > 0 }
            mergeData(arterialPressureTrace, newSamples, maxSize = 400) { it.timestamp }
            scheduleChartRedraw()
        }

        val latestPulseRate = cardio.pulseRateList.lastOrNull()?.value
        if (latestPulseRate != null) {
            pulseRateValue.text = String.format(Locale.ROOT, "%.0f bpm", latestPulseRate)
        }
    }

    private fun clearDisplayedVitals() {
        removeCallbacks(chartRedrawRunnable)
        chartsDirty = false
        breathingTraces.clear()
        arterialPressureTrace.clear()
        breathingRate = 0
        updateBreathingRateDisplay()
        pulseRateValue.text = "-- bpm"
        updateChart(breathingPlot, breathingDataSet, emptyList())
        updateChart(bloodPressurePlot, bloodPressureDataSet, emptyList())
        faceMetricsStatusView.reset()
        expressionStatusView.reset()
    }

    /**
     * Marks the buffers dirty and schedules a single redraw on the next UI loop.
     * Multiple calls within the same frame coalesce into one [renderCharts] pass.
     */
    private fun scheduleChartRedraw() {
        if (chartsDirty) return
        chartsDirty = true
        post(chartRedrawRunnable)
    }

    /** Pushes the latest buffer state to the chart views. */
    private fun renderCharts() {
        updateChart(breathingPlot, breathingDataSet, breathingTraces.toChartEntries())
        if (cardioMeasurementsEnabled) {
            updateChart(
                bloodPressurePlot,
                bloodPressureDataSet,
                arterialPressureTrace.toChartEntries(),
            )
        }
    }

    /** Configures default styling for each chart. */
    private fun setupChart(chart: LineChart) {
        chart.description.text = ""
        chart.setNoDataText("")
        chart.setTouchEnabled(false)
        chart.isDragEnabled = false
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        // Remove grid lines and axis markers
        chart.xAxis.isEnabled = false
        chart.axisLeft.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        chart.setDrawGridBackground(false)
    }

    private fun createDataSet(colorValue: Int): LineDataSet {
        return LineDataSet(emptyList(), "").apply {
            lineWidth = 2f
            color = colorValue
            setDrawCircles(false)
            setDrawValues(false)
        }
    }

    /**
     * Appends [newSamples] to [existing], replacing any overlap and capping at [maxSize].
     */
    private fun <T> mergeData(
        existing: MutableList<T>,
        newSamples: List<T>,
        maxSize: Int,
        timestampOf: (T) -> Long
    ) {
        val firstNewTs = newSamples.firstOrNull()?.let(timestampOf) ?: return
        val firstOverlapIndex = existing.indexOfFirst { timestampOf(it) >= firstNewTs }
        if (firstOverlapIndex != -1) {
            existing.subList(firstOverlapIndex, existing.size).clear()
        }
        existing.addAll(newSamples)
        if (existing.size > maxSize) {
            existing.subList(0, existing.size - maxSize).clear()
        }
    }

    /** Applies pre-converted chart [entries] to [chart]. */
    private fun updateChart(
        chart: LineChart,
        dataSet: LineDataSet,
        entries: List<Entry>
    ) {
        dataSet.values = entries
        dataSet.notifyDataSetChanged()
        chart.data?.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    /**
     * Updates the visibility of the face metrics status view based on face metrics enabled state.
     */
    private fun updateFaceMetricsVisibility() {
        val visibility = if (facialExpressionEnabled) View.VISIBLE else View.GONE
        faceMetricsStatusView.visibility = visibility

        if (!facialExpressionEnabled) {
            faceMetricsStatusView.reset()
        }
    }

    /**
     * Updates the face metrics status (blinking/talking) from the latest metrics.
     */
    private fun updateFaceMetricsFromMetrics(metrics: Metrics) {
        if (!metrics.hasFace()) return

        val face = metrics.face

        // Update blinking status if available
        if (face.blinkingCount > 0) {
            val lastBlinking = face.blinkingList.last()
            faceMetricsStatusView.setBlinkingStatus(lastBlinking.detected)
        }

        // Update talking status if available
        if (face.talkingCount > 0) {
            val lastTalking = face.talkingList.last()
            faceMetricsStatusView.setTalkingStatus(lastTalking.detected)
        }
    }

    private val cardioMeasurementsEnabled: Boolean
        get() = cardioMeasurementsEnabledOverride ?: sdk.cardioMeasurementsEnabled

    private val facialExpressionEnabled: Boolean
        get() = facialExpressionEnabledOverride ?: sdk.facialExpressionEnabled
}
