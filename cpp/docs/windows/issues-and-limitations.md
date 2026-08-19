---
title: Issues and Limitations
description: Known issues and platform limitations for the SmartSpectra C++ SDK on Windows, and the workarounds available for each of them.
---

# Issues and Limitations — Windows

> **Warning — Experimental platform:** Windows support for the SmartSpectra
> C++ SDK is experimental. The items below are known limitations we are actively
> tracking. If you hit something that is not listed here,
> [contact Presage support](mailto:support@presagetech.com) for assistance.

## Camera support

Built-in / integrated cameras (for example, the internal webcam on a laptop) do
**not** currently work with the SmartSpectra C++ SDK on Windows. Use an external
USB webcam instead.

The following camera has been tested and works with the Windows SDK:

| Camera | Status | Notes |
| ------ | ------ | ----- |
| Logitech C920 | Tested — working | External USB webcam |
| Built-in / integrated webcams | Not working | Use an external USB webcam |

## Reporting an issue

If you run into a limitation that is not listed here, let us know so we can
track it: contact [support@presagetech.com](mailto:support@presagetech.com) or
[submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues).
