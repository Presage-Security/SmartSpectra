// ScreeningPlotView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import AVFoundation
import SmartSpectra

struct ScreeningPlotView: View {
    @Environment(\.smartSpectraSDK) private var sdk
    @State private var lastExpression: (name: String, confidence: Float)?

    /// Extracts the top expression from metrics if available
    private func extractTopExpression(from metrics: Metrics?) -> (name: String, confidence: Float)? {
        guard let expressions = metrics?.face.expression,
              let latestExpression = expressions.last,
              let topScore = latestExpression.scores.max(by: { $0.confidence < $1.confidence }),
              topScore.confidence > 0,
              let displayName = topScore.type.displayName else {
            return nil
        }
        return (displayName, topScore.confidence)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ContinuousVitalsPlotView()
                .padding(.top, 4)

            if sdk.facialExpressionEnabled {
                ExpressionStatusView(
                    expressionName: lastExpression?.name,
                    confidence: lastExpression?.confidence ?? 0
                )
                .padding(.horizontal, 4)
            }

            Spacer()
        }
        .padding(.horizontal, 4)
        .padding(.top, 8)
        .padding(.bottom, 4)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .onChange(of: sdk.metrics) { _, newMetrics in
            if let newExpression = extractTopExpression(from: newMetrics) {
                lastExpression = newExpression
            }
        }
        .onAppear {
            lastExpression = nil
        }
    }
}
