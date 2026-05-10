// CheckupFragment.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.checkup

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.ScatterData
import com.github.mikephil.charting.data.ScatterDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.presagetech.smartspectra.proto.MetricsProto
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra_example.SmartSpectraButton
import com.presagetech.smartspectra_example.util.toChartEntries
import com.presagetech.smartspectra_example.MainActivity
import com.presagetech.smartspectra_example.R
import com.presagetech.smartspectra_example.userFacingMessage
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Implementation of the checkup demo based on [SmartSpectraButton] with [SmartSpectraSdk].
 */
class CheckupFragment : Fragment() {
    private val viewModel: CheckupViewModel
        get() = (requireActivity() as MainActivity).checkupViewModel

    private lateinit var smartSpectraButton: SmartSpectraButton
    private lateinit var errorTextView: TextView
    private lateinit var buttonContainer: LinearLayout
    private lateinit var chartContainer: LinearLayout
    private lateinit var faceMeshContainer: ScatterChart

    private val isCustomizationEnabled = true
    private val isFaceMeshEnabled = true

    // SmartSpectra SDK settings
    // define front or back camera to use
    private var cameraPosition: Int = CameraSelector.LENS_FACING_FRONT

    // Cardio measurements toggle
    private var cardioMeasurementsEnabled: Boolean = false

    // Face metrics toggle
    private var faceMetricsEnabled: Boolean = false

    // Metrics buffers for real-time display
    private val edgeArterialPressureBuffer = mutableListOf<MetricsProto.MeasurementWithConfidence>()
    private val edgePulseRateBuffer = mutableListOf<MetricsProto.MeasurementWithConfidence>()
    private val edgeBreathingRateBuffer = mutableListOf<MetricsProto.MeasurementWithConfidence>()
    private val edgeBreathingTraceBuffer = mutableListOf<MetricsProto.Measurement>()

    private val smartSpectraSdk: SmartSpectraSdk = SmartSpectraSdk.shared.apply {
        // Optional configurations
        // select camera (front or back, defaults to front when not set)
        // config.cameraPosition = CameraPosition.FRONT
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_checkup, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.cameraPosition.collect { lensFacing ->
                    cameraPosition = lensFacing
                }
            }
        }

        smartSpectraButton = view.findViewById(R.id.smart_spectra_button)
        errorTextView = view.findViewById(R.id.error_text)
        buttonContainer = view.findViewById(R.id.button_container)
        chartContainer = view.findViewById(R.id.chart_container)
        faceMeshContainer = view.findViewById(R.id.mesh_container)

        // Optional: Observe metrics for custom display (e.g. charts, face landmarks)
        smartSpectraSdk.metrics.observe(viewLifecycleOwner) { metrics ->
            metrics?.let { handleMetrics(it) }
        }
        smartSpectraSdk.error.observe(viewLifecycleOwner) { error ->
            errorTextView.isVisible = error != null
            errorTextView.text = error?.userFacingMessage(requireContext()).orEmpty()
        }

        if (isCustomizationEnabled) {
            addControlViews()
        }
    }

    private fun addControlViews() {
        cardioMeasurementsEnabled = viewModel.cardioMeasurementsEnabled.value
        faceMetricsEnabled = viewModel.faceMetricsEnabled.value
        refreshRequestedMetrics()
        addCameraToggle()
        addCardioToggle()
        addFaceMetricsToggle()
    }

    private fun refreshRequestedMetrics() {
        smartSpectraSdk.config.requestedMetrics = buildList {
            addAll(SmartSpectraConfig.breathingMetrics)
            if (cardioMeasurementsEnabled) addAll(SmartSpectraConfig.cardioMetrics)
            if (faceMetricsEnabled) addAll(SmartSpectraConfig.faceMetrics)
        }
    }

    private fun addCardioToggle() {
        val cardioContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
        }

        val cardioSwitch = SwitchMaterial(requireContext()).apply {
            text = getString(R.string.demo_cardio_measurements)
            isChecked = cardioMeasurementsEnabled
        }

        val cardioSubtitle = TextView(requireContext()).apply {
            text = getString(R.string.demo_cardio_subtitle)
            textSize = 12f
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
            setPadding(0, 0, 0, 8)
        }

        cardioSwitch.setOnCheckedChangeListener { _, isChecked ->
            cardioMeasurementsEnabled = isChecked
            viewModel.setCardioMeasurementsEnabled(isChecked)
            refreshRequestedMetrics()
            // Clear buffers when toggling
            edgeArterialPressureBuffer.clear()
            edgePulseRateBuffer.clear()
        }

        cardioContainer.addView(cardioSwitch)
        cardioContainer.addView(cardioSubtitle)
        buttonContainer.addView(cardioContainer)
    }

    private fun addFaceMetricsToggle() {
        val faceContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
        }

        val faceSwitch = SwitchMaterial(requireContext()).apply {
            text = getString(R.string.demo_face_metrics)
            isChecked = faceMetricsEnabled
        }

        val faceSubtitle = TextView(requireContext()).apply {
            text = getString(R.string.demo_face_metrics_subtitle)
            textSize = 12f
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
            setPadding(0, 0, 0, 8)
        }

        faceSwitch.setOnCheckedChangeListener { _, isChecked ->
            faceMetricsEnabled = isChecked
            viewModel.setFaceMetricsEnabled(isChecked)
            refreshRequestedMetrics()
        }

        faceContainer.addView(faceSwitch)
        faceContainer.addView(faceSubtitle)
        buttonContainer.addView(faceContainer)
    }

    private fun addCameraToggle() {
        val cameraPositionButton = MaterialButton(
            requireContext(), null, com.google.android.material.R.attr.materialIconButtonStyle
        ).apply {
            text = if (viewModel.cameraPosition.value == CameraSelector.LENS_FACING_FRONT) {
                getString(R.string.demo_switch_camera_back)
            } else {
                getString(R.string.demo_switch_camera_front)
            }
            setIconResource(R.drawable.ic_flip_camera)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = resources.getDimensionPixelSize(R.dimen.demo_button_icon_padding)
        }
        cameraPositionButton.setOnClickListener {
            if (cameraPosition == CameraSelector.LENS_FACING_FRONT) {
                cameraPosition = CameraSelector.LENS_FACING_BACK
                cameraPositionButton.text = getString(R.string.demo_switch_camera_front)
            } else {
                cameraPosition = CameraSelector.LENS_FACING_FRONT
                cameraPositionButton.text = getString(R.string.demo_switch_camera_back)
            }
            viewModel.setCameraPosition(cameraPosition)
        }
        buttonContainer.addView(cameraPositionButton)
    }

    private fun handleMetrics(metrics: MetricsProto.Metrics) {
        // Handle face mesh
        if (isFaceMeshEnabled && metrics.hasFace() && metrics.face.landmarksCount > 0) {
            val latestLandmarks = metrics.face.landmarksList.lastOrNull()?.valueList
            if (latestLandmarks != null) {
                val meshPoints = latestLandmarks.map { landmark ->
                    landmark.x.toInt() to landmark.y.toInt()
                }
                handleMeshPoints(meshPoints)
            }
        } else {
            faceMeshContainer.isVisible = false
        }

        // Buffer breathing metrics
        if (metrics.hasBreathing()) {
            val breathing = metrics.breathing
            if (breathing.rateCount > 0) {
                val newSamples = breathing.rateList.filter { it.timestamp > 0 }
                edgeBreathingRateBuffer.addAll(newSamples)
                edgeBreathingRateBuffer.trimBuffer(200)
            }
            if (breathing.upperTraceCount > 0) {
                val newSamples = breathing.upperTraceList.filter { it.timestamp > 0 }
                edgeBreathingTraceBuffer.addAll(newSamples)
                edgeBreathingTraceBuffer.trimBuffer(400)
            }
        }

        // Buffer cardio metrics
        if (cardioMeasurementsEnabled && metrics.hasCardio()) {
            val cardio = metrics.cardio
            if (cardio.arterialPressureTraceCount > 0) {
                val newSamples = cardio.arterialPressureTraceList.filter { it.timestamp > 0 }
                edgeArterialPressureBuffer.addAll(newSamples)
                edgeArterialPressureBuffer.trimBuffer(400)
            }
            if (cardio.pulseRateCount > 0) {
                val newSamples = cardio.pulseRateList.filter { it.timestamp > 0 }
                edgePulseRateBuffer.addAll(newSamples)
                edgePulseRateBuffer.trimBuffer(200)
            }
        }

        // Render aggregate charts from buffers
        renderCharts()
    }

    private fun renderCharts() {
        chartContainer.removeAllViews()

        if (edgeBreathingTraceBuffer.isNotEmpty()) {
            addChart(edgeBreathingTraceBuffer.toChartEntries(), getString(R.string.demo_breathing_pleth), false)
        }
        if (edgeBreathingRateBuffer.isNotEmpty()) {
            addChart(edgeBreathingRateBuffer.toChartEntries(), getString(R.string.demo_breathing_rate), true)
        }
        if (cardioMeasurementsEnabled) {
            if (edgePulseRateBuffer.isNotEmpty()) {
                addChart(edgePulseRateBuffer.toChartEntries(), getString(R.string.demo_pulse_rate), true)
            }
            if (edgeArterialPressureBuffer.isNotEmpty()) {
                addChart(edgeArterialPressureBuffer.toChartEntries(), getString(R.string.demo_blood_pressure_phasic), false)
            }
        }
    }

    private fun addChart(entries: List<Entry>, title: String, showYTicks: Boolean) {
        if (entries.isEmpty()) return

        val chart = LineChart(requireContext())
        val density = resources.displayMetrics.density
        val heightInPx = (200 * density).toInt()
        chart.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, heightInPx
        )

        val titleView = TextView(requireContext()).apply {
            text = title
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

        chartContainer.addView(titleView)
        chartContainer.addView(chart)

        val dataSet = LineDataSet(entries, "Data").apply {
            setDrawValues(false)
            setDrawCircles(false)
            color = Color.RED
        }

        chart.data = LineData(dataSet)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.setDrawGridLines(false)
        chart.xAxis.setDrawAxisLine(true)
        chart.xAxis.granularity = 1.0f
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val totalSeconds = value.toInt()
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
            }
        }
        chart.axisLeft.apply {
            setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
            setDrawZeroLine(false)
            setDrawGridLines(false)
            setDrawAxisLine(true)
            setDrawLabels(showYTicks)
        }
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        chart.description.isEnabled = false
        chart.onTouchListener = null
        chart.invalidate()
    }

    private fun handleMeshPoints(meshPoints: List<Pair<Int, Int>>) {
        if (meshPoints.isEmpty()) {
            faceMeshContainer.isVisible = false
            return
        }

        faceMeshContainer.isVisible = true

        // Sorting avoids the negative array size exception in MPAndroidChart scatter plots.
        val scaledPoints =
            meshPoints.map { Entry(1f - it.first / 720f, 1f - it.second / 720f) }.sortedBy { it.x }

        val dataSet = ScatterDataSet(scaledPoints, getString(R.string.demo_mesh_points)).apply {
            setDrawValues(false)
            scatterShapeSize = 15f
            setScatterShape(ScatterChart.ScatterShape.CIRCLE)
        }

        val scatterData = ScatterData(dataSet)

        faceMeshContainer.apply {
            data = scatterData
            axisLeft.isEnabled = false
            axisRight.isEnabled = false
            xAxis.isEnabled = false
            setTouchEnabled(false)
            description.isEnabled = false
            legend.isEnabled = false
            setVisibleXRange(0f, 1f)
            setVisibleYRange(0f, 1f, YAxis.AxisDependency.LEFT)
            moveViewTo(0f, 0f, YAxis.AxisDependency.LEFT)
            invalidate()
        }
    }

    private fun <T> MutableList<T>.trimBuffer(maxSize: Int) {
        if (size > maxSize) {
            val excess = size - maxSize
            subList(0, excess).clear()
        }
    }
}
