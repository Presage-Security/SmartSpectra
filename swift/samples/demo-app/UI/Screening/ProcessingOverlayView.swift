// ProcessingOverlayView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import SmartSpectra

struct ProcessingOverlayView: View {
    let status: ProcessingStatus
    var onDismiss: (() -> Void)?
    var onOpenSettings: (() -> Void)? = nil
    var messageOverride: String? = nil

    var body: some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
            VStack(spacing: 24) {
                ProcessingIndicator(status: status)
                Text(message)
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.primary)
                if status == .starting || status == .stopping {
                    ProgressView()
                        .progressViewStyle(.linear)
                        .tint(.brandPrimary)
                        .frame(maxWidth: 220)
                }
                if status == .error {
                    if let onOpenSettings {
                        Button("Open Settings", action: onOpenSettings)
                            .buttonStyle(.bordered)
                            .tint(.brandPrimary)
                    }

                    if let onDismiss {
                        Button("OK", action: onDismiss)
                            .buttonStyle(.borderedProminent)
                            .tint(.brandPrimary)
                    }
                }
            }
            .padding(24)
            .frame(maxWidth: 320)
            .background(.thickMaterial)
            .clipShape(.rect(cornerRadius: 20))
            .shadow(radius: 20)
        }
    }

    private var message: String {
        if status == .error, let messageOverride, !messageOverride.isEmpty {
            return messageOverride
        }

        switch status {
        case .starting:
            return "Initializing..."
        case .running:
            return "Measurement is in progress."
        case .stopping:
            return "Stopping session..."
        case .error:
            return "Analyzing data encountered an error. Contact support if it happens repeatedly."
        case .idle:
            return ""
        }
    }
}

private struct ProcessingIndicator: View {
    let status: ProcessingStatus
    @State private var animate = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var symbol: String {
        switch status {
        case .starting:
            return "arrow.triangle.2.circlepath"
        case .running:
            return "arrow.triangle.2.circlepath"
        case .stopping:
            return "checkmark.circle.fill"
        case .error:
            return "xmark.circle.fill"
        case .idle:
            return "hourglass"
        }
    }

    private var color: Color {
        switch status {
        case .error:
            return .red
        default:
            return .brandPrimary
        }
    }

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 64, weight: .bold))
            .foregroundStyle(color)
            .scaleEffect(reduceMotion ? 1.0 : (animate ? 1.1 : 0.9))
            .opacity(reduceMotion ? 1.0 : (animate ? 1 : 0.7))
            .onAppear {
                guard !reduceMotion else { return }
                withAnimation(.easeInOut(duration: 0.6).repeatForever(autoreverses: true)) {
                    animate = true
                }
            }
    }
}
