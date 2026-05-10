// ScreeningControlsOverlay.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import SmartSpectra

/// Bottom-anchored controls overlay for the screening view — status banner
/// and record button.
struct ScreeningControlsOverlay: View {
    @Environment(\.smartSpectraSDK) private var sdk
    let viewModel: ScreeningViewModel

    var body: some View {
        VStack {
            Spacer()

            Text(overlayStatusText)
                .font(.subheadline)
                .foregroundStyle(.white)
                .padding()
                .background(Color.black.opacity(0.7))
                .clipShape(.rect(cornerRadius: 12))
                .padding(.horizontal, 24)
                .padding(.bottom, 8)
                .opacity(overlayStatusText.isEmpty ? 0 : 1)

            Button(action: viewModel.recordButtonTapped) {
                Text(buttonTitle)
                    .font(.title3)
                    .frame(width: 80, height: 80)
                    .foregroundStyle(.white)
                    .background(buttonBackgroundColor)
                    .clipShape(Circle())
                    .overlay {
                        Circle()
                            .stroke(.white, lineWidth: 2)
                    }
            }
            .disabled(viewModel.buttonState == .disable)
            .accessibilityLabel(buttonTitle)
            .accessibilityIdentifier(buttonTitle)
            .padding(.bottom, 24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 24)
    }

    private var overlayStatusText: String {
        if sdk.processingStatus == .error {
            return sdk.error?.message ?? (sdk.validationStatus?.hint ?? "")
        }
        return sdk.validationStatus?.hint ?? ""
    }

    private var buttonTitle: String {
        switch viewModel.buttonState {
        case .running:
            return "Stop"
        default:
            return "Record"
        }
    }

    private var buttonBackgroundColor: Color {
        viewModel.buttonState == .disable ? .gray : .brandPrimary
    }
}
