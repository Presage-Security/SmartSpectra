// InsightsChatView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI

struct InsightsChatView: View {
    /// Owned by the presenting view so the conversation survives sheet
    /// dismissals. Creating the VM inside this struct would tie its
    /// lifetime to the sheet's SwiftUI identity, wiping chat history
    /// every time the sheet closed.
    let viewModel: InsightsChatViewModel

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Image(systemName: "brain.head.profile")
                    .foregroundStyle(.purple)
                    .accessibilityHidden(true)
                Text("Health Insights")
                    .font(.headline)
                Spacer()
            }
            .padding()
            .background(Color(.systemBackground))

            Divider()

            // Messages
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(viewModel.messages) { message in
                            ChatBubbleView(message: message)
                                .id(message.id)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                }
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: viewModel.messages.count) {
                    if let last = viewModel.messages.last {
                        withAnimation(.easeOut(duration: 0.2)) {
                            proxy.scrollTo(last.id, anchor: .bottom)
                        }
                    }
                }
            }

            Divider()

            InsightsChatInputBar(viewModel: viewModel)
        }
        .onAppear {
            viewModel.startObservingInsightsIfNeeded()
        }
    }
}

private struct InsightsChatInputBar: View {
    let viewModel: InsightsChatViewModel

    @State private var draftText = ""

    private var trimmedDraftText: String {
        draftText.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        HStack(spacing: 8) {
            TextField("Ask about your vitals…", text: $draftText, axis: .vertical)
                .lineLimit(1...3)
                .textFieldStyle(.roundedBorder)

            Button("Send", systemImage: "arrow.up.circle.fill", action: sendMessage)
                .labelStyle(.iconOnly)
                .font(.system(size: 32))
                .foregroundStyle(
                    trimmedDraftText.isEmpty || viewModel.isSending
                        ? Color.secondary
                        : Color.accentColor
                )
                .disabled(trimmedDraftText.isEmpty || viewModel.isSending)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color(.systemBackground))
    }

    private func sendMessage() {
        let text = draftText
        draftText = ""
        viewModel.sendMessage(text)
    }
}

struct ChatBubbleView: View {
    let message: ChatMessage

    var body: some View {
        HStack(alignment: .bottom, spacing: 0) {
            if message.isUser { Spacer(minLength: 60) }

            Group {
                if message.isLoading {
                    TypingIndicatorView()
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                } else {
                    Text(message.text)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                }
            }
            .background(message.isUser ? Color.accentColor : Color(.systemGray5))
            .foregroundStyle(message.isUser ? .white : .primary)
            .clipShape(RoundedRectangle(cornerRadius: 18))

            if !message.isUser { Spacer(minLength: 60) }
        }
        .frame(maxWidth: .infinity, alignment: message.isUser ? .trailing : .leading)
    }
}

struct TypingIndicatorView: View {
    @State private var phase = 0
    private let timer = Timer.publish(every: 0.4, on: .main, in: .common).autoconnect()

    var body: some View {
        HStack(spacing: 5) {
            ForEach(0..<3, id: \.self) { i in
                Circle()
                    .frame(width: 8, height: 8)
                    .opacity(phase == i ? 1.0 : 0.3)
                    .animation(.easeInOut(duration: 0.3), value: phase)
            }
        }
        .onReceive(timer) { _ in
            phase = (phase + 1) % 3
        }
    }
}
