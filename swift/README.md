# SmartSpectra Swift Quickstart

This repo contains two build guides that produce similar user end states:

- [OPTION_1_API_KEY.md](docs/OPTION_1_API_KEY.md)
- [OPTION_2_OAUTH.md](docs/OPTION_2_OAUTH.md)

The only difference in builds is that the API key build gets up and running very fast, but hard-codes your API key. The OAuth build is more suitable for production deployments because it avoids hard-coding your API key.

## Scope

These quickstarts intentionally request only:

- `SmartSpectraConfig.breathingMetrics`
- `SmartSpectraConfig.cardioMetrics`
- `MetricType.expressions`

Please see the detailed documents for additional features.

## Important Implementation Rules

Start by creating a new iOS app project named `Cool Vitals`.

The Quick Start is intended so that the developer can replace `Cool Vitals/ContentView.swift` as a full file.

- Import `SwiftUI`, `SmartSpectra`, and `AVFoundation`.
- Use `let sdk = SmartSpectraSDK.shared`.
- Buffer pulse, breathing, arterial pressure, chest, and abdomen samples locally before drawing charts.

## Choose Your Guide

Use [OPTION_1_API_KEY.md](docs/OPTION_1_API_KEY.md) for the fastest manual setup.

Use [OPTION_2_OAUTH.md](docs/OPTION_2_OAUTH.md) if you need OAuth.
