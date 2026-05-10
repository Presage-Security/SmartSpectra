// LegalDocumentView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI

struct LegalDocumentView: View {
    enum Document {
        case privacyPolicy
        case termsOfService

        var title: String {
            switch self {
            case .privacyPolicy:
                "Privacy Policy"
            case .termsOfService:
                "Terms of Service"
            }
        }

        var url: URL {
            switch self {
            case .privacyPolicy:
                URL(string: "https://api.physiology.presagetech.com/privacypolicy")!
            case .termsOfService:
                URL(string: "https://api.physiology.presagetech.com/termsofservice")!
            }
        }

        var userDefaultsKey: String {
            switch self {
            case .privacyPolicy:
                "HasAgreedToPrivacyPolicy"
            case .termsOfService:
                "HasAgreedToTerms"
            }
        }
    }

    let document: Document
    var onCompletion: (() -> Void)?

    @State private var isContentLoaded = false

    var body: some View {
        VStack(spacing: 0) {
            WebContentView(url: document.url) { loaded in
                isContentLoaded = loaded
            }
            .ignoresSafeArea(.container, edges: .horizontal)
            Divider()
            HStack(spacing: 12) {
                Button(action: decline) {
                    Text("Decline")
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(Color.red)
                        .foregroundStyle(.white)
                        .clipShape(.rect(cornerRadius: 10))
                }
                Button(action: agree) {
                    Text("Agree")
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(Color.blue)
                        .foregroundStyle(.white)
                        .clipShape(.rect(cornerRadius: 10))
                }
                .disabled(!isContentLoaded)
                .opacity(isContentLoaded ? 1 : 0.5)
            }
            .padding()
        }
        .navigationTitle(document.title)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func agree() {
        updateAgreementState(true)
    }

    private func decline() {
        updateAgreementState(false)
    }

    private func updateAgreementState(_ value: Bool) {
        UserDefaults.standard.set(value, forKey: document.userDefaultsKey)
        onCompletion?()
    }
}
