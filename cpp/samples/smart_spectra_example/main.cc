// main.cc
// Copyright (C) 2024-2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

// smart_spectra_example: Desktop sample using SmartSpectra.
//
// Usage:
//   ./smart_spectra_example --api_key=YOUR_KEY [--camera_device_index=0] [--input_video_path=path.mp4]

#include <iostream>
#include <string>

#include <absl/flags/flag.h>
#include <absl/flags/parse.h>
#include <google/protobuf/util/json_util.h>

#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/smartspectra_types.h>

namespace spectra = presage::smartspectra;

ABSL_FLAG(std::string, api_key, "", "API key for the Physiology service.");
ABSL_FLAG(int, camera_device_index, 0, "The index of the camera device to use.");
ABSL_FLAG(std::string, input_video_path, "", "Path to video file (omit for camera).");

int main(int argc, char** argv) {
    absl::ParseCommandLine(argc, argv);

    // --- Set up SmartSpectra (frames in, vitals out) ---
    spectra::SmartSpectraConfig config;
    config.api_key = absl::GetFlag(FLAGS_api_key);
    config.requested_metrics = spectra::SmartSpectraConfig::DefaultSupportedMetrics();

    spectra::SmartSpectra smart_spectra(std::move(config));

    smart_spectra.SetOnMetrics(
        [](const spectra::Metrics& m, int64_t ts) {
            if (m.has_breathing() && m.breathing().rate_size() > 0) {
                std::cout << "[edge] BR="
                          << m.breathing().rate(m.breathing().rate_size() - 1).value()
                          << " ts=" << ts << '\n';
            }
            if (m.has_cardio() && m.cardio().pulse_rate_size() > 0) {
                std::cout << "[edge] PR="
                          << m.cardio().pulse_rate(m.cardio().pulse_rate_size() - 1).value()
                          << " ts=" << ts << '\n';
            }
        });

    smart_spectra.SetOnValidationStatusChanged(
        [](const spectra::ValidationStatus& vs, int64_t ts) {
            std::cout << "[validation] " << vs.code
                      << " hint=" << vs.hint << " ts=" << ts << '\n';
        });

    smart_spectra.SetOnError([](const spectra::SmartSpectraError& error) {
        std::cerr << error.FullMessage() << '\n';
    });

    // --- Video source ---
    const std::string video_path = absl::GetFlag(FLAGS_input_video_path);
    if (!video_path.empty()) {
        const auto source_error = smart_spectra.UseFile(video_path).Build();
        if (!source_error.ok()) {
            std::cerr << "SmartSpectra::UseFile failed: "
                      << source_error.message << '\n';
            return EXIT_FAILURE;
        }
    } else {
        const auto source_error =
            smart_spectra.UseCamera(absl::GetFlag(FLAGS_camera_device_index)).Build();
        if (!source_error.ok()) {
            std::cerr << "SmartSpectra::UseCamera failed: "
                      << source_error.message << '\n';
            return EXIT_FAILURE;
        }
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
