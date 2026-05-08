// AmbientLightBrightnessController.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Window
import android.view.WindowManager

/**
 * Controls screen brightness based on ambient light sensor readings.
 */
internal class AmbientLightBrightnessController(
    context: Context,
    private val windowProvider: () -> Window?
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var currentLux: Float = DEFAULT_LUX
    private var started = false
    private var continuousAdjustment = false
    private var appliedFirstReading = false

    fun start(continuousAdjustment: Boolean = false) {
        if (started) return
        started = true
        this.continuousAdjustment = continuousAdjustment
        appliedFirstReading = false
        if (lightSensor != null) {
            sensorManager?.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        applyBrightMode(enabled = true)
    }

    fun stop() {
        if (!started) return
        started = false
        sensorManager?.unregisterListener(this)
        applyBrightMode(enabled = false)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT && event.values.isNotEmpty()) {
            currentLux = event.values[0]
            updateBrightnessForCurrentLight()
            if (!continuousAdjustment && !appliedFirstReading) {
                appliedFirstReading = true
                sensorManager?.unregisterListener(this)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for light sensor
    }

    private fun updateBrightnessForCurrentLight() {
        val window = windowProvider() ?: return
        val params = window.attributes
        if (params.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            setBrightness(window, calculateBrightnessForLux(currentLux))
        }
    }

    private fun applyBrightMode(enabled: Boolean) {
        val window = windowProvider() ?: return
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setBrightness(window, calculateBrightnessForLux(currentLux))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setBrightness(window, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
    }

    private fun setBrightness(window: Window, brightness: Float) {
        val params: WindowManager.LayoutParams = window.attributes
        params.screenBrightness = brightness
        window.attributes = params
    }

    /**
     * Maps ambient light (lux) to screen brightness.
     * Lower brightness in dark environments, higher in bright environments.
     */
    private fun calculateBrightnessForLux(lux: Float): Float {
        return when {
            lux < LUX_DARK -> MIN_BRIGHTNESS
            lux > LUX_BRIGHT -> MAX_BRIGHTNESS
            else -> {
                val ratio = (lux - LUX_DARK) / (LUX_BRIGHT - LUX_DARK)
                MIN_BRIGHTNESS + ratio * (MAX_BRIGHTNESS - MIN_BRIGHTNESS)
            }
        }
    }

    companion object {
        // Lux thresholds for brightness mapping
        private const val LUX_DARK = 50f      // Indoor dim lighting
        private const val LUX_BRIGHT = 1000f  // Bright indoor / cloudy outdoor

        // Brightness bounds
        private const val MIN_BRIGHTNESS = 0.65f
        private const val MAX_BRIGHTNESS = 0.90f
        private const val DEFAULT_LUX = 200f  // Assume normal indoor if sensor unavailable
    }
}
