// SmartSpectraInfoButton.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI

/// Circular info button that sits flush with the right edge of the checkup
/// pill in ``SmartSpectraButtonView``.
struct SmartSpectraInfoButton: View {
    var action: () -> Void

    private let cornerRadii = RectangleCornerRadii(
        topLeading: 0,
        bottomLeading: 0,
        bottomTrailing: 28,
        topTrailing: 28
    )

    var body: some View {
        Button(action: action) {
            ZStack {
                UnevenRoundedRectangle(cornerRadii: cornerRadii, style: .continuous)
                    .fill(Color.white)
                UnevenRoundedRectangle(cornerRadii: cornerRadii, style: .continuous)
                    .stroke(Color.brandPrimary, lineWidth: 4)
                Image(systemName: "info.circle.fill")
                    .foregroundStyle(Color.brandPrimary)
                    .font(.title3)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(UnevenRoundedRectangle(cornerRadii: cornerRadii, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Info")
    }
}
