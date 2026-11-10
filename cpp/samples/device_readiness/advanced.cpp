// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
#include <smartspectra/smartspectra.h>
#include "presentation.h"
#include <cstdlib>
#include <iostream>
int main() {
    presage::smartspectra::SmartSpectraConfig config;
    if (const auto* key = std::getenv("PHYSIOLOGY_API_KEY")) config.api_key = key;
    presage::smartspectra::SmartSpectra sdk(config);
    const auto result = sdk.checkDeviceReadiness();
    std::cout << readiness_example::Title(result) << '\n' << result.reason << '\n';
    return readiness_example::CanProceed(result) ? 0 : 2;
}
