// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
#pragma once
#include <smartspectra/device_compatibility.h>
namespace compatibility_example {
inline bool CanProceed(const presage::smartspectra::DeviceCompatibilityResult& result) {
    return result.verdict == presage::smartspectra::DeviceCompatibilityVerdict::kReady;
}
inline const char* Title(const presage::smartspectra::DeviceCompatibilityResult& result) {
    using V = presage::smartspectra::DeviceCompatibilityVerdict;
    switch (result.verdict) {
        case V::kReady: return "The configured SDK meets the compatibility requirements.";
        case V::kLimited: return "The configured SDK did not meet the performance requirements.";
        case V::kBlocked: return "A prerequisite prevents assessing compatibility.";
        case V::kInconclusive: return "The check could not establish device compatibility.";
    }
    return "The check could not establish device compatibility.";
}
inline std::string SupportReport(const presage::smartspectra::DeviceCompatibilityResult& result) {
    return result.diagnostics;
}
}
