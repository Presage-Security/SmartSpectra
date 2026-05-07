// FaceMetricsStatusView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import SmartSpectra

/// Small pill overlay showing blinking + talking indicators during screening.
struct FaceMetricsStatusView: View {
    @Environment(\.smartSpectraSDK) private var sdk
    @State private var currentBlinkingStatus: Bool = false
    @State private var currentTalkingStatus: Bool = false

    var body: some View {
        HStack(spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: currentBlinkingStatus ? "eye.slash.fill" : "eye.fill")
                    .foregroundStyle(currentBlinkingStatus ? .orange : .green)
                    .font(.caption)
                Text(currentBlinkingStatus ? "Blinking" : "Eyes Open")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(currentBlinkingStatus ? .orange : .green)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(currentBlinkingStatus ? Color.orange.opacity(0.2) : Color.green.opacity(0.2))
            )
            .accessibilityElement(children: .combine)

            HStack(spacing: 6) {
                Image(systemName: currentTalkingStatus ? "mic.fill" : "mic.slash.fill")
                    .foregroundStyle(currentTalkingStatus ? .orange : .green)
                    .font(.caption)
                Text(currentTalkingStatus ? "Talking" : "Silent")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(currentTalkingStatus ? .orange : .green)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(currentTalkingStatus ? Color.orange.opacity(0.2) : Color.green.opacity(0.2))
            )
            .accessibilityElement(children: .combine)
        }
        .onChange(of: sdk.metrics) { _, metrics in
            guard let metrics, metrics.hasFace else { return }
            let face = metrics.face

            if let lastBlinking = face.blinking.last {
                currentBlinkingStatus = lastBlinking.detected
            }

            if let lastTalking = face.talking.last {
                currentTalkingStatus = lastTalking.detected
            }
        }
    }
}
