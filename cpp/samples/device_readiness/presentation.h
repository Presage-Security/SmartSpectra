// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
#pragma once
#include <smartspectra/device_readiness.h>
namespace readiness_example {
inline bool CanProceed(const presage::smartspectra::DeviceReadinessResult& result) {
    return result.verdict == presage::smartspectra::DeviceReadinessVerdict::kReady;
}
inline const char* Title(const presage::smartspectra::DeviceReadinessResult& result) {
    using V = presage::smartspectra::DeviceReadinessVerdict;
    switch (result.verdict) {
        case V::kReady: return "The device meets the configured SDK readiness requirements.";
        case V::kLimited: return "The configured SDK did not meet the performance requirements.";
        case V::kBlocked: return "A prerequisite prevents assessing readiness.";
        case V::kInconclusive: return "The check could not establish device readiness.";
    }
    return "The check could not establish device readiness.";
}
}
