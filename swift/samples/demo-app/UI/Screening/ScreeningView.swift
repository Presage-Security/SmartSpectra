// ScreeningView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import UIKit
import SmartSpectra

private struct FullBrightnessModifier: ViewModifier {
    @State private var originalBrightness: CGFloat?

    func body(content: Content) -> some View {
        content
            .onAppear {
                guard let screen = Self.activeScreen() else { return }
                originalBrightness = screen.brightness
                screen.brightness = 1.0
            }
            .onDisappear {
                guard let screen = Self.activeScreen(), let originalBrightness else { return }
                screen.brightness = originalBrightness
                self.originalBrightness = nil
            }
    }

    /// Returns the screen backing the currently-foreground scene.
    /// Prefer this over `UIScreen.main` — the latter is deprecated on iOS 16+.
    @MainActor
    private static func activeScreen() -> UIScreen? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive || $0.activationState == .foregroundInactive }?
            .screen
    }
}

extension View {
    fileprivate func fullBrightness() -> some View {
        modifier(FullBrightnessModifier())
    }
}

struct SmartSpectraScreeningView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.smartSpectraSDK) private var sdk
    @State private var viewModel = ScreeningViewModel()
    @State private var showTipAlert = false
    let videoInputEnabled: Bool

    init(videoInputEnabled: Bool = false) {
        self.videoInputEnabled = videoInputEnabled
    }

    var body: some View {
        NavigationStack {
            ZStack {
                PreviewBackgroundView()
                SmartSpectraScreeningOverlay()
                    .allowsHitTesting(false)
                if sdk.processingStatus == .starting {
                    Color.black.opacity(0.4)
                        .ignoresSafeArea()
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(.white)
                        .scaleEffect(2.2)
                }
                VStack(spacing: 0) {
                    ScreeningPlotView()
                        .padding(.horizontal, 24)
                    Spacer()
                }

                if sdk.processingStatus == .running && sdk.facialExpressionEnabled {
                    VStack {
                        FaceMetricsStatusView()
                            .padding(.top, 8)
                        Spacer()
                    }
                }

                ScreeningControlsOverlay(viewModel: viewModel)

                if sdk.processingStatus == .error {
                    ProcessingOverlayView(
                        status: sdk.processingStatus,
                        onDismiss: {
                            viewModel.handleBackButton()
                            dismiss()
                        },
                        onOpenSettings: StartupRecovery.shouldShowOpenSettingsAction(sdk: sdk, videoInputEnabled: videoInputEnabled) ? {
                            guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else {
                                return
                            }
                            openURL(settingsUrl)
                        } : nil,
                        messageOverride: sdk.error?.message ?? sdk.validationStatus?.hint
                    )
                    .transition(.opacity)
                }
            }
            .navigationBarBackButtonHidden(true)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text("Presage SmartSpectra")
                        .font(.headline)
                        .foregroundStyle(.primary)
                }
                ToolbarItem(placement: .topBarLeading) {
                    Button("Back", systemImage: "arrow.left") {
                        viewModel.handleBackButton()
                        dismiss()
                    }
                    .labelStyle(.iconOnly)
                    .foregroundStyle(.secondary)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Tip", systemImage: "info.circle") {
                        showTipAlert = true
                    }
                    .labelStyle(.iconOnly)
                    .foregroundStyle(.secondary)
                }
            }
            .alert("Tip", isPresented: $showTipAlert) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.tipMessage)
            }
            .alert("No Internet Connection", isPresented: $viewModel.showNoInternetAlert) {
                Button("OK", role: .cancel) {
                    viewModel.handleBackButton()
                }
            } message: {
                Text("Please check your internet connection and try again.")
            }
            .fullBrightness()
            .onAppear {
                viewModel.configure(sdk: sdk)
                viewModel.startSession()
            }
            .onDisappear {
                viewModel.stopSession()
            }
            .onChange(of: sdk.processingStatus) { _, newStatus in
                viewModel.updateButtonState(from: newStatus)
            }
        }
    }
}

private struct SmartSpectraScreeningOverlay: View {
    private let horizontalPadding: CGFloat = 40
    private let bottomBackdropFraction: CGFloat = 0.04
    private let cornerRadius: CGFloat = 28
    private let topPadding: CGFloat = 2
    private let overlayOpacity: CGFloat = 0.85

    var body: some View {
        GeometryReader { geometry in
            let minBottomHeight: CGFloat = 40
            let bottomHeight = max(geometry.size.height * bottomBackdropFraction, minBottomHeight)
            let topInset = geometry.safeAreaInsets.top + topPadding
            let extraCutout = max(geometry.size.height * 0.05, 20)
            let availableHeight = geometry.size.height - bottomHeight - topInset
            let extendedHeight = availableHeight + extraCutout
            let maxAllowedHeight = geometry.size.height - topInset - 10
            let cutoutHeight = min(max(0, extendedHeight), maxAllowedHeight)
            let cutoutRect = CGRect(
                x: horizontalPadding,
                y: topInset,
                width: max(0, geometry.size.width - horizontalPadding * 2),
                height: cutoutHeight
            )

            SmartSpectraOverlayShape(cutout: cutoutRect, cornerRadius: cornerRadius)
                .fill(Color.white, style: FillStyle(eoFill: true))
                .opacity(overlayOpacity)
                .ignoresSafeArea()
        }
    }
}

private struct SmartSpectraOverlayShape: Shape {
    let cutout: CGRect
    let cornerRadius: CGFloat

    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addRect(rect)
        path.addRoundedRect(in: cutout, cornerSize: CGSize(width: cornerRadius, height: cornerRadius))
        return path
    }
}
