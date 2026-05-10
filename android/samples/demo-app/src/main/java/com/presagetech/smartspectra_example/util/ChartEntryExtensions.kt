// ChartEntryExtensions.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.util

import com.github.mikephil.charting.data.Entry
import com.presagetech.smartspectra.proto.MetricsProto.DetectionStatus
import com.presagetech.smartspectra.proto.MetricsProto.Measurement
import com.presagetech.smartspectra.proto.MetricsProto.MeasurementWithConfidence

/**
 * Converts a list of samples into window-relative chart [Entry] objects.
 * Filters out items with non-positive timestamps, then normalises so
 * the first visible sample sits at t = 0 on the X-axis.
 */
fun <T> List<T>.toChartEntries(
    timestampOf: (T) -> Long,
    valueOf: (T) -> Float
): List<Entry> {
    val valid = filter { timestampOf(it) > 0 }
    if (valid.isEmpty()) return emptyList()
    val base = timestampOf(valid.first())
    return valid.map { Entry((timestampOf(it) - base) / 1_000_000f, valueOf(it)) }
}

/** Convenience overload for [Measurement] lists (timestamp + value). */
@JvmName("measurementToChartEntries")
fun List<Measurement>.toChartEntries() = toChartEntries({ it.timestamp }, { it.value })

/** Convenience overload for [MeasurementWithConfidence] lists; defaults to [MeasurementWithConfidence.getValue]. */
@JvmName("measurementWithConfidenceToChartEntries")
fun List<MeasurementWithConfidence>.toChartEntries(
    valueOf: (MeasurementWithConfidence) -> Float = { it.value }
) = toChartEntries({ it.timestamp }, valueOf)

/** Convenience overload for [DetectionStatus] lists (1 if detected, 0 otherwise). */
@JvmName("detectionStatusToChartEntries")
fun List<DetectionStatus>.toChartEntries() =
    toChartEntries({ it.timestamp }, { if (it.detected) 1f else 0f })
