// opencv_hud.cpp
// Copyright (C) 2025-2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary


// === standard library includes (if any) ===
// === third-party includes (if any) ===
#include <utility>
// === local includes (if any) ===
#include "opencv_hud.hpp"
#include "opencv_element_fits.hpp"
#include "confidence_thresholding.hpp"


namespace presage::smartspectra::gui {

const int OpenCvHud::top_plot_area_margin = 20;
const int OpenCvHud::bottom_plot_area_margin = 20;
const int OpenCvHud::minimal_plot_area_height = 90;
const int OpenCvHud::indicator_width = 200;
const int OpenCvHud::label_width = 150;
const int OpenCvHud::minimal_plot_area_width = 200;

const int OpenCvHud::minimal_width =
    OpenCvHud::indicator_width + OpenCvHud::minimal_plot_area_width;
const int OpenCvHud::minimal_height =
    OpenCvHud::top_plot_area_margin + OpenCvHud::minimal_plot_area_height + OpenCvHud::bottom_plot_area_margin;


OpenCvHud::OpenCvHud(
    int x, int y, int width, int height,
    int max_trace_points,
    cv::Scalar pulse_confident_color,
    cv::Scalar pulse_unconfident_color,
    cv::Scalar breathing_upper_confident_color,
    cv::Scalar breathing_upper_unconfident_color,
    cv::Scalar breathing_lower_confident_color,
    cv::Scalar breathing_lower_unconfident_color
) :
    width_sufficient(width >= OpenCvHud::minimal_width),
    height_sufficient(height >= OpenCvHud::minimal_height),
    hud_area(x, y, width, height), max_trace_points(max_trace_points) {

    if (width_sufficient && height_sufficient) {
        const int usable_plot_area_height =
            this->hud_area.height - OpenCvHud::top_plot_area_margin - OpenCvHud::bottom_plot_area_margin;
        const int single_trace_height = static_cast<int>(static_cast<float>(usable_plot_area_height) / 3.f - 1.f);
        const float sixth_trace_height = static_cast<float>(usable_plot_area_height) / 6.f;
        // Row 1 (pulse) carries an extra HRV readout alongside the pulse rate.
        // Reserve the same amount of horizontal space on every row so the primary
        // indicator and label columns stay aligned vertically; the other rows just
        // leave the secondary slot empty.
        const int secondary_indicator_width = 100;
        const int secondary_label_width = 70;
        const int trace_width = this->hud_area.width - OpenCvHud::indicator_width - OpenCvHud::label_width
                                - secondary_indicator_width - secondary_label_width;

        int pulse_group_y = this->hud_area.y + static_cast<int>(OpenCvHud::top_plot_area_margin + sixth_trace_height);
        this->pulse_group = std::make_unique<MetricsGroup>(MetricsGroup::Create(
            this->hud_area.x, pulse_group_y, trace_width, single_trace_height,
            OpenCvHud::indicator_width, OpenCvHud::label_width,
            "Pulse (Edge Cardio)",
            std::move(pulse_confident_color), std::move(pulse_unconfident_color),
            this->max_trace_points,
            /*display_rate=*/true, /*display_trace=*/true,
            /*secondary_name=*/"HRV",
            secondary_indicator_width, secondary_label_width
        ));

        int upper_breathing_group_y =
            this->hud_area.y + static_cast<int>(OpenCvHud::top_plot_area_margin + 3 * sixth_trace_height);
        this->upper_breathing_group = std::make_unique<MetricsGroup>(MetricsGroup::Create(
            this->hud_area.x, upper_breathing_group_y, trace_width, single_trace_height,
            OpenCvHud::indicator_width, OpenCvHud::label_width,
            "Breathing (Chest)",
            std::move(breathing_upper_confident_color), std::move(breathing_upper_unconfident_color),
            this->max_trace_points
        ));

        int lower_breathing_group_y =
            this->hud_area.y + static_cast<int>(OpenCvHud::top_plot_area_margin + 5 * sixth_trace_height);
        // The breathing rate is identical to the chest rate, so suppress its
        // indicator on the abdomen row — only the trace is meaningful here.
        this->lower_breathing_group = std::make_unique<MetricsGroup>(MetricsGroup::Create(
            this->hud_area.x, lower_breathing_group_y, trace_width, single_trace_height,
            OpenCvHud::indicator_width, OpenCvHud::label_width,
            "Breathing (Abdomen)",
            std::move(breathing_lower_confident_color), std::move(breathing_lower_unconfident_color),
            this->max_trace_points,
            /*display_rate=*/false
        ));
    }
}

void OpenCvHud::UpdateWithEdgeCardio(const std::vector<TraceSample>& arterial_pressure_trace,
                                     const std::vector<ConfidenceSample>& pulse_rate) {
    if (!this->pulse_group) { return; }
    if (!arterial_pressure_trace.empty()) {
        this->pulse_group->trace_plotter.UpdateTraceWithSampleRange(arterial_pressure_trace);
    }
    if (!pulse_rate.empty()) {
        this->pulse_group->rate = pulse_rate.back();
        this->pulse_group->rate_is_high_confidence =
            is_edge_pulse_high_confidence(this->pulse_group->rate.confidence);
    }
}

void OpenCvHud::UpdateWithEdgeBreathing(const std::vector<TraceSample>& upper_trace,
                                        const std::vector<TraceSample>& lower_trace,
                                        const std::vector<ConfidenceSample>& breathing_rate) {
    if (this->upper_breathing_group && !upper_trace.empty()) {
        this->upper_breathing_group->trace_plotter.UpdateTraceWithSampleRange(upper_trace);
    }
    if (this->lower_breathing_group && !lower_trace.empty()) {
        this->lower_breathing_group->trace_plotter.UpdateTraceWithSampleRange(lower_trace);
    }
    if (!breathing_rate.empty() && this->upper_breathing_group) {
        const auto& latest = breathing_rate.back();
        this->upper_breathing_group->rate = latest;
        this->upper_breathing_group->rate_is_high_confidence =
            is_breathing_high_confidence(latest.confidence);
    }
}

void OpenCvHud::UpdateWithEdgeHrv(const ConfidenceSample& hrv) {
    if (!this->pulse_group) { return; }
    this->pulse_group->secondary_rate = hrv;
    this->pulse_group->secondary_rate_is_high_confidence =
        is_edge_pulse_high_confidence(hrv.confidence);
}

bool OpenCvHud::Render(cv::Mat& image) {
    if (!this->width_sufficient) {
        return false;
    }
    if (!this->height_sufficient) {
        return false;
    }
    if (!CheckThatElementFitsImage("OpenCvHud", this->hud_area, image)) return false;
    if (pulse_group && !pulse_group->Render(image)) return false;
    if (upper_breathing_group && !upper_breathing_group->Render(image)) return false;
    if (lower_breathing_group && !lower_breathing_group->Render(image)) return false;
    return true;
}


} // namespace presage::smartspectra::gui
