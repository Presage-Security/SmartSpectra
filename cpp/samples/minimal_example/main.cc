// main.cc
// Copyright (C) 2024-2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

// minimal_example: Smallest runnable SmartSpectra desktop sample.
//
// Usage:
//   ./minimal_example --api_key=YOUR_KEY [--input_video_path=video.mp4]
//       [--requested_metrics=breathing_rate,pulse_rate,hrv]

#include <cstdlib>
#include <string>
#include <iostream>
#include <vector>

#include <absl/flags/flag.h>
#include <absl/flags/parse.h>
#include <google/protobuf/util/json_util.h>

#include <smartspectra/messages/metric_types.h>
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/smartspectra_types.h>

namespace spectra = presage::smartspectra;

ABSL_FLAG(std::string, api_key, "", "API key for the Physiology service.");
ABSL_FLAG(std::string, input_video_path, "", "Path to video file (omit for camera).");
ABSL_FLAG(int, interframe_delay, 20, "Delay (ms) between file input frames.");
ABSL_FLAG(std::vector<spectra::MetricType>, requested_metrics,
          std::vector<spectra::MetricType>(),
          "Comma-separated metrics. Entries from: " + spectra::kAllMetricTypesString);

int main(int argc, char** argv) {
    absl::ParseCommandLine(argc, argv);

    spectra::SmartSpectraConfig config;
    config.api_key = absl::GetFlag(FLAGS_api_key);
    config.requested_metrics = absl::GetFlag(FLAGS_requested_metrics);
    if (config.requested_metrics.empty()) {
        config.requested_metrics = spectra::SmartSpectraConfig::DefaultSupportedMetrics();
    }

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

    smart_spectra.SetOnValidationStatusChanged(
        [have_last_status = false,
         last_code = spectra::ValidationCode::kOk,
         last_hint = std::string{}](const spectra::ValidationStatus& status, int64_t) mutable {
            if (have_last_status &&
                status.code == last_code &&
                status.hint == last_hint) {
                return;
            }
            have_last_status = true;
            last_code = status.code;
            last_hint = status.hint;
            std::cout << "Validation [" << status.code
                      << "]: " << status.hint << '\n';
        }
    );

    const std::string input_video_path = absl::GetFlag(FLAGS_input_video_path);
    const auto source_error = input_video_path.empty()
        ? smart_spectra.UseCamera().Build()
        : smart_spectra.UseFile(input_video_path)
              .SetInterframeDelay(absl::GetFlag(FLAGS_interframe_delay)).Build();
    if (!source_error.ok()) {
        std::cerr << "SmartSpectra input source setup failed: "
                  << source_error.message << '\n';
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
        return EXIT_FAILURE;
    }

    std::cout << "Done.\n";
    return smart_spectra.GetStatus() == spectra::ProcessingStatus::kError
        ? EXIT_FAILURE : EXIT_SUCCESS;
}
