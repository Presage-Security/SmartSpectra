// confidence_thresholding.hpp
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary


// === standard library includes (if any) ===
// === third-party includes (if any) ===
// === local includes (if any) ===
#pragma once

namespace presage::smartspectra::gui {

constexpr float pulse_log_snr_threshold = 2.35; // Defined Sept 2024, taken from line 1062 of compute_metrics.py
constexpr float breathing_log_snr_threshold = 1.7; // Defined Oct 2024 (after changing PW =2), taken from line 1115 of compute_metrics.py
constexpr float min_supported_breathing_rate = 8.0;
constexpr float max_supported_breathing_rate = 31.0;

bool is_pulse_high_confidence(float snr);
bool is_breathing_high_confidence(float snr);
bool is_breathing_rate_high_confidence(float snr, float rate);

constexpr float edge_pulse_confidence_threshold = 0.5f;
bool is_edge_pulse_high_confidence(float confidence);



} // namespace presage::smartspectra::gui
