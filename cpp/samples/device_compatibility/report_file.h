// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
#pragma once
#include <cstdio>
#include <string>
#include <fcntl.h>
#ifdef _WIN32
#include <io.h>
#include <share.h>
#include <sys/stat.h>
#else
#include <unistd.h>
#endif
namespace compatibility_example {
// Exclusive creation also rejects an existing symlink. Never truncate a prior report.
inline bool WriteSupportReport(const std::string& path, const std::string& report) {
    int descriptor = -1;
#ifdef _WIN32
    if (_sopen_s(&descriptor, path.c_str(), _O_WRONLY | _O_CREAT | _O_EXCL | _O_BINARY,
                 _SH_DENYRW, _S_IREAD | _S_IWRITE) != 0) return false;
    FILE* file = _fdopen(descriptor, "wb");
    if (!file) { _close(descriptor); return false; }
#else
    // Atomic exclusive creation rejects existing files/symlinks; mode 0600 is owner-only.
    // nosemgrep: flawfinder.fopen-1.open-1
    descriptor = open(path.c_str(), O_WRONLY | O_CREAT | O_EXCL, 0600);
    if (descriptor < 0) return false;
    FILE* file = fdopen(descriptor, "wb");
    if (!file) { close(descriptor); return false; }
#endif
    const bool written = std::fwrite(report.data(), 1, report.size(), file) == report.size();
    const bool closed = std::fclose(file) == 0;
    return written && closed;
}
}  // namespace compatibility_example
