// ScreeningViewModel.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import Foundation
import SwiftUI
import Network
import SmartSpectra

enum ButtonState {
    case disable, idle, running
}

@MainActor
@Observable
final class ScreeningViewModel {
    var buttonState: ButtonState = .disable
    var showNoInternetAlert = false

    let tipMessage = "Please ensure the subject's face, shoulders, and upper chest are in view and remove any clothing that may impede visibility. Please refer to Instructions For Use for more information."
    @ObservationIgnored private var sdk: SmartSpectraSDK?
    @ObservationIgnored private var pathMonitorTask: Task<Void, Never>?
    @ObservationIgnored private var sessionTask: Task<Void, Never>?

    private var configuredSDK: SmartSpectraSDK {
        guard let sdk else {
            preconditionFailure("ScreeningViewModel.configure(sdk:) must be called from the view's onAppear before any other method.")
        }
        return sdk
    }

    /// Binds the view-model to the SDK resolved from the environment.
    /// Seeds `buttonState` from the current processing status — the view
    /// drives subsequent updates through `.onChange(of: sdk.processingStatus)`,
    /// which only fires on transitions.
    func configure(sdk: SmartSpectraSDK) {
        self.sdk = sdk
        updateButtonState(from: sdk.processingStatus)
    }

    func startSession() {
        let sdk = configuredSDK
        sessionTask?.cancel()
        sessionTask = Task { @MainActor in
            do {
                try await sdk.start()
            } catch {
                // The SDK surfaces errors via `sdk.error` / `processingStatus`;
                // the screening view observes those.
            }
        }
        monitorInternetConnection()
    }

    func stopSession() {
        pathMonitorTask?.cancel()
        pathMonitorTask = nil
        let sdk = configuredSDK
        sessionTask?.cancel()
        sessionTask = Task { @MainActor in
            try? await sdk.stop()
        }
    }

    func handleBackButton() {
        stopSession()
    }

    func recordButtonTapped() {
        switch buttonState {
        case .idle:
            startSession()
        case .running:
            stopSession()
        case .disable:
            break
        }
    }

    func updateButtonState(from processingStatus: ProcessingStatus) {
        buttonState = switch processingStatus {
        case .running: .running
        case .idle: .idle
        default: .disable
        }
    }

    private func monitorInternetConnection() {
        pathMonitorTask?.cancel()
        pathMonitorTask = Task { [weak self] in
            let monitor = NWPathMonitor()
            // Two cleanup hooks, covering complementary unwind paths:
            //
            // 1. `continuation.onTermination` fires when the stream's
            //    consumer side is released — most importantly when this
            //    Task is cancelled while `for await` is suspended waiting
            //    for the next path update.
            //
            // 2. `defer { monitor.cancel() }` guarantees cancellation on
            //    normal Task-closure exit.
            //
            // `NWPathMonitor.cancel()` is idempotent.
            defer { monitor.cancel() }
            let stream = AsyncStream<NWPath> { continuation in
                monitor.pathUpdateHandler = { continuation.yield($0) }
                continuation.onTermination = { _ in monitor.cancel() }
            }
            monitor.start(queue: .global(qos: .utility))
            for await path in stream {
                if Task.isCancelled { break }
                guard let self else { break }
                self.showNoInternetAlert = (path.status != .satisfied)
            }
        }
    }
}
