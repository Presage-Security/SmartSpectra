// InsightsChatViewModel.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import Foundation
import SmartSpectra
import Observation

struct ChatMessage: Identifiable {
    let id = UUID()
    let text: String
    let isUser: Bool
    var isLoading: Bool = false
}

@MainActor
@Observable
final class InsightsChatViewModel {

    var messages: [ChatMessage] = []
    var isSending: Bool = false

    @ObservationIgnored private let sdk = SmartSpectraSDK.shared
    @ObservationIgnored private var pendingRequestId: Int32?
    @ObservationIgnored private var hasStartedObservingInsights = false

    init() {
        messages.append(ChatMessage(
            text: "Hi! I can answer questions about your health metrics while monitoring is active. What would you like to know?",
            isUser: false
        ))
    }

    func startObservingInsightsIfNeeded() {
        guard !hasStartedObservingInsights else { return }
        hasStartedObservingInsights = true
        observeInsight()
    }

    private func observeInsight() {
        withObservationTracking {
            _ = sdk.insight
        } onChange: { [weak self] in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.handleInsightUpdate(self.sdk.insight)
                self.observeInsight()
            }
        }
    }

    private func handleInsightUpdate(_ insight: Insight?) {
        guard let insight, insight.requestID == pendingRequestId else { return }
        switch insight.result {
        case .analysis(let text):
            replaceLoadingMessage(with: text)
        case .error:
            replaceLoadingMessage(with: "The insights service is currently unavailable. Please try again later.")
        case .none:
            break
        }
        pendingRequestId = nil
        isSending = false
    }

    func sendMessage(_ draftText: String) {
        let text = draftText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isSending else { return }

        messages.append(ChatMessage(text: text, isUser: true))
        messages.append(ChatMessage(text: "", isUser: false, isLoading: true))
        isSending = true

        do {
            pendingRequestId = try sdk.requestInsight(text)
        } catch {
            replaceLoadingMessage(with: "Sorry, I couldn't process that request. Make sure monitoring is active and try again.")
            isSending = false
        }
    }

    private func replaceLoadingMessage(with text: String) {
        if let idx = messages.lastIndex(where: { $0.isLoading }) {
            messages[idx] = ChatMessage(text: text, isUser: false)
        }
    }
}
