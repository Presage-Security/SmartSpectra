// main.cc
// Copyright (C) 2024-2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

// minimal_example: Smallest runnable SmartSpectra desktop sample.
//
// Usage:
//   ./minimal_example --api_key=YOUR_KEY

#include <string>
#include <iostream>

#include <absl/flags/flag.h>
#include <absl/flags/parse.h>
#include <google/protobuf/util/json_util.h>

#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/smartspectra_types.h>

namespace spectra = presage::smartspectra;

ABSL_FLAG(std::string, api_key, "", "API key for the Physiology service.");

int main(int argc, char** argv) {
    absl::ParseCommandLine(argc, argv);

    spectra::SmartSpectraConfig config;
    config.api_key = absl::GetFlag(FLAGS_api_key);
    config.requested_metrics = spectra::SmartSpectraConfig::DefaultSupportedMetrics();

    spectra::SmartSpectra smart_spectra(std::move(config));

    smart_spectra.SetOnMetrics(
        [](const spectra::Metrics& m, int64_t ts) {
            std::string json;
            google::protobuf::util::JsonPrintOptions options;
            // can overwhelm log output if whitespace is enabled
            options.add_whitespace = false;
            google::protobuf::util::MessageToJsonString(m, &json, options);
            std::cout << "Got edge metrics at " << ts << " microseconds: " << json << '\n';
        }
    );

    smart_spectra.SetOnError([](const spectra::SmartSpectraError& error) {
        std::cerr << error.FullMessage() << '\n';
    });

    const auto source_error = smart_spectra.UseCamera().Build();
    if (!source_error.ok()) {
        std::cerr << "SmartSpectra::UseCamera failed: " << source_error.message << '\n';
        return EXIT_FAILURE;
    }

    if (const auto err = smart_spectra.Start(); !err.ok()) {
        std::cerr << err.FullMessage() << '\n';
        return EXIT_FAILURE;
    }

    std::cout << "Running... (Ctrl+C to stop)\n";

    // Blocks until EOF (file source) or Stop() (camera source).
    smart_spectra.WaitUntilComplete();

    if (const auto err = smart_spectra.Stop(); !err.ok()) {
        std::cerr << "Stop failed: " << err.message << '\n';
    }

    std::cout << "Done.\n";
    return 0;
}
