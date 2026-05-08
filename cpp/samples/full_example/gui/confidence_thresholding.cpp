// confidence_thresholding.cpp
// Copyright (C) 2024-2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary


// === standard library includes (if any) ===
#include <cmath>
// === third-party includes (if any) ===
// === local includes (if any) ===
#include "confidence_thresholding.hpp"

namespace presage::smartspectra::gui {

#define EPSILON 1e-15


bool is_pulse_high_confidence(float snr) {
    if (snr < EPSILON) {
        snr = EPSILON;
    }
    return std::log(snr) >= pulse_log_snr_threshold;
}

bool is_breathing_high_confidence(float snr) {
    if (snr < EPSILON) {
        snr = EPSILON;
    }
    return std::log(snr) >= breathing_log_snr_threshold;
}

bool is_breathing_rate_high_confidence(float snr, float rate) {
    if (snr < EPSILON) {
        snr = EPSILON;
    }
    return std::log(snr) >= breathing_log_snr_threshold &&
           rate >= min_supported_breathing_rate &&
           rate <= max_supported_breathing_rate;
}

bool is_edge_pulse_high_confidence(float confidence) {
    return confidence >= edge_pulse_confidence_threshold;
}

} // namespace presage::smartspectra::gui
