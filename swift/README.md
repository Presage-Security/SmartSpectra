---
title: Swift Quick Start
description: Measure pulse and breathing from the iPhone camera with the SmartSpectra Swift SDK. Requires iOS 17 or later; install, authenticate, and run.
sidebarTitle: Quick Start
---

# SmartSpectra Swift Quickstart

This repo contains two build guides that produce similar user end states:

- [Option 1: API Key](docs/option-1-api-key.md)
- [Option 2: OAuth](docs/option-2-oauth.md)

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

## Logging

SDK log verbosity defaults to warnings and errors only. To change it, set
`logLevel` on the SDK config:

```swift
SmartSpectraSDK.shared.config.logLevel = .info
```

Levels are cumulative — `.debug`, `.info`, `.warning` (default), `.error`,
`.none`. `.debug` cannot restore debug-only statements compiled out of the
release engine binary.

## Choose Your Guide

Use [Option 1: API Key](docs/option-1-api-key.md) for the fastest manual setup.

Use [Option 2: OAuth](docs/option-2-oauth.md) if you need OAuth.

Either way, an AI assistant connected to the [SmartSpectra MCP Server](../docs/mcp-server.md) can do the account side for you — fetch your API key, or register your bundle ID and Apple team ID and download `PresageService-Info.plist`.

## LLM Insights

See [LLM Insights](docs/llm-insights.md) for natural-language analysis of the measured vitals.
