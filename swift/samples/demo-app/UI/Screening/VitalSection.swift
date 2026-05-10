// VitalSection.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI

/// Standardized layout for each vital displayed in `ContinuousVitalsPlotView` —
/// icon, title, optional numeric value on the right, and a chart card below.
struct VitalSection<Content: View>: View {
    let title: String
    let valueText: String?
    let icon: String
    let color: Color
    @ViewBuilder let chart: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Image(systemName: icon)
                    .foregroundStyle(color)
                    .font(.title3.weight(.semibold))
                    .frame(width: 24, height: 24, alignment: .center)
                Text(title)
                    .font(.headline)
                    .lineLimit(1)
                Spacer()
                if let valueText {
                    Text(valueText)
                        .font(.title2.weight(.semibold))
                        .fontDesign(.rounded)
                        .foregroundStyle(color)
                        .monospacedDigit()
                        .lineLimit(1)
                }
            }

            chart
                .frame(height: 64)
                .frame(maxWidth: .infinity)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(color.opacity(0.14))
                )
                .overlay {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(color.opacity(0.28), lineWidth: 1)
                }
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .padding(.horizontal, -6)
        }
        .padding(.vertical, 4)
        .padding(.horizontal, 6)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.primary.opacity(0.04))
        )
        .shadow(color: Color.black.opacity(0.035), radius: 2, x: 0, y: 1.5)
        .frame(maxWidth: .infinity)
    }
}
