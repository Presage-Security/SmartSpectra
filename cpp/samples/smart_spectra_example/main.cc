// main.cc
// Copyright (C) 2024-2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

// smart_spectra_example: Desktop sample using SmartSpectra.
//
// Usage:
//   ./smart_spectra_example --api_key=YOUR_KEY [--camera_device_index=0] [--input_video_path=path.mp4]
//   ./smart_spectra_example --list_cameras
//   ./smart_spectra_example --api_key=YOUR_KEY --camera_id=DISCOVERED_ID

#include <iostream>
#include <string>
#include <vector>

#include <absl/flags/flag.h>
#include <absl/flags/parse.h>
#include <google/protobuf/util/json_util.h>

#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/smartspectra_types.h>

namespace spectra = presage::smartspectra;

ABSL_FLAG(std::string, api_key, "", "API key for the Physiology service.");
ABSL_FLAG(int, camera_device_index, 0, "The index of the camera device to use.");
ABSL_FLAG(bool, list_cameras, false, "List camera IDs and exit without authentication (Linux/macOS/Windows).");
ABSL_FLAG(std::string, camera_id, "", "Exact discovered camera ID; overrides camera_device_index (Linux/macOS/Windows).");
ABSL_FLAG(std::string, input_video_path, "", "Path to video file (omit for camera).");

int main(int argc, char** argv) {
    absl::ParseCommandLine(argc, argv);

    if (absl::GetFlag(FLAGS_list_cameras)) {
        std::vector<spectra::CameraInfo> cameras;
        if (auto error = spectra::SmartSpectra::AvailableCameras(cameras); !error.ok()) {
            std::cerr << error.FullMessage() << '\n';
            return EXIT_FAILURE;
        }
        for (const auto& camera : cameras) {
            const char* facing = camera.facing == spectra::CameraFacing::kFront ? "front" :
                                 camera.facing == spectra::CameraFacing::kBack ? "back" : "unknown";
            const char* lens = camera.lens_type == spectra::CameraLensType::kWideAngle ? "wide-angle" :
                               camera.lens_type == spectra::CameraLensType::kUltraWide ? "ultra-wide" :
                               camera.lens_type == spectra::CameraLensType::kTelephoto ? "telephoto" : "unknown";
            std::cout << camera.id << '\t' << camera.name.value_or("Unnamed camera")
                      << '\t' << facing << '\t' << lens << '\n';
        }
        return EXIT_SUCCESS;
    }

    const std::string camera_id = absl::GetFlag(FLAGS_camera_id);
    const std::string video_path = absl::GetFlag(FLAGS_input_video_path);
    if (!camera_id.empty() && !video_path.empty()) {
        std::cerr << "Choose either --camera_id or --input_video_path.\n";
        return EXIT_FAILURE;
    }

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
    if (!video_path.empty()) {
        const auto source_error = smart_spectra.UseFile(video_path).Build();
        if (!source_error.ok()) {
            std::cerr << "SmartSpectra::UseFile failed: "
                      << source_error.message << '\n';
            return EXIT_FAILURE;
        }
    } else {
        const auto source_error = camera_id.empty()
            ? smart_spectra.UseCamera(absl::GetFlag(FLAGS_camera_device_index)).Build()
            : smart_spectra.UseCamera(spectra::CameraSelection::ById(camera_id)).Build();
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
