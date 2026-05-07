// StartupRecovery.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import AVFoundation
import SmartSpectra

@MainActor
enum StartupRecovery {
    static func shouldShowOpenSettingsAction(sdk: SmartSpectraSDK, videoInputEnabled: Bool) -> Bool {
        guard !videoInputEnabled else { return false }
        return sdk.error?.code == .inputUnavailable &&
            AVCaptureDevice.authorizationStatus(for: .video) == .denied
    }

    static func canEnterScreeningFlow(sdk: SmartSpectraSDK, videoInputEnabled: Bool) -> Bool {
        let inputBlocked = sdk.error?.code == .inputUnavailable
        return !inputBlocked || shouldShowOpenSettingsAction(sdk: sdk, videoInputEnabled: videoInputEnabled)
    }
}
