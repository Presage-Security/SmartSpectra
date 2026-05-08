// sample_overlay_types.hpp
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

#pragma once

#include <cstdint>

namespace presage::smartspectra::gui {

struct TraceSample {
    int64_t timestamp = 0;
    float value = 0.0f;
    bool stable = false;
};

struct ConfidenceSample {
    int64_t timestamp = 0;
    float value = 0.0f;
    bool stable = false;
    float confidence = 0.0f;
};

} // namespace presage::smartspectra::gui
