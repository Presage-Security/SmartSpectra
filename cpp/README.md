---
title: C++ SDK
description: Get started with SmartSpectra C++ across Linux, macOS, and Windows.
---

# SmartSpectra C++ SDK

Cross-platform C++ SDK for measuring vitals and waveform shapes (pulse,
breathing, relative blood pressure, and more) from a camera. Headless by default
with optional preview frames; runs on Linux, macOS, and Windows.

## Supported Platforms

| Platform | Status | Notes |
| --- | --- | --- |
| Ubuntu 22.04 / Mint 21 (amd64) | Experimental | Debian package available |
| Ubuntu 22.04 / Mint 21 (arm64) | Experimental | Debian package available |
| macOS Apple Silicon (14.0+) | Supported | Homebrew package available |
| Windows 10 / 11 (x64) | Experimental | ZIP distribution available |
| Ubuntu 24.04 / Mint 22 | Coming soon | — |
| macOS Intel | Not supported | — |
| Debian 12 | Not supported | — |
| RHEL 9 / Fedora 41 | Not supported | — |

For platforms marked "Not supported" or anything not listed above, contact
[support@presagetech.com](mailto:support@presagetech.com) if you have a
specific need.

## Common Prerequisites

All platforms need:

- **CMake 3.22.1+**
- **C++17 compiler** (GCC, Clang, or MSVC 2022)
- An **API key** from [physiology.presagetech.com](https://physiology.presagetech.com)

The SDK package is self-contained — you do not need to install OpenCV,
protobuf, curl, or OpenSSL separately on any platform.

## Pick your platform

Each guide is self-contained: prerequisites → install → first running build.

- [**Linux Quickstart (Ubuntu/Mint)**](docs/linux.md) — apt-based install, amd64 + arm64
- [**macOS Quickstart**](docs/macos.md) — Homebrew formula, Apple Silicon
- [**Windows Quickstart**](docs/windows.md) — prebuilt ZIP from GitHub Releases

## Scope

The quickstarts intentionally request only the breathing and cardio metric
bundles, which is enough to see live values on the console. See the
[C++ API reference](https://smartspectra.presagetech.com/docs/cpp/api-reference) for
the full `requested_metrics` catalog and custom-input pipeline.

## Going further

Once your first build runs:

- [Configure which metrics to compute](docs/metrics.md)
- [Headless mode](docs/headless-mode.md) — C++ is headless by default; see the guide for video output callbacks
- [Migration guide](docs/migration-guide.md) — upgrading from v1.x or v2.x
- [API reference](https://smartspectra.presagetech.com/docs/cpp/api-reference)

## Bugs & Troubleshooting

- Each platform quickstart above ends with a Troubleshooting section covering platform-specific install, signing, and runtime issues.
- For additional support, contact [support@presagetech.com](mailto:support@presagetech.com) or [submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues).
