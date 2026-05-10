// opencv_metrics_group.cpp
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary


// === local includes ===
#include "opencv_metrics_group.hpp"
#include "opencv_element_fits.hpp"

namespace presage::smartspectra::gui {

bool MetricsGroup::Render(cv::Mat& image) {
    auto color = this->rate.value == no_rate_value_to_display || this->rate_is_high_confidence ?
                 this->confident_color : this->unconfident_color;
    if (this->display_trace) {
        if (!this->trace_plotter.Render(image, color)) return false;
    }
    if (this->display_rate) {
        if (this->rate.value == no_rate_value_to_display) {
            if (!this->rate_indicator.RenderNA(image, color)) return false;
        } else {
            if (!this->rate_indicator.Render(image, this->rate.value, color)) return false;
        }
    }
    if (!this->label.Render(image, color)) return false;
    if (this->display_secondary_rate) {
        auto secondary_color =
            this->secondary_rate.value == no_rate_value_to_display || this->secondary_rate_is_high_confidence ?
            this->confident_color : this->unconfident_color;
        if (this->secondary_rate.value == no_rate_value_to_display) {
            if (!this->secondary_rate_indicator.RenderNA(image, secondary_color)) return false;
        } else {
            if (!this->secondary_rate_indicator.Render(image, this->secondary_rate.value, secondary_color)) {
                return false;
            }
        }
        if (!this->secondary_rate_label.Render(image, secondary_color)) return false;
    }
    return true;
}

MetricsGroup MetricsGroup::Create(
    int x, int y, int trace_width, int trace_height,
    int indicator_width, int label_width,
    const std::string& name,
    cv::Scalar confident_color, cv::Scalar unconfident_color,
    int max_trace_points,
    bool display_rate,
    bool display_trace,
    const std::string& secondary_name,
    int secondary_indicator_width,
    int secondary_label_width
) {
    const bool display_secondary_rate =
        !secondary_name.empty() && secondary_indicator_width > 0 && secondary_label_width > 0;

    int rate_indicator_x = display_trace ? x + trace_width : x;
    int label_x = display_rate ? rate_indicator_x + indicator_width : rate_indicator_x;
    int secondary_indicator_x = label_x + label_width;
    int secondary_label_x = secondary_indicator_x + secondary_indicator_width;

    ConfidenceSample rate;
    rate.value = no_rate_value_to_display;
    ConfidenceSample secondary_rate;
    secondary_rate.value = no_rate_value_to_display;

    return MetricsGroup{
        OpenCvTracePlotter{x, y, display_trace ? trace_width : 0, trace_height, max_trace_points},
        OpenCvValueIndicator{rate_indicator_x, y + trace_height / 2, indicator_width, trace_height},
        OpenCvLabel{label_x, y, label_width, trace_height, name},
        rate,
        OpenCvValueIndicator{
            display_secondary_rate ? secondary_indicator_x : 0,
            y + trace_height / 2,
            display_secondary_rate ? secondary_indicator_width : 0,
            trace_height
        },
        OpenCvLabel{
            display_secondary_rate ? secondary_label_x : 0,
            y,
            display_secondary_rate ? secondary_label_width : 0,
            trace_height,
            secondary_name
        },
        secondary_rate,
        display_trace, display_rate, display_secondary_rate,
        true, true,
        std::move(confident_color), std::move(unconfident_color)
    };
}

} // namespace presage::smartspectra::gui
