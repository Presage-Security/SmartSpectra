---
title: Install the SmartSpectra C++ SDK on Linux
sidebarTitle: Overview
description: Install the SmartSpectra C++ SDK on Ubuntu and Linux Mint. Pick the guide that matches your distribution.
---

# Overview

> **Warning — Experimental platform:** Linux support for the SmartSpectra C++
> SDK is experimental. If you have any issues running SmartSpectra,
> [contact Presage support](mailto:support@presagetech.com) for assistance.

The Presage apt repository ships two suites. Pick the guide that matches your
host:

- [**Ubuntu 22.04 / Mint 21**](ubuntu-22-04.md) — `jammy` suite, `amd64` and `arm64`.
- [**Ubuntu 24.04 / Mint 22**](ubuntu-24-04.md) — `noble` suite, `amd64` and `arm64`.

Each guide is end-to-end: prerequisites, repository setup, a minimal CMake
project, the headless-host bootstrap, and the advanced apt workflows (RC
channel, pinning, uninstall) for that suite.

## Supported Platforms

| Platform | Status | Notes |
| -------- | ------ | ----- |
| Ubuntu 22.04 / Mint 21 (amd64) | Experimental | Debian package available |
| Ubuntu 22.04 / Mint 21 (arm64) | Experimental | Debian package available |
| Ubuntu 24.04 / Mint 22 (amd64) | Experimental | Debian package available |
| Ubuntu 24.04 / Mint 22 (arm64) | Experimental | Debian package available |
| Debian 12 | Not supported | — |
| RHEL 9 / Fedora 41 | Not supported | — |

For platforms marked "Not supported" or anything not listed above, contact
[support@presagetech.com](mailto:support@presagetech.com) if you have a
specific need.
