// main.cc
// Copyright (C) 2024-2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

// full_example: Feature-rich SmartSpectra desktop sample.
//
// Demonstrates SmartSpectra with HUD overlay, metric plotters,
// camera tuning, video file playback, metrics saving, and keyboard controls.
//
// Usage:
//   ./full_example --api_key=YOUR_KEY [--camera_device_index=0] [--input_video_path=video.mp4]

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <absl/flags/flag.h>
#include <absl/flags/parse.h>
#include <absl/flags/usage.h>
#include <absl/strings/str_cat.h>
#include <absl/strings/str_join.h>
#include <google/protobuf/util/json_util.h>
#include <opencv2/highgui.hpp>
#include <opencv2/imgproc.hpp>

#include <smartspectra/messages/metric_types.h>
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/smartspectra_types.h>
#include <smartspectra/input_source.h>

#include "opencv_hud.hpp"
#include "opencv_label.hpp"
#include "opencv_trace_plotter.hpp"

namespace spectra = presage::smartspectra;

spectra::FrameTransform ParseFrameTransform(const std::string& s) {
    if (s == "none" || s == "None" || s == "NONE") return spectra::FrameTransform::kNone;
    if (s == "cw90" || s == "CW90" || s == "clockwise90") return spectra::FrameTransform::kRotate90CW;
    if (s == "ccw90" || s == "CCW90" || s == "counterclockwise90") return spectra::FrameTransform::kRotate90CCW;
    if (s == "rot180" || s == "180" || s == "rotate180") return spectra::FrameTransform::kRotate180;
    if (s == "mirror_h" || s == "mirror_horizontal") return spectra::FrameTransform::kMirrorHorizontal;
    if (s == "mirror_v" || s == "mirror_vertical") return spectra::FrameTransform::kMirrorVertical;
    std::cerr << "Unknown input_transform_mode '" << s << "', using none\n";
    return spectra::FrameTransform::kNone;
}

// region ==================================== CAMERA PARAMETERS =======================================================
ABSL_FLAG(int, camera_device_index, 0, "The index of the camera device to use.");
ABSL_FLAG(int, capture_width_px, 1280, "Capture width in pixels.");
ABSL_FLAG(int, capture_height_px, 720, "Capture height in pixels.");
ABSL_FLAG(int, capture_fps, 30, "Capture frames per second.");
ABSL_FLAG(std::string, input_video_path, "", "Path to video file (omit for camera).");
ABSL_FLAG(std::string, input_video_time_path, "", "Path to timestamp file (ms per line).");
ABSL_FLAG(std::string, input_transform_mode, "none",
          "Spatial transform: none, cw90, ccw90, rot180, mirror_h, mirror_v.");
// endregion ===========================================================================================================
// region ======================== GUI / INTERACTION SETTINGS ==========================================================
ABSL_FLAG(bool, headless, false, "Disable GUI.");
ABSL_FLAG(bool, also_log_to_stderr, false, "Accepted for compatibility; logs are printed to stdout/stderr.");
ABSL_FLAG(int, interframe_delay, 20, "Delay (ms) between frames for cv::waitKey.");
// endregion ===========================================================================================================
// region ======================== GRAPH SETTINGS =====================================================================
ABSL_FLAG(std::vector<presage::smartspectra::MetricType>, requested_metrics,
          std::vector<presage::smartspectra::MetricType>(),
          "Comma-separated metrics. Entries from: " + presage::smartspectra::kAllMetricTypesString);
ABSL_FLAG(int, verbosity, 1, "Verbosity level.");
ABSL_FLAG(std::string, api_key, "", "API key for the Physiology service.");
// endregion ===========================================================================================================
// region ======================== OUTPUT SETTINGS =====================================================================
ABSL_FLAG(bool, save_metrics_to_disk, false, "Save metrics to disk.");
ABSL_FLAG(bool, use_accumulated_stream, false, "Use accumulated output stream for metrics.");
ABSL_FLAG(std::string, output_directory, "out", "Directory for saved metrics.");
ABSL_FLAG(std::string, video_output_directory, "",
          "If set, record the processed video (Motion-JPEG AVI) plus a per-frame "
          "timestamp sidecar into this directory during measurement.");
ABSL_FLAG(bool, enable_hud, true, "Enable HUD overlay.");
// endregion ===========================================================================================================

struct HudLayout {
    int hud_width = 1260;
    int hud_height = 400;
    int hud_left_margin = 10;
    int additional_plotters_width = 910;
};

HudLayout GetHudLayout(bool portrait_mode) {
    if (portrait_mode) {
        return {700, 400, 10, 500};
    }
    return {1260, 400, 10, 910};
}

template <typename T>
std::vector<spectra::gui::TraceSample> ToTraceSamples(
    const google::protobuf::RepeatedPtrField<T>& values) {
    std::vector<spectra::gui::TraceSample> converted;
    converted.reserve(values.size());
    for (const auto& value : values) {
        converted.push_back({value.timestamp(), value.value(), value.stable()});
    }
    return converted;
}

std::vector<spectra::gui::ConfidenceSample> ToConfidenceSamples(
    const google::protobuf::RepeatedPtrField<presage::smartspectra::MeasurementWithConfidence>& values) {
    std::vector<spectra::gui::ConfidenceSample> converted;
    converted.reserve(values.size());
    for (const auto& value : values) {
        converted.push_back({value.timestamp(), value.value(), value.stable(), value.confidence()});
    }
    return converted;
}

spectra::gui::ConfidenceSample ToHrvSample(const presage::smartspectra::Hrv& value) {
    return {
        value.timestamp(),
        static_cast<float>(value.rmssd()),
        value.stable(),
        value.confidence()
    };
}

int main(int argc, char** argv) {
    absl::SetProgramUsageMessage(
        "Run Presage SmartSpectra C++ REST Continuous Example.\n"
        "Hit 'q' to quit.");
    absl::ParseCommandLine(argc, argv);

    int verbosity = absl::GetFlag(FLAGS_verbosity);
    bool headless = absl::GetFlag(FLAGS_headless);
    bool enable_hud = absl::GetFlag(FLAGS_enable_hud) && !headless;
    bool save_metrics = absl::GetFlag(FLAGS_save_metrics_to_disk);
    bool use_accumulated = absl::GetFlag(FLAGS_use_accumulated_stream);
    std::string output_dir = absl::GetFlag(FLAGS_output_directory);

    // --- SmartSpectra config ---
    spectra::SmartSpectraConfig config;
    config.api_key = absl::GetFlag(FLAGS_api_key);

    auto requested = absl::GetFlag(FLAGS_requested_metrics);
    if (requested.empty()) {
        config.requested_metrics = spectra::SmartSpectraConfig::DefaultSupportedMetrics();
    } else {
        config.requested_metrics = std::move(requested);
    }
    config.enable_accumulated_output = use_accumulated && save_metrics;

    // Optional: record the processed video via the SDK's built-in Motion-JPEG
    // AVI writer (+ timestamp sidecar) for later replay through --input_video_path.
    if (const std::string rec_dir = absl::GetFlag(FLAGS_video_output_directory);
        !rec_dir.empty()) {
        config.video_output_directory = rec_dir;
    }

    // Capture before config is moved into SmartSpectra; the HUD layout and the
    // OnMetrics gate both key off whether EDA was requested at config time.
    const bool eda_requested =
        std::find(config.requested_metrics.begin(),
                  config.requested_metrics.end(),
                  spectra::MetricType::EDA_TRACE) != config.requested_metrics.end();

    spectra::SmartSpectra smart_spectra(std::move(config));

    std::cout << "SmartSpectra version: " << spectra::SmartSpectra::version << '\n';

    auto frame_transform = ParseFrameTransform(absl::GetFlag(FLAGS_input_transform_mode));

    // --- HUD + plotter setup ---
    // Portrait mode when using 90° rotation (camera mounted sideways).
    bool portrait_mode = (frame_transform == spectra::FrameTransform::kRotate90CW ||
                          frame_transform == spectra::FrameTransform::kRotate90CCW);
    HudLayout layout = GetHudLayout(portrait_mode);

    spectra::gui::OpenCvHud hud(layout.hud_left_margin, 0, layout.hud_width, layout.hud_height,
                                eda_requested);

    const int plot_h = 100, plot_label_w = 150, step = 110, label_off = 10;
    const int label_x = layout.hud_left_margin + layout.additional_plotters_width + label_off;

    auto make_plotter = [&](int y) {
        return spectra::gui::OpenCvTracePlotter(layout.hud_left_margin, y, layout.additional_plotters_width, plot_h);
    };
    auto make_label = [&](const std::string& text, int y) {
        return spectra::gui::OpenCvLabel(label_x, y, plot_label_w, plot_h, text);
    };

    int plotter_y = 450 + step;
    spectra::gui::OpenCvTracePlotter abdomen_plotter = make_plotter(plotter_y);
    spectra::gui::OpenCvLabel abdomen_label = make_label("Breathing (Abdomen)", plotter_y);
    plotter_y += step;
    spectra::gui::OpenCvTracePlotter glute_plotter = make_plotter(plotter_y);
    spectra::gui::OpenCvLabel glute_label = make_label("Micromotion (Glutes)", plotter_y);
    plotter_y += step;
    spectra::gui::OpenCvTracePlotter knee_plotter = make_plotter(plotter_y);
    spectra::gui::OpenCvLabel knee_label = make_label("Micromotion (Knees)", plotter_y);

    presage::smartspectra::Metrics accumulated_metrics;
    std::atomic<spectra::ValidationCode> last_status{spectra::ValidationCode::kOk};

    // Protects hud, plotters, and accumulated_metrics.
    // Callbacks fire on graph scheduler threads; the main loop renders on the main thread.
    std::mutex hud_mutex;

    // Validation hint text, updated from the callback thread and rendered on
    // the frame each iteration of the main loop. Protected by hud_mutex
    // (held briefly under copy in the render path).
    std::string last_validation_hint;

    // --- Callbacks ---
    smart_spectra.SetOnValidationStatusChanged(
        [&last_status, &last_validation_hint, &hud_mutex, verbosity](
                const spectra::ValidationStatus& vs, int64_t ts) {
            auto previous = last_status.exchange(vs.code);
            {
                std::lock_guard<std::mutex> lock(hud_mutex);
                last_validation_hint = vs.hint;
            }
            if (verbosity > 0 && previous != vs.code) {
                std::cout << "Validation status: " << vs.code
                          << " (" << vs.hint << ")"
                          << " at " << ts << std::endl;
            }
        });

    smart_spectra.SetOnMetrics(
        [&hud, &hud_mutex, &accumulated_metrics, &abdomen_plotter,
         &glute_plotter, &knee_plotter,
         enable_hud, save_metrics, use_accumulated, portrait_mode, eda_requested, verbosity](
            const presage::smartspectra::Metrics& metrics, int64_t) {
            {
                std::lock_guard<std::mutex> lock(hud_mutex);
                if (save_metrics && !use_accumulated) {
                    accumulated_metrics.MergeFrom(metrics);
                }
                if (enable_hud) {
                    hud.UpdateWithEdgeCardio(
                        ToTraceSamples(metrics.cardio().arterial_pressure_trace()),
                        ToConfidenceSamples(metrics.cardio().pulse_rate())
                    );
                    hud.UpdateWithEdgeBreathing(
                        ToTraceSamples(metrics.breathing().upper_trace()),
                        ToTraceSamples(metrics.breathing().lower_trace()),
                        ToConfidenceSamples(metrics.breathing().rate())
                    );
                    if (!metrics.cardio().hrv().empty()) {
                        hud.UpdateWithEdgeHrv(ToHrvSample(*metrics.cardio().hrv().rbegin()));
                    }
                    if (eda_requested) {
                        // Gated on the requested-metrics config at startup, not the
                        // per-frame proto contents — the HUD only has an EDA row to
                        // update when EDA was requested.
                        hud.UpdateWithEdgeEda(ToTraceSamples(metrics.eda().trace()));
                    }
                }
                if (portrait_mode) {
                    if (!metrics.breathing().lower_trace().empty())
                        abdomen_plotter.UpdateTraceWithSample(
                            ToTraceSamples(metrics.breathing().lower_trace()).back());
                    if (!metrics.micromotion().glutes().empty())
                        glute_plotter.UpdateTraceWithSample(
                            ToTraceSamples(metrics.micromotion().glutes()).back());
                    if (!metrics.micromotion().knees().empty())
                        knee_plotter.UpdateTraceWithSample(
                            ToTraceSamples(metrics.micromotion().knees()).back());
                }
            }
            if (verbosity > 1) {
                std::string json;
                google::protobuf::util::JsonPrintOptions opts;
                opts.always_print_fields_with_no_presence = true;
                if (verbosity < 4) opts.add_whitespace = false;
                google::protobuf::util::MessageToJsonString(metrics, &json, opts);
                std::cout << "Metrics";
                if (verbosity > 2) std::cout << ": " << json;
                std::cout << '\n';
            }
        });

    if (save_metrics && use_accumulated) {
        smart_spectra.SetOnAccumulatedMetrics(
            [&output_dir, verbosity](const presage::smartspectra::Metrics& metrics, int64_t) {
                std::filesystem::create_directories(output_dir);
                auto path = std::filesystem::path(output_dir) / "accumulated_metrics.json";
                std::string json;
                google::protobuf::util::JsonPrintOptions opts;
                opts.always_print_fields_with_no_presence = true;
                if (verbosity < 4) opts.add_whitespace = false;
                google::protobuf::util::MessageToJsonString(metrics, &json, opts);
                std::ofstream(path) << json;
                if (verbosity > 0)
                    std::cout << "Saved accumulated metrics to: " << path.string() << '\n';
            });
    }

    smart_spectra.SetOnError([](const presage::smartspectra::SmartSpectraError& error) {
        std::cerr << error.FullMessage() << '\n';
    });

    // --- Display frame buffer (populated by OnVideoOutput callback for both modes) ---
    cv::Mat latest_display_frame;
    std::mutex display_frame_mutex;

    // All sources are push-based. Display frames come from OnVideoOutput callback.
    smart_spectra.SetOnVideoOutput(
        [&latest_display_frame, &display_frame_mutex](
            const spectra::FrameBuffer& fb, int64_t) {
            // Convert RGB FrameBuffer to BGR cv::Mat for display
            cv::Mat rgb(fb.height, fb.width, CV_8UC3,
                        const_cast<uint8_t*>(fb.data), fb.stride_bytes);
            cv::Mat bgr;
            cv::cvtColor(rgb, bgr, cv::COLOR_RGB2BGR);
            std::lock_guard<std::mutex> lock(display_frame_mutex);
            latest_display_frame = bgr;
        });

    // --- Video source (configure before Start) ---
    std::string video_path = absl::GetFlag(FLAGS_input_video_path);

    if (video_path.empty()) {
        const auto source_error =
            smart_spectra.UseCamera(absl::GetFlag(FLAGS_camera_device_index))
                .SetResolution(absl::GetFlag(FLAGS_capture_width_px),
                               absl::GetFlag(FLAGS_capture_height_px))
                .SetFps(absl::GetFlag(FLAGS_capture_fps))
                .SetFrameTransform(frame_transform)
                .Build();
        if (!source_error.ok()) {
            std::cerr << (source_error.message.empty()
                              ? "No camera available on this platform."
                              : source_error.message) << '\n';
            return EXIT_FAILURE;
        }
    } else {
        const auto source_error =
            smart_spectra.UseFile(video_path)
                .SetTimestamps(absl::GetFlag(FLAGS_input_video_time_path))
                .SetInterframeDelay(absl::GetFlag(FLAGS_interframe_delay))
                .SetFrameTransform(frame_transform)
                .Build();
        if (!source_error.ok()) {
            std::cerr << (source_error.message.empty()
                              ? "Failed to open video file: " + video_path
                              : source_error.message) << '\n';
            return EXIT_FAILURE;
        }
    }

    // --- Start (Initialize + begin session) ---
    if (const auto err = smart_spectra.Start(); !err.ok()) {
        std::cerr << err.FullMessage() << '\n';
        return EXIT_FAILURE;
    }

    if (!headless) {
        std::cout << "Press 'q' to quit.\n";
    }

    // --- Frame loop ---
    auto log_render_err = [](bool ok, const char* what) {
        if (!ok) std::cerr << what << " render skipped\n";
    };
    const auto edge_color = cv::Scalar(0, 165, 255);
    int interframe_delay = absl::GetFlag(FLAGS_interframe_delay);

    cv::Mat frame;
    bool running = true;
    bool session_started = false;
    bool session_failed = false;
    while (running) {
        const auto status = smart_spectra.GetStatus();
        if (status == spectra::ProcessingStatus::kError) {
            session_failed = true;
            break;
        }

        // Headless mode has no key handler to end the loop. A file source
        // self-stops at EOF (the session returns to kIdle) while a camera stays
        // kRunning until externally stopped, so exit once a started headless
        // session leaves the running state. Without this a
        // `--input_video_path --headless` run spins forever on the retained
        // last frame after the video ends.
        if (headless) {
            if (status == spectra::ProcessingStatus::kRunning) session_started = true;
            const bool terminal =
                session_started && status != spectra::ProcessingStatus::kStarting &&
                status != spectra::ProcessingStatus::kRunning &&
                status != spectra::ProcessingStatus::kStopping;
            if (terminal) {
                running = false;
                break;
            }
        }
        // Display frames come from OnVideoOutput callback for both camera and file
        {
            std::lock_guard<std::mutex> lock(display_frame_mutex);
            frame = latest_display_frame.clone();
        }
        if (frame.empty()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(interframe_delay));
            if (!headless) {
                char key = cv::waitKey(1) & 0xFF;
                if (key == 'q' || key == 27) running = false;
            }
            continue;
        }


        // HUD rendering on display frame (lock shared with callback threads)
        if (enable_hud) {
            std::lock_guard<std::mutex> lock(hud_mutex);
            log_render_err(hud.Render(frame), "HUD");
            if (portrait_mode) {
                log_render_err(abdomen_plotter.Render(frame, edge_color), "Abdomen trace");
                log_render_err(abdomen_label.Render(frame, edge_color), "Abdomen label");
                log_render_err(glute_plotter.Render(frame, edge_color), "Glute trace");
                log_render_err(glute_label.Render(frame, edge_color), "Glute label");
                log_render_err(knee_plotter.Render(frame, edge_color), "Knee trace");
                log_render_err(knee_label.Render(frame, edge_color), "Knee label");
            }
            // Validation banner: code + hint pinned to the top of the frame.
            const auto code = last_status.load(std::memory_order_relaxed);
            const bool ok = (code == spectra::ValidationCode::kOk);
            const std::string banner =
                std::string(spectra::ToString(code)) +
                (last_validation_hint.empty() ? std::string{}
                                              : ": " + last_validation_hint);
            const cv::Scalar bg = ok ? cv::Scalar(30, 80, 30) : cv::Scalar(20, 20, 160);
            const cv::Scalar fg = cv::Scalar(240, 240, 240);
            const int row_h = 28;
            cv::rectangle(frame, cv::Rect(0, 0, frame.cols, row_h),
                          bg, cv::FILLED);
            cv::putText(frame, banner, cv::Point(10, row_h - 9),
                        cv::FONT_HERSHEY_SIMPLEX, 0.55, fg, 1, cv::LINE_AA);
        }

        if (headless) {
            std::this_thread::sleep_for(std::chrono::milliseconds(interframe_delay));
        } else {
            cv::imshow("SmartSpectra", frame);
            char key = cv::waitKey(interframe_delay) & 0xFF;
            switch (key) {
                case 'q': case 27:
                    running = false;
                    break;
            }
        }
    }

    if (!session_failed) {
        if (const auto err = smart_spectra.Stop(); !err.ok()) {
            std::cerr << "Stop failed: " << err.message << '\n';
        }
    }

    // An error may race with a user-requested exit and settle while Stop() is
    // waiting for SDK workers, so inspect the final state before choosing the
    // process exit code.
    session_failed =
        session_failed || smart_spectra.GetStatus() == spectra::ProcessingStatus::kError;

    // Save accumulated metrics (non-accumulated-stream mode)
    if (save_metrics && !use_accumulated) {
        std::filesystem::create_directories(output_dir);
        auto path = std::filesystem::path(output_dir) / "metrics.json";
        std::string json;
        google::protobuf::util::JsonPrintOptions opts;
        google::protobuf::util::MessageToJsonString(accumulated_metrics, &json, opts);
        std::ofstream(path) << json;
    }

    if (!headless) cv::destroyAllWindows();
    std::cout << "Done.\n";
    return session_failed ? EXIT_FAILURE : EXIT_SUCCESS;
}
