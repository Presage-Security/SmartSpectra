---
title: Use Case Examples
description: Example SmartSpectra Swift integration patterns for common app use cases.
---

# iOS Use Case Examples

These are intentionally short, partial examples. They are meant to show one pattern at a time, not serve as complete drop-in apps.

## Shared Setup

Most examples assume you already own an SDK instance:

```swift
import SwiftUI
import AVFoundation
import Charts
import SmartSpectra

struct ExampleHostView: View {
    private let sdk = SmartSpectraSDK.shared

    init() {
        sdk.config.apiKey = "YOUR_API_KEY"
    }

    var body: some View {
        Text("Ready")
    }
}
```

## Accessing Face Mesh

Read the latest face landmarks from `sdk.metrics` and render them into your own overlay.
Face landmarks are only populated when face metrics are requested.

```swift
sdk.config.requestedMetrics = SmartSpectraConfig.breathingMetrics + SmartSpectraConfig.faceMetrics

if let latestLandmarks = sdk.metrics?.face.landmarks.last {
    GeometryReader { geometry in
        ZStack {
            ForEach(Array(latestLandmarks.value.enumerated()), id: \.offset) { _, landmark in
                Circle()
                    .fill(.blue)
                    .frame(width: 3, height: 3)
                    .position(
                        x: CGFloat(landmark.x) * geometry.size.width / 1280.0,
                        y: CGFloat(landmark.y) * geometry.size.height / 1280.0
                    )
            }
        }
    }
}
```

## Accessing Metrics

Read the latest metrics directly from the SDK object.
Pulse and HRV values require cardio metrics to be requested.

```swift
sdk.config.requestedMetrics = SmartSpectraConfig.breathingMetrics + SmartSpectraConfig.cardioMetrics

if let metrics = sdk.metrics {
    if let breathingRate = metrics.breathing.rate.last {
        Text("Breathing: \(breathingRate.value.formatted()) RPM")
    }

    if let pulseRate = metrics.cardio.pulseRate.last {
        Text("Pulse: \(pulseRate.value.formatted()) BPM")
    }
}
```

## LineChartView

Use a small reusable chart view for breathing, pulse, or confidence values.

```swift
struct LineChartView: View {
    let orderedPairs: [(time: Date, value: Float)]
    let title: String

    var body: some View {
        VStack(alignment: .leading) {
            Text(title)
                .font(.headline)

            Chart {
                ForEach(orderedPairs, id: \.time) { pair in
                    LineMark(
                        x: .value("Time", pair.time),
                        y: .value("Value", pair.value)
                    )
                }
            }
            .frame(height: 180)
        }
    }
}
```

You can feed it from metrics like this:

```swift
if let breathingSeries = sdk.metrics?.breathing.rate {
    LineChartView(
        orderedPairs: breathingSeries.map {
            (
                time: Date(timeIntervalSince1970: Double($0.timestamp) / 1_000_000.0),
                value: $0.value
            )
        },
        title: "Breathing Rate"
    )
}
```

## Metrics Data Export

Convert the latest metrics into your own export format before writing to disk or uploading.

```swift
struct MetricsExportRow: Codable {
    let timestampUs: Int64
    let breathingRate: Float?
    let pulseRate: Float?
}

if let metrics = sdk.metrics {
    let row = MetricsExportRow(
        timestampUs: metrics.breathing.rate.last?.timestamp
            ?? metrics.cardio.pulseRate.last?.timestamp
            ?? 0,
        breathingRate: metrics.breathing.rate.last?.value,
        pulseRate: metrics.cardio.pulseRate.last?.value
    )

    let jsonData = try JSONEncoder().encode(row)
    let jsonString = String(decoding: jsonData, as: UTF8.self)
    print(jsonString)
}
```

## State Management

Own the SDK once and bind UI directly to its observable state.

```swift
struct MonitoringView: View {
    private let sdk = SmartSpectraSDK.shared

    init() {
        sdk.config.apiKey = "YOUR_API_KEY"
    }

    var body: some View {
        VStack {
            Text("Status: \(String(describing: sdk.processingStatus))")

            if let validationStatus = sdk.validationStatus {
                Text(validationStatus.hint)
            }

            if let error = sdk.error {
                Text(error.message)
                    .foregroundStyle(.red)
            }
        }
    }
}
```

## Camera Handling

Set the camera on the shared config before calling `try await sdk.start()`.

```swift
let sdk = SmartSpectraSDK.shared

sdk.config.apiKey = "YOUR_API_KEY"
sdk.config.cameraPosition = .front
```

If your app needs to use the other camera for a later session, update the shared config before starting again.

```swift
func switchToBackCamera() {
    let sdk = SmartSpectraSDK.shared
    sdk.config.cameraPosition = .back
}
```
