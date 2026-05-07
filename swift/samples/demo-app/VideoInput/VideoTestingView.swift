// VideoTestingView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
@_spi(Testing) import SmartSpectra

/// Tab for testing video file input against the SDK.
///
/// All video-input API is gated behind `@_spi(Testing)`, so this view
/// (and its `@_spi(Testing) import`) is only usable by consumers that
/// explicitly opt in with `@_spi(Testing) import SmartSpectra`.
struct VideoTestingView: View {
    private let sdk = SmartSpectraSDK.shared
    @State private var selectedVideoPath: String = ""

    private var videoReady: Bool { !selectedVideoPath.isEmpty }

    var body: some View {
        VStack {
            SmartSpectraView(videoInputEnabled: true)
                .disabled(!videoReady)
                .grayscale(videoReady ? 0 : 1)

            if !videoReady {
                Text("Pick a video file to begin")
                    .font(.headline)
                    .foregroundStyle(.primary)
            }

            VideoInputControlPanel(selectedVideoPath: $selectedVideoPath)

            Spacer()
        }
        .padding()
        .onAppear {
            sdk.setVideoInputEnabled(true)
        }
        .onDisappear {
            sdk.setVideoInputEnabled(false)
        }
    }
}
