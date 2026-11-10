// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
#include <smartspectra/device_readiness.h>
#include <smartspectra/smartspectra.h>
#include <cstdlib>
#include <iostream>

namespace spectra = presage::smartspectra;

int main() {
    spectra::SmartSpectraConfig config;
    if (const char* key = std::getenv("PHYSIOLOGY_API_KEY")) config.api_key = key;

    spectra::SmartSpectra sdk(config);
    // Optional, explicit test. Run off the UI thread in a graphical application.
    const auto result = sdk.checkDeviceReadiness();
    switch (result.verdict) {
        case spectra::DeviceReadinessVerdict::kReady: std::cout << "Ready"; break;
        case spectra::DeviceReadinessVerdict::kLimited: std::cout << "Limited"; break;
        case spectra::DeviceReadinessVerdict::kBlocked: std::cout << "Blocked"; break;
        case spectra::DeviceReadinessVerdict::kInconclusive: std::cout << "Inconclusive"; break;
    }
    std::cout << '\n';
    std::cout << result.reason << '\n';
    return result.verdict == spectra::DeviceReadinessVerdict::kReady ? 0 : 2;
}
