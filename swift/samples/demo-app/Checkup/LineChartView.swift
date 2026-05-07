// LineChartView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import Charts

struct LineChartView: View {
    let orderedPairs: [(time: Date, value: Float)]
    let title: String
    let yLabel: String
    let showYTicks: Bool

    var body: some View {
        VStack {
            Text(title)
                .font(.headline)
                .padding(.top)
                .foregroundStyle(.gray)

            Chart {
                ForEach(orderedPairs, id: \.time) { pair in
                    LineMark(
                        x: .value("Time", pair.time),
                        y: .value("Value", pair.value)
                    )
                    .foregroundStyle(.red)
                }
            }
            .chartXAxis {
                AxisMarks(position: .bottom) { _ in
                    AxisGridLine()
                    AxisTick()
                    AxisValueLabel(format: .dateTime.minute().second())
                }
            }
            .chartYAxis {
                if showYTicks {
                    AxisMarks(position: .leading) { value in
                        AxisGridLine()
                        AxisTick()
                        AxisValueLabel()
                    }
                } else {
                    AxisMarks(position: .leading) { _ in
                        AxisGridLine().foregroundStyle(.clear)
                        AxisTick().foregroundStyle(.clear)
                        AxisValueLabel().foregroundStyle(.clear)
                    }
                }
            }
            .frame(height: 200)
            .padding()
        }
    }
}

#Preview {
    let base = Date(timeIntervalSince1970: 1_700_000_000)
    LineChartView(
        orderedPairs: [
            (time: base, value: 0.0),
            (time: base.addingTimeInterval(1), value: 2.0),
            (time: base.addingTimeInterval(2), value: 1.5),
            (time: base.addingTimeInterval(3), value: 3.0)
        ],
        title: "Dummy Chart",
        yLabel: "Value",
        showYTicks: true
    )
}
