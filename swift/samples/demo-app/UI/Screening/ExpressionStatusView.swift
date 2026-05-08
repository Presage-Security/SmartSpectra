// ExpressionStatusView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import SmartSpectra

struct ExpressionStatusView: View {
    let expressionName: String?
    let confidence: Float

    private var confidenceText: String {
        guard confidence > 0 else { return "Confidence: --" }
        return "Confidence: \(Int(confidence))%"
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Image(systemName: "face.smiling")
                .foregroundStyle(.orange)
                .font(.title3.weight(.semibold))
                .frame(width: 24, height: 24, alignment: .center)
            Text("Facial Expression")
                .font(.headline)
                .lineLimit(1)
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(expressionName ?? "--")
                    .font(.headline)
                    .fontDesign(.rounded)
                    .foregroundStyle(.orange)
                    .lineLimit(1)
                Text(confidenceText)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 6)
        .padding(.horizontal, 10)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.primary.opacity(0.04))
        )
        .shadow(color: Color.black.opacity(0.035), radius: 2, x: 0, y: 1.5)
    }
}

extension ExpressionType {
    var displayName: String? {
        switch self {
        case .angry: return "Angry"
        case .contempt: return "Contempt"
        case .disgust: return "Disgust"
        case .fear: return "Fear"
        case .happy: return "Happy"
        case .neutral: return "Neutral"
        case .sad: return "Sad"
        case .surprise: return "Surprise"
        default: return nil
        }
    }
}
