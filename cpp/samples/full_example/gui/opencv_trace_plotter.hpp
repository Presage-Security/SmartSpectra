// opencv_trace_plotter.hpp
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary


// === standard library includes (if any) ===
// === third-party includes (if any) ===
#include <opencv2/core.hpp>
// === standard library includes ===
#include <vector>
// === local includes (if any) ===
#include "sample_overlay_types.hpp"

#pragma once

namespace presage::smartspectra::gui {

class OpenCvTracePlotter {
public:
    OpenCvTracePlotter(int x, int y, int width, int height, int max_points = 300);

    ~OpenCvTracePlotter() = default;

    void UpdateTraceWithSampleRange(const std::vector<TraceSample>& new_values);
    void UpdateTraceWithSampleRange(const std::vector<ConfidenceSample>& new_values);
    void UpdateTraceWithSample(const TraceSample& new_value);
    void UpdateTraceWithSample(const ConfidenceSample& new_value);

    bool Render(
        cv::Mat& image,
        const cv::Scalar& color = cv::Scalar(0, 255, 0)
    );

private:
    void TrimBuffer(bool adjust_overlap_cursor);

    cv::Rect2i plot_area;
    std::vector<TraceSample> buffer;

    const int max_points;

    int last_overlap_area_start = 0;
};

} // presage::smartspectra::gui
