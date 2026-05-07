// PreviewBackgroundView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import SmartSpectra

/// Full-screen camera preview shown behind the screening overlay.
struct PreviewBackgroundView: View {
    @Environment(\.smartSpectraSDK) private var sdk

    var body: some View {
        ZStack {
            Color.black
            if let image = sdk.imageOutput {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
                    .saturation(sdk.processingStatus == .idle ? 0.3 : 1.0)
            }
            if sdk.processingStatus == .idle && sdk.imageOutput != nil {
                Color.black.opacity(0.4)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea()
    }
}
