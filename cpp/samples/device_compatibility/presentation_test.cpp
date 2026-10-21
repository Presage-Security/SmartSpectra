// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
#include "presentation.h"
#include "report_file.h"
#include <filesystem>
#include <chrono>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>
namespace api = presage::smartspectra;
void Require(bool value) { if (!value) throw std::runtime_error("Presentation regression"); }
int main() {
    api::DeviceCompatibilityResult result;
    for (auto verdict : {api::DeviceCompatibilityVerdict::kReady, api::DeviceCompatibilityVerdict::kLimited,
                         api::DeviceCompatibilityVerdict::kBlocked, api::DeviceCompatibilityVerdict::kInconclusive}) {
        result.verdict = verdict;
        Require(compatibility_example::CanProceed(result) == (verdict == api::DeviceCompatibilityVerdict::kReady));
        Require(std::string(compatibility_example::Title(result)).size() > 20);
    }
    result.reason = "Performance below requirements. Reduce the configured workload.";
    result.diagnostics = R"({"sdkVersion":"test","requestedMetrics":[0,10],"observed":14.5})";
    const auto report = compatibility_example::SupportReport(result);
    Require(report == result.diagnostics);
    const auto path = std::filesystem::temp_directory_path() /
        ("readiness-report-test-" + std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()) + ".json");
    Require(compatibility_example::WriteSupportReport(path.string(), report));
    Require(!compatibility_example::WriteSupportReport(path.string(), "replacement"));
    std::ifstream saved(path);
    const std::string contents((std::istreambuf_iterator<char>(saved)), std::istreambuf_iterator<char>());
    saved.close();
    std::filesystem::remove(path);
    Require(contents == report);
    std::cout << report << '\n';
}
