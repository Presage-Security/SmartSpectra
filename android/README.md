---
title: Quick Start
description: Get started with the SmartSpectra Android SDK — Kotlin, min SDK 28.
---

# SmartSpectra Android Quickstart

This repo contains two build guides that produce similar user end states:

- [Option 1: API Key](docs/option-1-api-key.md)
- [Option 2: OAuth](docs/option-2-oauth.md)

The only difference in builds is that the API key build gets up and running very fast, but hard-codes your API key. The OAuth build is more suitable for production deployments because it avoids hard-coding your API key.

## Scope

These quickstarts intentionally request only:

- `SmartSpectraConfig.breathingMetrics`
- `SmartSpectraConfig.cardioMetrics`
- `MetricType.EXPRESSIONS`

Please see the detailed documents for additional features.

## Important Implementation Rules

Start by creating a new Android app project named `Cool Vitals` with package name `com.example.coolvitals`.

The Quick Start is intended so that the developer can replace `app/src/main/java/com/example/coolvitals/MainActivity.kt` as a full file.

- Use `SmartSpectraSdk.shared`.
- Use `PreviewView` with `sdk.config.previewSurfaceProvider` for native CameraX preview.
- Disable bitmap preview frames with `sdk.config.imageOutputEnabled = false`.
- Buffer trace samples locally before drawing charts.
- Keep camera permission in the activity flow with `ActivityResultContracts.RequestPermission()`.

## Choose Your Guide

Use [Option 1: API Key](docs/option-1-api-key.md) for the fastest manual setup.

Use [Option 2: OAuth](docs/option-2-oauth.md) if you need OAuth.

## Supported Platforms

| Platform                | Notes                             |
| ----------------------- | --------------------------------- |
| Google Pixel 3 / 3 XL   | Tested on Pixel 9, 10             |
| Samsung Galaxy S21      | Tested on A16, S21, Galaxy Flip 6 |
| Motorola Edge 30 Ultra  |                                   |

For support: contact <support@presagetech.com> or [submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues).
