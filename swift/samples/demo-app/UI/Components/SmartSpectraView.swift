// SmartSpectraView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import SmartSpectra

/// One-line capture view: shows the Checkup button, runs the screening flow,
/// and renders the result card. Sample-app reimplementation of the v3
/// `SmartSpectraView` — moved out of the SDK in v4. Drop this folder into
/// your own project to keep the same behavior, then customize as needed.
struct SmartSpectraView: View {
    @Environment(\.smartSpectraSDK) private var envSDK
    private let injectedSDK: SmartSpectraSDK?
    private let videoInputEnabled: Bool

    private var sdk: SmartSpectraSDK { injectedSDK ?? envSDK }

    /// Creates a new capture view bound to a specific SDK instance.
    ///
    /// - Parameters:
    ///   - sdk: Optional SDK instance the view (and its descendants) render
    ///     against. When `nil` (default), the view resolves its SDK from the
    ///     environment via `\.smartSpectraSDK`, defaulting to
    ///     ``SmartSpectraSDK/shared``.
    ///   - videoInputEnabled: Set to `true` if the host has driven the SDK
    ///     into video-file input mode (via the `@_spi(Testing)` setter), so
    ///     the screening flow's permission-recovery path skips the camera-
    ///     denied "Open Settings" branch.
    init(sdk: SmartSpectraSDK? = nil, videoInputEnabled: Bool = false) {
        self.injectedSDK = sdk
        self.videoInputEnabled = videoInputEnabled
    }

    var body: some View {
        VStack {
            SmartSpectraButtonView(videoInputEnabled: videoInputEnabled)
            SmartSpectraResultView()
        }
        .ignoresSafeArea()
        .environment(\.smartSpectraSDK, sdk)
    }
}
