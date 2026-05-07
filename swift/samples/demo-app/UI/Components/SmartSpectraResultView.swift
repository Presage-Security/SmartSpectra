// SmartSpectraResultView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import SmartSpectra

/// Displays breathing rate (and pulse rate when cardio is enabled) after a measurement completes.
struct SmartSpectraResultView: View {
    @Environment(\.smartSpectraSDK) private var sdk

    private var resultText: String {
        guard let metrics = sdk.metrics else {
            return "No Results\n..."
        }

        let breathingRateInt: Int = {
            guard metrics.hasBreathing, let last = metrics.breathing.rate.last else { return 0 }
            return Int(last.value.rounded())
        }()
        let breathingRateText = "Breathing Rate: \(breathingRateInt == 0 ? "N/A" : "\(breathingRateInt) BPM")"

        if sdk.cardioMeasurementsEnabled {
            let pulseRateInt: Int = {
                guard metrics.hasCardio, let last = metrics.cardio.pulseRate.last else { return 0 }
                return Int(last.value.rounded())
            }()
            let pulseRateText = "Pulse Rate: \(pulseRateInt == 0 ? "N/A" : "\(pulseRateInt) BPM")"
            return "\(breathingRateText)\n\(pulseRateText)"
        } else {
            return breathingRateText
        }
    }

    var body: some View {
        VStack {
            HStack {
                Spacer()
                Text(resultText)
                    .foregroundStyle(.secondary)
                    .font(.title2.bold())
                    .multilineTextAlignment(.center)
                Spacer()
            }
            if let errorMessage = sdk.error?.message {
                Text("Error: \(errorMessage)")
                    .font(.headline)
                    .foregroundStyle(.red)
                    .padding(.vertical, 10)
            }
        }
        .padding(.vertical, 10)
        .background(.thickMaterial)
        .clipShape(.rect(cornerRadius: 10))
        .overlay {
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.brandPrimary, lineWidth: 3)
        }
    }
}

#Preview {
    SmartSpectraResultView()
}
