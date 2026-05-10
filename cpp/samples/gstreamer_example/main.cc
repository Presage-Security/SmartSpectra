// main.cc
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

// gstreamer_example: SmartSpectra with GStreamer video capture pipeline.
//
// The GStreamer pipeline string in this file selects the capture device
// directly, so this sample intentionally does not expose a camera flag.
//
// Usage:
//   ./gstreamer_example --api_key=YOUR_KEY

#include <chrono>
#include <string>

#include <absl/flags/flag.h>
#include <absl/flags/parse.h>
#include <glog/logging.h>
#include <google/protobuf/util/json_util.h>
#include <opencv2/videoio.hpp>

#include <smartspectra/input_source.h>
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/smartspectra_types.h>

namespace spectra = presage::smartspectra;

ABSL_FLAG(std::string, api_key, "", "API key for the Physiology service.");

int main(int argc, char** argv) {
    google::InitGoogleLogging(argv[0]);
    google::SetStderrLogging(google::INFO);
    absl::ParseCommandLine(argc, argv);

    const std::string api_key = absl::GetFlag(FLAGS_api_key);

    // --- Set up SmartSpectra ---
    spectra::SmartSpectraConfig config;
    config.api_key = api_key;
    config.requested_metrics = spectra::SmartSpectraConfig::DefaultSupportedMetrics();

    spectra::SmartSpectra smart_spectra(std::move(config));

    smart_spectra.SetOnMetrics(
        [](const presage::smartspectra::Metrics& m, int64_t ts) {
            std::string json;
            google::protobuf::util::JsonPrintOptions options;
            // can overwhelm log output if whitespace is enabled
            options.add_whitespace = false;
            google::protobuf::util::MessageToJsonString(m, &json, options);
            LOG(INFO) << "Got edge metrics at " << ts << " microseconds: " << json;
        }
    );

    smart_spectra.SetOnError([](const presage::smartspectra::SmartSpectraError& error) {
        LOG(ERROR) << error.FullMessage();
    });

    // Custom frame push — GStreamer pipeline delivers frames externally.
    std::shared_ptr<spectra::CustomInput> input;
    if (const auto err = smart_spectra.UseCustomInput().Build(input); !err.ok()) {
        LOG(ERROR) << "SmartSpectra::UseCustomInput failed: " << err.FullMessage();
        return EXIT_FAILURE;
    }

    // --- Open GStreamer pipeline via OpenCV ---
    std::string gst_pipeline =
        "v4l2src device=/dev/video0 ! image/jpeg,width=1280,height=720,framerate=30/1 "
        "! jpegdec ! videoconvert ! appsink";
    cv::VideoCapture cap(gst_pipeline, cv::CAP_GSTREAMER);
    if (!cap.isOpened()) {
        LOG(ERROR) << "Failed to open GStreamer pipeline: " << gst_pipeline;
        return EXIT_FAILURE;
    }

    // --- Capture loop ---
    if (const auto err = smart_spectra.Start(); !err.ok()) {
        LOG(ERROR) << "SmartSpectra::Start failed: "
                   << err.message;
        return EXIT_FAILURE;
    }

    LOG(INFO) << "Running... (Ctrl+C to stop)";

    cv::Mat frame_bgr;
    while (cap.read(frame_bgr)) {
        // Use steady_clock (CLOCK_MONOTONIC) — system_clock is not monotonic
        // and NTP steps cause kNonMonotonicTimestamp errors.
        int64_t timestamp_us =
            std::chrono::duration_cast<std::chrono::microseconds>(
                std::chrono::steady_clock::now().time_since_epoch())
                .count();

        spectra::FrameBuffer fb{};
        fb.data = frame_bgr.data;
        fb.width = frame_bgr.cols;
        fb.height = frame_bgr.rows;
        fb.stride_bytes = static_cast<int>(frame_bgr.step);
        fb.format = spectra::PixelFormat::kBGR;

        input->Send(fb, timestamp_us);
    }

    if (const auto err = smart_spectra.Stop(); !err.ok()) {
        LOG(ERROR) << "Stop failed: " << err.message;
    }

    LOG(INFO) << "Done.";
    return 0;
}
