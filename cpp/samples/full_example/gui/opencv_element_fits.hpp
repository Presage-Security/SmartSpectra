// opencv_element_fits.hpp
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary


// === standard library includes (if any) ===
// === third-party includes (if any) ===
#include <opencv2/core.hpp>
// === local includes (if any) ===
#include <string>

#pragma once
namespace presage::smartspectra::gui {

bool CheckThatElementFitsImage(
    const std::string& element_name,
    const cv::Rect2i element_area,
    const cv::Mat& image
);

} // namespace presage::smartspectra::gui
