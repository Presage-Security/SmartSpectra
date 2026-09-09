---
title: Install the SmartSpectra C++ SDK on Linux
sidebarTitle: Overview
description: Install the SmartSpectra C++ SDK on Debian, Ubuntu, and Linux Mint. Pick the guide that matches your distribution.
---

# Overview

> **Warning — Experimental platform:** Linux support for the SmartSpectra C++
> SDK is experimental. If you have any issues running SmartSpectra,
> [contact Presage support](mailto:support@presagetech.com) for assistance.

Pick the Presage apt repository and suite that match your host:

- [**Ubuntu 22.04 / Mint 21**](ubuntu-22-04.md) — `jammy` suite, `amd64` and `arm64`.
- [**Ubuntu 24.04 / Mint 22**](ubuntu-24-04.md) — `noble` suite, `amd64` and `arm64`.
- [**Debian 13 / Trixie**](debian-13.md) — `trixie` suite, `amd64` and `arm64`.

Each guide is end-to-end: prerequisites, repository setup, a minimal CMake
project, headless-host behavior, and the advanced apt workflows (RC
channel, pinning, uninstall) for that suite.

## Supported Platforms

| Platform | Status | Notes |
| -------- | ------ | ----- |
| Ubuntu 22.04 / Mint 21 (amd64) | Experimental | Debian package available |
| Ubuntu 22.04 / Mint 21 (arm64) | Experimental | Debian package available |
| Ubuntu 24.04 / Mint 22 (amd64) | Experimental | Debian package available |
| Ubuntu 24.04 / Mint 22 (arm64) | Experimental | Debian package available |
| Debian 13 / Trixie (amd64) | Experimental | Debian package available |
| Debian 13 / Trixie (arm64) | Experimental | Debian package available |
| Debian 12 | Not supported | — |
| RHEL 9 / Fedora 41 | Not supported | — |

For platforms marked "Not supported" or anything not listed above, contact
[support@presagetech.com](mailto:support@presagetech.com) if you have a
specific need.
