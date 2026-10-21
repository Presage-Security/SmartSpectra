// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
#include <smartspectra/smartspectra.h>
#include "presentation.h"
#include "report_file.h"
#include <cstdlib>
#include <future>
#include <iostream>
int main(int argc, char** argv) {
    presage::smartspectra::SmartSpectraConfig config;
    if (const auto* key = std::getenv("PHYSIOLOGY_API_KEY")) config.api_key = key;
    presage::smartspectra::SmartSpectra sdk(config);
    // A UI application can keep handling events while this worker runs.
    auto pending = std::async(std::launch::async, [&] { return sdk.isDeviceCompatible(); });
    pending.get();
    const auto saved = sdk.getDeviceCompatibilityResult();
    if (!saved) {
        std::cerr << "Compatibility check was cancelled or no result is available.\n";
        return 2;
    }
    const auto& result = *saved;
    std::cout << compatibility_example::Title(result) << '\n' << result.reason << '\n';
    if (argc > 1 && !compatibility_example::WriteSupportReport(argv[1], result.diagnostics)) {
        std::cerr << "Could not create diagnostic report (existing files are not overwritten).\n";
        return 1;
    }
    return compatibility_example::CanProceed(result) ? 0 : 2;
}
