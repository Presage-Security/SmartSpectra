// main.cc
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

// insights_example: minimal SmartSpectra sample exercising the insight API.
//
// Renders a pulse waveform and a breathing waveform (with labels) using the
// FullExampleGui library, plus two text fields: a single-line prompt input
// and a wrapped output area that shows the latest insight response. Submit
// a prompt with Enter; quit with 'q' or Esc.

#include <atomic>
#include <chrono>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#include <absl/flags/flag.h>
#include <absl/flags/parse.h>
#include <absl/flags/usage.h>
#include <glog/logging.h>
#include <opencv2/highgui.hpp>
#include <opencv2/imgproc.hpp>

#include <smartspectra/messages/insights.pb.h>
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/smartspectra_types.h>

#include "opencv_label.hpp"
#include "opencv_trace_plotter.hpp"

namespace spectra = presage::smartspectra;

ABSL_FLAG(std::string, api_key, "", "API key for the Physiology service.");
ABSL_FLAG(int, camera_device_index, 0, "Camera device index.");
ABSL_FLAG(int, capture_width_px, 1280, "Capture width in pixels.");
ABSL_FLAG(int, capture_height_px, 720, "Capture height in pixels.");
ABSL_FLAG(int, capture_fps, 30, "Capture frames per second.");
ABSL_FLAG(std::string, input_video_path, "", "Optional video file path (camera otherwise).");
ABSL_FLAG(int, interframe_delay, 30, "cv::waitKey delay per frame (ms).");

namespace {

constexpr int kCanvasWidth = 960;
constexpr int kCanvasHeight = 640;
constexpr int kMargin = 20;
constexpr int kPlotHeight = 110;
constexpr int kLabelWidth = 200;
constexpr int kOutputHeight = 240;
constexpr int kInputHeight = 48;

const cv::Scalar kPulseColor(0, 165, 255);       // orange
const cv::Scalar kBreathingColor(255, 180, 0);   // sky
const cv::Scalar kTextColor(230, 230, 230);
const cv::Scalar kHintColor(140, 140, 140);
const cv::Scalar kBoxColor(60, 60, 60);
const cv::Scalar kFrameColor(110, 110, 110);

template <typename T>
std::vector<spectra::gui::TraceSample> ToTraceSamples(
    const google::protobuf::RepeatedPtrField<T>& values) {
    std::vector<spectra::gui::TraceSample> converted;
    converted.reserve(values.size());
    for (const auto& v : values) {
        converted.push_back({v.timestamp(), v.value(), v.stable()});
    }
    return converted;
}

// Greedy word-wrap using OpenCV text metrics so the output box stays inside
// `max_pixel_width` with a cushion for the left/right padding.
std::vector<std::string> WrapText(const std::string& text,
                                  int max_pixel_width,
                                  int font_face,
                                  double font_scale,
                                  int thickness) {
    std::vector<std::string> lines;
    std::istringstream paragraphs(text);
    std::string paragraph;
    while (std::getline(paragraphs, paragraph)) {
        std::istringstream words(paragraph);
        std::string word;
        std::string current;
        while (words >> word) {
            std::string candidate = current.empty() ? word : current + " " + word;
            int baseline = 0;
            auto size = cv::getTextSize(candidate, font_face, font_scale,
                                        thickness, &baseline);
            if (size.width <= max_pixel_width) {
                current = std::move(candidate);
                continue;
            }
            if (!current.empty()) {
                lines.push_back(std::move(current));
            }
            current = word;
        }
        lines.push_back(std::move(current));
    }
    if (lines.empty()) lines.emplace_back("");
    return lines;
}

void DrawLabeledBox(cv::Mat& canvas, const cv::Rect& rect, const std::string& title) {
    cv::rectangle(canvas, rect, kBoxColor, cv::FILLED);
    cv::rectangle(canvas, rect, kFrameColor, 1);
    if (!title.empty()) {
        cv::putText(canvas, title, {rect.x + 8, rect.y + 18},
                    cv::FONT_HERSHEY_DUPLEX, 0.5, kHintColor, 1, cv::LINE_AA);
    }
}

}  // namespace

int main(int argc, char** argv) {
    google::InitGoogleLogging(argv[0]);
    absl::SetProgramUsageMessage(
        "SmartSpectra insights example. Type a prompt and press Enter to "
        "request an insight; Esc or 'q' quits.");
    absl::ParseCommandLine(argc, argv);

    spectra::SmartSpectraConfig config;
    config.api_key = absl::GetFlag(FLAGS_api_key);
    // Request breathing (defaults) + cardio. InsightSession buffers
    // pulse_rate, HRV, and breathing.rate, so we need those metric groups
    // for the insight payload. ARTERIAL_PRESSURE_TRACE drives the on-screen
    // pulse waveform.
    config.AddMetrics(spectra::SmartSpectraConfig::DefaultSupportedMetrics());
    config.AddMetrics(spectra::SmartSpectraConfig::CardioMetrics());

    spectra::SmartSpectra smart_spectra(std::move(config));

    // Waveform plotters.
    const int plots_top = kMargin;
    const int plots_width = kCanvasWidth - 2 * kMargin - kLabelWidth - kMargin;
    const int pulse_y = plots_top;
    const int breathing_y = pulse_y + kPlotHeight + kMargin;

    // OpenCvLabel auto-scales font from the supplied template text, so passing
    // distinct labels would size them differently. Use a shared 9-char template
    // (length of "breathing") so both labels render at the same scale, then
    // override the actual text at Render time.
    constexpr int kLabelTemplateLength = 9;
    spectra::gui::OpenCvTracePlotter pulse_plotter(kMargin, pulse_y, plots_width, kPlotHeight);
    spectra::gui::OpenCvLabel pulse_label(kMargin + plots_width + kMargin, pulse_y,
                                     kLabelWidth, kPlotHeight,
                                     /*default_text=*/"", kLabelTemplateLength);
    spectra::gui::OpenCvTracePlotter breathing_plotter(kMargin, breathing_y,
                                                  plots_width, kPlotHeight);
    spectra::gui::OpenCvLabel breathing_label(kMargin + plots_width + kMargin, breathing_y,
                                         kLabelWidth, kPlotHeight,
                                         /*default_text=*/"", kLabelTemplateLength);

    std::mutex plot_mutex;
    smart_spectra.SetOnMetrics(
        [&pulse_plotter, &breathing_plotter, &plot_mutex](
            const presage::smartspectra::Metrics& metrics, int64_t) {
            std::lock_guard<std::mutex> lock(plot_mutex);
            // Each metrics callback delivers a range of trace samples — the
            // pulse trace in particular is dense (cardiac waveform at graph
            // rate). Feed the whole range so the plotted curve is smooth;
            // using only the last sample throws away intra-tick points.
            if (!metrics.cardio().arterial_pressure_trace().empty()) {
                pulse_plotter.UpdateTraceWithSampleRange(
                    ToTraceSamples(metrics.cardio().arterial_pressure_trace()));
            }
            if (!metrics.breathing().upper_trace().empty()) {
                breathing_plotter.UpdateTraceWithSampleRange(
                    ToTraceSamples(metrics.breathing().upper_trace()));
            }
        });

    smart_spectra.SetOnError([](const spectra::SmartSpectraError& err) {
        LOG(ERROR) << err.FullMessage();
    });

    // Insight response sink — updated from a background thread.
    std::mutex insight_mutex;
    std::string latest_insight = "Waiting for first insight response...";
    std::atomic<int32_t> last_request_id{-1};

    smart_spectra.SetOnInsight(
        [&insight_mutex, &latest_insight](const presage::smartspectra::Insight& insight) {
            std::string rendered;
            if (insight.has_analysis()) {
                rendered = insight.analysis();
            } else if (insight.has_error()) {
                rendered = "[error] " + insight.error();
            } else {
                rendered = "[empty response]";
            }
            std::lock_guard<std::mutex> lock(insight_mutex);
            latest_insight = std::move(rendered);
        });

    std::string video_path = absl::GetFlag(FLAGS_input_video_path);
    if (video_path.empty()) {
        const auto err = smart_spectra.UseCamera(absl::GetFlag(FLAGS_camera_device_index))
                             .SetResolution(absl::GetFlag(FLAGS_capture_width_px),
                                            absl::GetFlag(FLAGS_capture_height_px))
                             .SetFps(absl::GetFlag(FLAGS_capture_fps))
                             .Build();
        if (!err.ok()) {
            LOG(ERROR) << "Camera setup failed: " << err.FullMessage();
            return EXIT_FAILURE;
        }
    } else {
        const auto err = smart_spectra.UseFile(video_path)
                             .SetInterframeDelay(absl::GetFlag(FLAGS_interframe_delay))
                             .Build();
        if (!err.ok()) {
            LOG(ERROR) << "Video file setup failed: " << err.FullMessage();
            return EXIT_FAILURE;
        }
    }

    if (const auto err = smart_spectra.Start(); !err.ok()) {
        LOG(ERROR) << err.FullMessage();
        return EXIT_FAILURE;
    }

    std::cout << "Type a prompt and press Enter to request an insight. "
                 "Press Esc or 'q' to quit.\n";

    const int interframe_delay = absl::GetFlag(FLAGS_interframe_delay);
    const int output_top = breathing_y + kPlotHeight + kMargin;
    const cv::Rect output_rect(kMargin, output_top,
                               kCanvasWidth - 2 * kMargin, kOutputHeight);
    const int input_top = output_rect.y + output_rect.height + kMargin;
    const cv::Rect input_rect(kMargin, input_top,
                              kCanvasWidth - 2 * kMargin, kInputHeight);

    std::string prompt_buffer;
    bool running = true;
    while (running) {
        cv::Mat canvas(kCanvasHeight, kCanvasWidth, CV_8UC3, cv::Scalar(30, 30, 30));

        {
            std::lock_guard<std::mutex> lock(plot_mutex);
            pulse_plotter.Render(canvas, kPulseColor);
            pulse_label.Render(canvas, "pulse", kPulseColor);
            breathing_plotter.Render(canvas, kBreathingColor);
            breathing_label.Render(canvas, "breathing", kBreathingColor);
        }

        DrawLabeledBox(canvas, output_rect, "Insight response");
        {
            std::lock_guard<std::mutex> lock(insight_mutex);
            auto lines = WrapText(latest_insight,
                                  output_rect.width - 20,
                                  cv::FONT_HERSHEY_DUPLEX, 0.5, 1);
            int y = output_rect.y + 42;
            for (const auto& line : lines) {
                if (y + 20 > output_rect.y + output_rect.height) break;
                cv::putText(canvas, line, {output_rect.x + 10, y},
                            cv::FONT_HERSHEY_DUPLEX, 0.5, kTextColor, 1, cv::LINE_AA);
                y += 20;
            }
        }

        DrawLabeledBox(canvas, input_rect, "Prompt (Enter to send)");
        {
            const std::string prefix = "> ";
            cv::putText(canvas, prefix + prompt_buffer + "_",
                        {input_rect.x + 10, input_rect.y + 38},
                        cv::FONT_HERSHEY_DUPLEX, 0.55, kTextColor, 1, cv::LINE_AA);
        }

        cv::imshow("SmartSpectra Insights", canvas);
        int key = cv::waitKey(interframe_delay);
        if (key < 0) continue;
        key &= 0xFF;
        if (key == 27 || key == 'q') {
            running = false;
        } else if (key == '\n' || key == '\r') {
            if (!prompt_buffer.empty()) {
                int32_t request_id = -1;
                const auto err = smart_spectra.RequestInsight(prompt_buffer, &request_id);
                {
                    std::lock_guard<std::mutex> lock(insight_mutex);
                    if (err.ok()) {
                        last_request_id.store(request_id);
                        latest_insight = "[waiting for response to request #" +
                                         std::to_string(request_id) + "]";
                    } else {
                        latest_insight = "[request failed] " + err.FullMessage();
                    }
                }
                prompt_buffer.clear();
            }
        } else if (key == 8 || key == 127) {  // Backspace / Delete
            if (!prompt_buffer.empty()) prompt_buffer.pop_back();
        } else if (key >= 32 && key < 127) {
            prompt_buffer.push_back(static_cast<char>(key));
        }
    }

    if (const auto err = smart_spectra.Stop(); !err.ok()) {
        LOG(ERROR) << "Stop failed: " << err.FullMessage();
    }
    cv::destroyAllWindows();
    return 0;
}
