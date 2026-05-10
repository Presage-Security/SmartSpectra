// SmartSpectraButtonView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import SmartSpectra

struct SmartSpectraButtonView: View {
    @State private var viewModel = SmartSpectraButtonViewModel()
    @Environment(\.smartSpectraSDK) private var sdk
    @Environment(\.openURL) private var openURL
    let videoInputEnabled: Bool
    private let height: CGFloat = 56

    init(videoInputEnabled: Bool = false) {
        self.videoInputEnabled = videoInputEnabled
    }

    var body: some View {
        @Bindable var viewModel = viewModel
        HStack {
            SmartSpectraCheckupButton {
                viewModel.smartSpectraButtonTapped()
            }
            .disabled(!viewModel.canEnterScreeningFlow)
            .opacity(viewModel.canEnterScreeningFlow ? 1.0 : 0.6)
            Spacer()
            SmartSpectraInfoButton {
                viewModel.presentActionSheet()
            }
            .frame(maxWidth: height)
        }
        .frame(maxWidth: 300, minHeight: height, maxHeight: height)
        .background(Color.brandPrimary)
        .clipShape(RoundedRectangle(cornerRadius: height / 2))
        .onAppear {
            viewModel.configure(sdk: sdk, videoInputEnabled: videoInputEnabled)
        }
        .onChange(of: sdk.error) { _, _ in
            viewModel.refreshCanEnterScreeningFlow()
        }
        // SwiftUI does not auto-forward custom @Entry environment values
        // across sheet/fullScreenCover presentation boundaries — re-inject
        // the SDK we resolved at this view so the screening flow renders
        // against the same instance.
        .sheet(isPresented: $viewModel.showOnboardingSheet, onDismiss: {
            viewModel.cancelOnboarding()
        }) {
            NavigationStack {
                OnboardingContentView(
                    step: viewModel.currentOnboardingStep,
                    onTutorialCompleted: viewModel.handleTutorialCompletion,
                    onAgreementCompleted: viewModel.handleAgreementCompletion,
                    onPrivacyCompleted: viewModel.handlePrivacyCompletion
                )
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Cancel", systemImage: "xmark.circle.fill", action: viewModel.cancelOnboarding)
                            .labelStyle(.iconOnly)
                    }
                }
            }
            .environment(\.smartSpectraSDK, sdk)
        }
        .fullScreenCover(isPresented: $viewModel.showScreening) {
            SmartSpectraScreeningView(videoInputEnabled: videoInputEnabled)
                .environment(\.smartSpectraSDK, sdk)
        }
        .confirmationDialog("Options", isPresented: $viewModel.isActionSheetPresented, titleVisibility: .visible) {
            Button("Show Tutorial") {
                viewModel.startTutorialFlow()
            }
            Button("Instructions for Use") {
                if let url = URL(string: "https://api.physiology.presagetech.com/instructions") {
                    openURL(url)
                }
            }
            Button("Terms of Service") {
                viewModel.presentAgreementStandalone()
            }
            Button("Privacy Policy") {
                viewModel.presentPrivacyStandalone()
            }
            Button("Contact Us") {
                if let url = URL(string: "https://api.physiology.presagetech.com/contact") {
                    openURL(url)
                }
            }
            Button("Cancel", role: .cancel) {}
        }
    }
}
