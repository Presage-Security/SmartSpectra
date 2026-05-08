// opencv_hud.hpp
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary


// === standard library includes (if any) ===
// === standard library includes ===
#include <memory>
#include <vector>
// === third-party includes (if any) ===
#include <opencv2/core.hpp>
// === local includes (if any) ===
#include "opencv_metrics_group.hpp"


#pragma once

namespace presage::smartspectra::gui {

class OpenCvHud {
public:
    OpenCvHud(
        int x, int y, int width, int height,
        int max_trace_points = 300,
        cv::Scalar pulse_confident_color = cv::Scalar(0, 255, 0), // green
        cv::Scalar pulse_unconfident_color = cv::Scalar(0, 0, 255), // red
        cv::Scalar breathing_upper_confident_color = cv::Scalar(255, 255, 0), // cyan
        cv::Scalar breathing_upper_unconfident_color = cv::Scalar(0, 0, 255), // red
        cv::Scalar breathing_lower_confident_color = cv::Scalar(255, 0, 0), // blue
        cv::Scalar breathing_lower_unconfident_color = cv::Scalar(0, 0, 255) // red
    );

    void UpdateWithEdgeCardio(const std::vector<TraceSample>& arterial_pressure_trace,
                              const std::vector<ConfidenceSample>& pulse_rate);
    void UpdateWithEdgeBreathing(const std::vector<TraceSample>& upper_trace,
                                 const std::vector<TraceSample>& lower_trace,
                                 const std::vector<ConfidenceSample>& breathing_rate);
    void UpdateWithEdgeHrv(const ConfidenceSample& hrv);
    bool Render(cv::Mat& image);

    static const int minimal_width; // derived
    static const int minimal_height; // derived

private:
    static const int top_plot_area_margin;
    static const int bottom_plot_area_margin;
    static const int minimal_plot_area_width; // arbitrary / base on visual experimentation
    static const int minimal_plot_area_height; // arbitrary / base on visual experimentation

    static const int indicator_width;
    static const int label_width;

    const int max_trace_points;
    bool width_sufficient = false;
    bool height_sufficient = false;
    cv::Rect2i hud_area;

    std::unique_ptr<MetricsGroup> pulse_group;
    std::unique_ptr<MetricsGroup> upper_breathing_group;
    std::unique_ptr<MetricsGroup> lower_breathing_group;

};

} // namespace presage::smartspectra::gui
