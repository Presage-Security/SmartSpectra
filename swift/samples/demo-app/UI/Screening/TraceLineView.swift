// TraceLineView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import SmartSpectra

/// Protocol that unifies the timestamp + value fields on the different
/// protobuf measurement types used by the vitals pipeline.
protocol MeasurementProtocol {
    var timestamp: Int64 { get }
    var value: Float { get }
}

extension SmartSpectra.Measurement: MeasurementProtocol {}
extension MeasurementWithConfidence: MeasurementProtocol {}

/// Draws a simple line chart of the most recent `recentCount` points from the
/// given measurements.
struct TraceLineView<T: MeasurementProtocol>: View {
    let data: [T]
    let color: Color
    let recentCount: Int

    var body: some View {
        GeometryReader { geometry in
            let displayedData = Array(data.suffix(recentCount))
            let width = max(geometry.size.width, 1)
            let height = max(geometry.size.height, 1)

            Path { path in
                guard displayedData.count > 1 else { return }

                let timestamps = displayedData.map { $0.timestamp }
                let values = displayedData.map { $0.value }

                guard
                    let minTimestamp = timestamps.min(),
                    let maxTimestamp = timestamps.max(),
                    let minValue = values.min(),
                    let maxValue = values.max()
                else {
                    return
                }

                let timestampRange = maxTimestamp - minTimestamp
                let valueRange = maxValue - minValue

                for (index, measurement) in displayedData.enumerated() {
                    let x: CGFloat
                    if timestampRange > 0 {
                        x = CGFloat(Double(measurement.timestamp - minTimestamp) / Double(timestampRange)) * width
                    } else if displayedData.count > 1 {
                        x = CGFloat(index) / CGFloat(displayedData.count - 1) * width
                    } else {
                        x = width / 2
                    }

                    let y: CGFloat
                    if valueRange > 0 {
                        y = height - CGFloat((measurement.value - minValue) / valueRange) * height
                    } else {
                        y = height / 2
                    }

                    if index == 0 {
                        path.move(to: CGPoint(x: x, y: y))
                    } else {
                        path.addLine(to: CGPoint(x: x, y: y))
                    }
                }
            }
            .stroke(color, lineWidth: 2)
        }
    }
}
