// SDKExtensions.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SmartSpectra

/// Sample-local mirrors of the SDK's `config.cardioMeasurementsEnabled` /
/// `edaInferenceEnabled` / `facialExpressionEnabled` derived flags, computed
/// from the public `requestedMetrics` surface. These exist so the moved
/// screening UI can read what's enabled without the SDK promoting its
/// derived flags to public API.
extension SmartSpectraSDK {
    var cardioMeasurementsEnabled: Bool {
        let cardio = Set(SmartSpectraConfig.cardioMetrics)
        return config.requestedMetrics?.contains(where: cardio.contains) ?? false
    }

    var facialExpressionEnabled: Bool {
        let face = Set(SmartSpectraConfig.faceMetrics)
        return config.requestedMetrics?.contains(where: face.contains) ?? false
    }

    var edaInferenceEnabled: Bool {
        config.requestedMetrics?.contains(.edaTrace) ?? false
    }
}
