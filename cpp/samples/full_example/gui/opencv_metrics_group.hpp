// opencv_metrics_group.hpp
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary


#pragma once

// === third-party includes ===
#include <opencv2/core.hpp>
// === standard library includes ===
#include <string>
// === local includes ===
#include "opencv_trace_plotter.hpp"
#include "opencv_value_indicator.hpp"
#include "opencv_label.hpp"

namespace presage::smartspectra::gui {

struct MetricsGroup {
    OpenCvTracePlotter trace_plotter;
    OpenCvValueIndicator rate_indicator;
    OpenCvLabel label;
    ConfidenceSample rate;
    OpenCvValueIndicator secondary_rate_indicator;
    OpenCvLabel secondary_rate_label;
    ConfidenceSample secondary_rate;
    bool display_trace = true;
    bool display_rate = true;
    bool display_secondary_rate = false;
    bool rate_is_high_confidence = false;
    bool secondary_rate_is_high_confidence = false;
    const cv::Scalar confident_color;
    const cv::Scalar unconfident_color;

    bool Render(cv::Mat& image);

    static constexpr float no_rate_value_to_display = -1.0f;

    static MetricsGroup Create(
        int x, int y, int trace_width, int trace_height,
        int indicator_width, int label_width,
        const std::string& name,
        cv::Scalar confident_color, cv::Scalar unconfident_color,
        int max_trace_points = 300,
        bool display_rate = true,
        bool display_trace = true,
        const std::string& secondary_name = "",
        int secondary_indicator_width = 0,
        int secondary_label_width = 0
    );
};

} // namespace presage::smartspectra::gui
