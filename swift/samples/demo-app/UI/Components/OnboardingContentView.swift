// OnboardingContentView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI

/// Presents one of the three onboarding steps — tutorial, terms of service, or
/// privacy policy — inside the onboarding sheet.
struct OnboardingContentView: View {
    let step: OnboardingStep?
    let onTutorialCompleted: () -> Void
    let onAgreementCompleted: () -> Void
    let onPrivacyCompleted: () -> Void

    var body: some View {
        if let step {
            switch step {
            case .tutorial:
                TutorialView(onTutorialCompleted: onTutorialCompleted)
                    .presentationDetents([.large])
                    .presentationDragIndicator(.visible)
            case .agreement:
                LegalDocumentView(
                    document: .termsOfService,
                    onCompletion: onAgreementCompleted
                )
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
            case .privacy:
                LegalDocumentView(
                    document: .privacyPolicy,
                    onCompletion: onPrivacyCompleted
                )
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
            }
        }
    }
}
