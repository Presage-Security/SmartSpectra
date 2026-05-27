// main.cc
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

// debian-app-example: headless SmartSpectra CLI that ships as a self-contained
// Debian package via this example's own CPack DEB target. Demonstrates the
// public C++ SDK redistribution path: consume the published SmartSpectra Linux
// SDK release tarball via find_package(SmartSpectra), privately bundle the
// runtime under the app's /opt/<name>/lib prefix, and ship a .deb that installs
// on stock Ubuntu without a Presage apt source.
//
// Usage:
//   debian-app-example --api_key=YOUR_KEY \
//                      --input_video_path=/path/to/vitals.mp4 \
//                      --output_json=/tmp/metrics.json
//
// With no arguments, the binary reaches the SDK initialization boundary and
// exits with rc=1 from a controlled error (no API key / no video). This is
// what the CI verify jobs exercise via the rc-class crash classifier — they
// do not validate metric values; metric assertion is for local / HITL use.

#include <fstream>
#include <iostream>
#include <mutex>
#include <string>

#include <absl/flags/flag.h>
#include <absl/flags/parse.h>
#include <absl/flags/usage.h>
#include <google/protobuf/util/json_util.h>

#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/smartspectra_types.h>

namespace spectra = presage::smartspectra;

ABSL_FLAG(std::string, api_key, "", "API key for the Physiology service.");
ABSL_FLAG(std::string, input_video_path, "",
          "Path to vitals video file (required for measurement).");
ABSL_FLAG(std::string, output_json, "",
          "Path to write the final metrics JSON (required for measurement).");

int main(int argc, char** argv) {
    absl::SetProgramUsageMessage(
        "debian-app-example: headless SmartSpectra CLI demonstrating the "
        "Linux C++ SDK redistribution path. Pass --api_key, "
        "--input_video_path, and --output_json to run a measurement; with no "
        "arguments, the binary exercises the SDK init path and exits.");
    absl::ParseCommandLine(argc, argv);

    const std::string api_key = absl::GetFlag(FLAGS_api_key);
    const std::string video_path = absl::GetFlag(FLAGS_input_video_path);
    const std::string output_json_path = absl::GetFlag(FLAGS_output_json);

    spectra::SmartSpectraConfig config;
    config.api_key = api_key;
    config.requested_metrics = spectra::SmartSpectraConfig::DefaultSupportedMetrics();

    // Construct the SDK before the argument check — this exercises the
    // dynamic-load / model-manifest resolution path that the CI rc-class
    // assertion exists to catch (rc=127 if libsmartspectra's DT_NEEDED chain
    // can't be resolved, rc=134/139 if init segfaults).
    spectra::SmartSpectra smart_spectra(std::move(config));

    if (video_path.empty() || output_json_path.empty()) {
        std::cerr << "Usage: debian-app-example --api_key=KEY "
                     "--input_video_path=VIDEO --output_json=PATH\n"
                  << "  No measurement performed: missing required argument.\n";
        return EXIT_FAILURE;
    }

    std::mutex metrics_mutex;
    spectra::Metrics latest_metrics;
    bool metrics_received = false;
    smart_spectra.SetOnMetrics(
        [&](const spectra::Metrics& m, int64_t) {
            std::lock_guard<std::mutex> lock(metrics_mutex);
            latest_metrics = m;
            metrics_received = true;
        });

    smart_spectra.SetOnError([](const spectra::SmartSpectraError& error) {
        std::cerr << error.FullMessage() << '\n';
    });

    if (const auto err = smart_spectra.UseFile(video_path).Build(); !err.ok()) {
        std::cerr << "Video file setup failed: " << err.FullMessage() << '\n';
        return EXIT_FAILURE;
    }

    if (const auto err = smart_spectra.Start(); !err.ok()) {
        std::cerr << err.FullMessage() << '\n';
        return EXIT_FAILURE;
    }

    smart_spectra.WaitUntilComplete();

    if (const auto err = smart_spectra.Stop(); !err.ok()) {
        std::cerr << "Stop failed: " << err.FullMessage() << '\n';
        return EXIT_FAILURE;
    }

    std::string json;
    google::protobuf::util::JsonPrintOptions options;
    options.add_whitespace = true;
    {
        std::lock_guard<std::mutex> lock(metrics_mutex);
        if (!metrics_received) {
            std::cerr << "No metrics callback fired during measurement — "
                         "SDK did not produce results.\n";
            return EXIT_FAILURE;
        }

        const auto& cardio = latest_metrics.cardio();
        const auto& breathing = latest_metrics.breathing();
        if (cardio.pulse_rate_size() == 0 || breathing.rate_size() == 0) {
            std::cerr << "Metrics payload missing pulse and/or breathing samples "
                         "(pulse_rate_size=" << cardio.pulse_rate_size()
                      << ", breathing.rate_size=" << breathing.rate_size() << ").\n";
            return EXIT_FAILURE;
        }
        const float pulse =
            cardio.pulse_rate(cardio.pulse_rate_size() - 1).value();
        const float breath =
            breathing.rate(breathing.rate_size() - 1).value();
        if (pulse <= 0.0f || breath <= 0.0f) {
            std::cerr << "Non-positive vital values (pulse=" << pulse
                      << ", breathing=" << breath << ") — treating as failure.\n";
            return EXIT_FAILURE;
        }

        const auto json_status = google::protobuf::util::MessageToJsonString(
            latest_metrics, &json, options);
        if (!json_status.ok()) {
            std::cerr << "Failed to serialize metrics to JSON: "
                      << json_status.ToString() << '\n';
            return EXIT_FAILURE;
        }
    }

    std::ofstream out(output_json_path);
    if (!out) {
        std::cerr << "Failed to open output JSON file: " << output_json_path << '\n';
        return EXIT_FAILURE;
    }
    out << json;
    out.close();
    if (!out) {
        std::cerr << "Failed to flush/close output JSON file: "
                  << output_json_path << '\n';
        return EXIT_FAILURE;
    }

    std::cout << "Wrote metrics JSON to " << output_json_path << '\n';
    return 0;
}
