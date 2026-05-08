// main.cc
// Copyright (C) 2024-2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

// SmartSpectra Dense Facemesh Example
//
// Renders face landmarks as an overlay on the video output.
//
// Usage:
//   ./dense_facemesh --api_key=YOUR_KEY [--camera_device_index=0]

#include <chrono>
#include <iomanip>
#include <iostream>
#include <mutex>
#include <string>
#include <vector>

#include <absl/flags/flag.h>
#include <absl/flags/parse.h>
#include <glog/logging.h>
#include <opencv2/highgui.hpp>
#include <opencv2/imgproc.hpp>

#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/smartspectra_types.h>

namespace spectra = presage::smartspectra;

ABSL_FLAG(std::string, api_key, "", "API key for the Physiology service.");
ABSL_FLAG(int, camera_device_index, 0, "The index of the camera device to use.");

int main(int argc, char** argv) {
    google::InitGoogleLogging(argv[0]);
    google::SetStderrLogging(google::INFO);
    absl::ParseCommandLine(argc, argv);

    const std::string api_key = absl::GetFlag(FLAGS_api_key);
    if (api_key.empty()) {
        std::cout << "Usage: ./dense_facemesh --api_key=YOUR_KEY [--camera_device_index=0]\n";
        std::cout << "Get your API key from: https://physiology.presagetech.com\n";
        return 1;
    }

    std::cout << "Starting SmartSpectra Dense Facemesh...\n";

    // --- Set up SmartSpectra ---
    spectra::SmartSpectraConfig config;
    config.api_key = api_key;
    config.requested_metrics = {spectra::FACE_LANDMARKS};

    spectra::SmartSpectra smart_spectra(std::move(config));

    // Shared face landmarks (protected by mutex since callbacks fire on graph threads)
    std::mutex landmarks_mutex;
    std::vector<cv::Point2f> face_landmarks;

    // Latest display frame from OnVideoOutput
    cv::Mat latest_display_frame;
    std::mutex display_frame_mutex;

    smart_spectra.SetOnMetrics(
        [&landmarks_mutex, &face_landmarks](const spectra::Metrics& metrics, int64_t) {
            if (!metrics.has_face() || metrics.face().landmarks().empty()) return;

            const auto& latest = *metrics.face().landmarks().rbegin();

            auto tp = std::chrono::system_clock::time_point(
                std::chrono::microseconds(latest.timestamp()));
            auto tt = std::chrono::system_clock::to_time_t(tp);
            auto us = latest.timestamp() % 1000000;
            std::cout << "Face landmarks at time: "
                      << std::put_time(std::localtime(&tt), "%H:%M:%S")
                      << '.' << std::setfill('0') << std::setw(3) << (us / 1000)
                      << "  count=" << latest.value_size()
                      << "  stable=" << (latest.stable() ? "yes" : "no")
                      << "  reset=" << (latest.reset() ? "yes" : "no")
                      << '\n';

            std::vector<cv::Point2f> pts;
            pts.reserve(latest.value_size());
            for (int i = 0; i < latest.value_size(); ++i) {
                pts.emplace_back(latest.value(i).x(), latest.value(i).y());
            }

            std::lock_guard<std::mutex> lock(landmarks_mutex);
            face_landmarks = std::move(pts);
        });

    smart_spectra.SetOnValidationStatusChanged(
        [](const spectra::ValidationStatus& vs, int64_t) {
            std::cout << "Validation status: " << vs.code
                      << " (" << vs.hint << ")\n";
        });

    smart_spectra.SetOnError([](const spectra::SmartSpectraError& error) {
        LOG(ERROR) << "[error] code=" << static_cast<int>(error.code)
                   << " " << error.message;
    });

    // Video output callback — display frames come from the graph.
    smart_spectra.SetOnVideoOutput(
        [&latest_display_frame, &display_frame_mutex](
            const spectra::FrameBuffer& fb, int64_t) {
            cv::Mat rgb(fb.height, fb.width, CV_8UC3,
                        const_cast<uint8_t*>(fb.data), fb.stride_bytes);
            cv::Mat bgr;
            cv::cvtColor(rgb, bgr, cv::COLOR_RGB2BGR);
            std::lock_guard<std::mutex> lock(display_frame_mutex);
            latest_display_frame = bgr;
        });

    // Use the requested camera device (default 0, 1280x720).
    const auto source_error =
        smart_spectra.UseCamera(absl::GetFlag(FLAGS_camera_device_index))
            .SetResolution(1280, 720)
            .Build();
    if (!source_error.ok()) {
        LOG(ERROR) << "SmartSpectra::UseCamera failed: " << source_error.message;
        return 1;
    }

    // --- Capture + render loop ---
    if (const auto err = smart_spectra.Start(); !err.ok()) {
        LOG(ERROR) << "SmartSpectra::Start failed: "
                   << err.message;
        return 1;
    }

    std::cout << "Ready! Press 'q' or ESC to quit.\n";

    while (true) {
        cv::Mat frame;
        {
            std::lock_guard<std::mutex> lock(display_frame_mutex);
            frame = latest_display_frame.clone();
        }

        if (!frame.empty()) {
            // Draw landmarks on the display frame, then clear so stale points
            // don't persist when the face leaves the frame.
            {
                std::lock_guard<std::mutex> lock(landmarks_mutex);
                const cv::Scalar color(0, 255, 0);
                for (const auto& pt : face_landmarks) {
                    cv::circle(frame, pt, 2, color);
                }
                face_landmarks.clear();
            }

            cv::imshow("SmartSpectra (face mesh overlay)", frame);
        }

        char key = cv::waitKey(16) & 0xFF;  // ~60fps display
        if (key == 'q' || key == 27) break;
    }

    if (const auto err = smart_spectra.Stop(); !err.ok()) {
        LOG(ERROR) << "Stop failed: " << err.message;
    }

    cv::destroyAllWindows();
    std::cout << "Done!\n";
    return 0;
}
