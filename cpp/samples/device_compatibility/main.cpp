// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
#include <smartspectra/device_compatibility.h>
#include <smartspectra/smartspectra.h>
#include <cstdlib>
#include <iostream>

namespace spectra = presage::smartspectra;

int main() {
    spectra::SmartSpectraConfig config;
    if (const char* key = std::getenv("PHYSIOLOGY_API_KEY")) config.api_key = key;

    spectra::SmartSpectra sdk(config);
    std::cout << "Checking device compatibility...\n";
    // Optional, explicit test. Run off the UI thread in a graphical application.
    const auto verdict = sdk.isDeviceCompatible();
    std::cout << (verdict == spectra::DeviceCompatibilityVerdict::kReady ? "Ready" : "Compatibility not confirmed") << "\n";
    return verdict == spectra::DeviceCompatibilityVerdict::kReady ? 0 : 2;
}
