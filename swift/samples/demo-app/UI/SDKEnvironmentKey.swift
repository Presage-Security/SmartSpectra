// SDKEnvironmentKey.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import SmartSpectra

extension EnvironmentValues {
    /// The `SmartSpectraSDK` instance the moved sample views read from when
    /// binding through `@Environment(\.smartSpectraSDK)`.
    ///
    /// Defaults to `SmartSpectraSDK.shared`. Apps that construct a custom
    /// instance via `SmartSpectraSDK(config:)` (for tests or side-by-side
    /// state) override this value with `.smartSpectraSDK(_:)` at the root
    /// of their SwiftUI hierarchy.
    @Entry var smartSpectraSDK: SmartSpectraSDK = MainActor.assumeIsolated {
        SmartSpectraSDK.shared
    }
}

extension View {
    /// Injects the given `SmartSpectraSDK` into the environment so descendants
    /// reading `@Environment(\.smartSpectraSDK)` resolve to it instead of
    /// `SmartSpectraSDK.shared`.
    func smartSpectraSDK(_ sdk: SmartSpectraSDK) -> some View {
        environment(\.smartSpectraSDK, sdk)
    }
}
