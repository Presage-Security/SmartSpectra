// opencv_value_indicator.hpp
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary


#pragma once

// === standard library includes (if any) ===
// === third-party includes (if any) ===
#include <opencv2/imgproc.hpp>
// === local includes (if any) ===


namespace presage::smartspectra::gui {

class OpenCvValueIndicator {
public:
    OpenCvValueIndicator(int x, int y, int width, int height, int precision_digits = 1);
    ~OpenCvValueIndicator() = default;
    bool Render(cv::Mat& image, float value, cv::Scalar color);
    bool RenderNA(cv::Mat& image, cv::Scalar color);
    static const float min_value;
    static const float max_value;
private:
    cv::Rect2i indicator_area;
    double font_scale;
    cv::Point2i text_origin;
    const int font_face = cv::FONT_HERSHEY_DUPLEX;
    int precision_digits;

};

} // namespace presage::smartspectra::gui
