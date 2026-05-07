// SmartSpectraButtonViewModel.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import Foundation
import SwiftUI
import SmartSpectra

enum OnboardingStep {
    case tutorial
    case agreement
    case privacy
}

@Observable
@MainActor
final class SmartSpectraButtonViewModel {
    var showOnboardingSheet = false
    var currentOnboardingStep: OnboardingStep?
    var showScreening = false
    var isActionSheetPresented: Bool = false
    var isOnboardingFlow = false
    private(set) var canEnterScreeningFlow: Bool = true
    @ObservationIgnored private var sdk: SmartSpectraSDK?
    @ObservationIgnored private var videoInputEnabled: Bool = false
    @ObservationIgnored private var pendingOnboardingCompletion: (() -> Void)?

    private var configuredSDK: SmartSpectraSDK {
        guard let sdk else {
            preconditionFailure("SmartSpectraButtonViewModel.configure(sdk:) must be called from the view's onAppear before any other method.")
        }
        return sdk
    }

    init() {}

    /// Binds the view-model to the SDK resolved from the environment and
    /// recomputes screening-flow eligibility from current SDK state.
    /// Call once from the view's `onAppear` before any other method.
    /// `videoInputEnabled` reflects whether the host app has driven the SDK
    /// into video-file input mode (via the @_spi(Testing) setter); the sample
    /// tracks this locally since the SDK does not expose it on the public
    /// surface.
    func configure(sdk: SmartSpectraSDK, videoInputEnabled: Bool) {
        self.sdk = sdk
        self.videoInputEnabled = videoInputEnabled
        canEnterScreeningFlow = StartupRecovery.canEnterScreeningFlow(sdk: sdk, videoInputEnabled: videoInputEnabled)
    }

    /// Recomputes `canEnterScreeningFlow` from the current SDK state.
    /// Call when `sdk.error` changes — the view drives this via `.onChange`.
    func refreshCanEnterScreeningFlow() {
        canEnterScreeningFlow = StartupRecovery.canEnterScreeningFlow(sdk: configuredSDK, videoInputEnabled: videoInputEnabled)
    }

    func smartSpectraButtonTapped() {
        guard canEnterScreeningFlow else {
            return
        }
        showTutorialAndAgreementIfNecessary { [weak self] in
            self?.presentScreening()
        }
    }

    func presentActionSheet() {
        isActionSheetPresented = true
    }

    func startTutorialFlow() {
        UserDefaults.standard.set(false, forKey: "WalkthroughShown")
        isOnboardingFlow = false
        currentOnboardingStep = .tutorial
        showOnboardingSheet = true
    }

    func presentAgreementStandalone() {
        isOnboardingFlow = false
        currentOnboardingStep = .agreement
        showOnboardingSheet = true
    }

    func presentPrivacyStandalone() {
        isOnboardingFlow = false
        currentOnboardingStep = .privacy
        showOnboardingSheet = true
    }

    func handleTutorialCompletion() {
        UserDefaults.standard.set(true, forKey: "WalkthroughShown")
        completeCurrentStepAndAdvance()
    }

    func handleAgreementCompletion() {
        completeCurrentStepAndAdvance()
    }

    func handlePrivacyCompletion() {
        completeCurrentStepAndAdvance()
    }

    private func completeCurrentStepAndAdvance() {
        guard isOnboardingFlow else {
            dismissOnboarding()
            return
        }

        guard let currentStep = currentOnboardingStep else {
            dismissOnboarding()
            return
        }

        switch currentStep {
        case .tutorial:
            if !UserDefaults.standard.bool(forKey: "HasAgreedToTerms") {
                currentOnboardingStep = .agreement
                showOnboardingSheet = true
            } else if !UserDefaults.standard.bool(forKey: "HasAgreedToPrivacyPolicy") {
                currentOnboardingStep = .privacy
                showOnboardingSheet = true
            } else {
                completeOnboarding()
            }
        case .agreement:
            if !UserDefaults.standard.bool(forKey: "HasAgreedToPrivacyPolicy") {
                currentOnboardingStep = .privacy
                showOnboardingSheet = true
            } else {
                completeOnboarding()
            }
        case .privacy:
            completeOnboarding()
        }
    }

    private func dismissOnboarding() {
        showOnboardingSheet = false
        currentOnboardingStep = nil
    }

    /// Public cancellation entry point for the sheet (swipe-down or X button).
    /// Marks the tutorial as shown so the user isn't prompted again.
    func cancelOnboarding() {
        if currentOnboardingStep == .tutorial {
            UserDefaults.standard.set(true, forKey: "WalkthroughShown")
        }
        pendingOnboardingCompletion = nil
        dismissOnboarding()
    }

    private func completeOnboarding() {
        dismissOnboarding()
        finalizeOnboarding()
    }

    private func showTutorialAndAgreementIfNecessary(completion: @escaping () -> Void) {
        pendingOnboardingCompletion = completion
        isOnboardingFlow = true

        let walkthroughShown = UserDefaults.standard.bool(forKey: "WalkthroughShown")
        let hasAgreedToTerms = UserDefaults.standard.bool(forKey: "HasAgreedToTerms")
        let hasAgreedToPrivacyPolicy = UserDefaults.standard.bool(forKey: "HasAgreedToPrivacyPolicy")

        if !walkthroughShown {
            currentOnboardingStep = .tutorial
            showOnboardingSheet = true
        } else if !hasAgreedToTerms {
            currentOnboardingStep = .agreement
            showOnboardingSheet = true
        } else if !hasAgreedToPrivacyPolicy {
            currentOnboardingStep = .privacy
            showOnboardingSheet = true
        } else {
            finalizeOnboarding()
        }
    }

    private func finalizeOnboarding() {
        let completion = pendingOnboardingCompletion
        pendingOnboardingCompletion = nil
        completion?()
    }

    private func presentScreening() {
        showScreening = true
    }
}
