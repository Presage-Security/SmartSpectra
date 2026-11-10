// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
#include "presentation.h"
#include <stdexcept>
#include <string>
namespace api = presage::smartspectra;
void Require(bool value) { if (!value) throw std::runtime_error("Presentation regression"); }
int main() {
    api::DeviceReadinessResult result;
    for (auto verdict : {api::DeviceReadinessVerdict::kReady, api::DeviceReadinessVerdict::kLimited,
                         api::DeviceReadinessVerdict::kBlocked, api::DeviceReadinessVerdict::kInconclusive}) {
        result.verdict = verdict;
        Require(readiness_example::CanProceed(result) == (verdict == api::DeviceReadinessVerdict::kReady));
        Require(std::string(readiness_example::Title(result)).size() > 20);
    }
    result.reason = "Performance below requirements. Reduce the configured workload.";
    Require(!result.reason.empty());
}
