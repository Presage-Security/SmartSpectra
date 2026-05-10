// opencv_element_fits.cpp
// Copyright (C) 2024-2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary


// === standard library includes (if any) ===
// === third-party includes (if any) ===
// === local includes (if any) ===
#include "opencv_element_fits.hpp"
namespace presage::smartspectra::gui {

bool CheckThatElementFitsImage(
    const std::string& element_name,
    const cv::Rect2i element_area,
    const cv::Mat& image
) {
    cv::Rect2i image_bounds = cv::Rect2i(0, 0, image.cols, image.rows);
    bool covers = image_bounds.x <= element_area.x && image_bounds.y <= element_area.y &&
                  image_bounds.x + image_bounds.width >= element_area.x + element_area.width &&
                  image_bounds.y + image_bounds.height >= element_area.y + element_area.height;
    (void)element_name;
    return covers;
}

}  // namespace presage::smartspectra::gui
