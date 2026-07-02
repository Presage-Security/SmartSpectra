// CheckupView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import AVFoundation
import SmartSpectra

struct CheckupView: View {
    private let sdk = SmartSpectraSDK.shared
    @State private var edgePulseRateBuffer: [MeasurementWithConfidence] = []
    @State private var edgeArterialPressureBuffer: [MeasurementWithConfidence] = []
    @State private var edgeBreathingRateBuffer: [MeasurementWithConfidence] = []
    // EDA trace is a plain Measurement (value + timestamp, no confidence). `SmartSpectra.` qualifies it
    // to avoid clashing with Foundation's `Measurement`.
    @State private var edgeEdaBuffer: [SmartSpectra.Measurement] = []

    // Set the initial camera position. Can be set to .front or .back. Defaults to .front
    @State private var cameraPosition: AVCaptureDevice.Position = .front
    // Cardio measurements (pulse rate, arterial pressure trace, HRV).
    @State private var cardioMeasurementsEnabled: Bool = false
    // Face metrics (landmarks, blinking, talking, expressions). Contact support for compatible custom bundles.
    @State private var faceMetricsEnabled: Bool = false
    // EDA (electrodermal activity) trace.
    @State private var edaMeasurementsEnabled: Bool = false

    // App display configurations
    let isCustomizationEnabled: Bool = true
    let isFaceMeshEnabled: Bool = true

    init() {
        // (Required) Authentication with API key or OAuth
        let apiKey = ProcessInfo.processInfo.environment["SMARTSPECTRA_API_KEY"] ?? "YOUR_API_KEY_HERE"
        sdk.config.apiKey = apiKey

        // (Optional) Camera and metrics configuration via sdk.config
        sdk.config.cameraPosition = cameraPosition
    }

    var body: some View {

        VStack {
            // add smartspectra view
            SmartSpectraView()

            if isCustomizationEnabled {
                // (Optional), example of how to switch camera at runtime
                Button(cameraPosition == .front ? "Switch to Back Camera": "Switch to Front Camera", systemImage: "camera.rotate") {
                    if cameraPosition == .front {
                        cameraPosition = .back
                    } else {
                        cameraPosition = .front
                    }
                    sdk.config.cameraPosition = cameraPosition
                }

                Toggle(isOn: $cardioMeasurementsEnabled) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Cardio Measurements")
                        Text("Pulse rate, arterial pressure trace, and HRV.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .onChange(of: cardioMeasurementsEnabled) { _, enabled in
                    var current = sdk.config.requestedMetrics ?? SmartSpectraConfig.breathingMetrics
                    let cardioMetrics = SmartSpectraConfig.cardioMetrics
                    if enabled {
                        let currentSet = Set(current)
                        for m in cardioMetrics where !currentSet.contains(m) { current.append(m) }
                    } else {
                        let removeSet = Set(cardioMetrics)
                        current.removeAll { removeSet.contains($0) }
                    }
                    sdk.config.requestedMetrics = current
                }

                Toggle(isOn: $faceMetricsEnabled) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Face Metrics")
                        Text("Landmarks, blinking, talking, and expressions.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .onChange(of: faceMetricsEnabled) { _, enabled in
                    var current = sdk.config.requestedMetrics ?? SmartSpectraConfig.breathingMetrics
                    let faceMetrics = SmartSpectraConfig.faceMetrics
                    if enabled {
                        let currentSet = Set(current)
                        for m in faceMetrics where !currentSet.contains(m) { current.append(m) }
                    } else {
                        let removeSet = Set(faceMetrics)
                        current.removeAll { removeSet.contains($0) }
                    }
                    sdk.config.requestedMetrics = current
                }

                Toggle(isOn: $edaMeasurementsEnabled) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("EDA Measurements")
                        Text("Electrodermal activity (EDA) trace.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .onChange(of: edaMeasurementsEnabled) { _, enabled in
                    var current = sdk.config.requestedMetrics ?? SmartSpectraConfig.breathingMetrics
                    let edaMetrics = SmartSpectraConfig.edaMetrics
                    if enabled {
                        let currentSet = Set(current)
                        for m in edaMetrics where !currentSet.contains(m) { current.append(m) }
                    } else {
                        let removeSet = Set(edaMetrics)
                        current.removeAll { removeSet.contains($0) }
                    }
                    sdk.config.requestedMetrics = current
                }
            }


            // Scrolling view to view additional metrics from measurement
            // Show plots when idle (not during active processing)
            if sdk.processingStatus == .idle {
                ScrollView {
                    VStack {
                    if let metrics = sdk.metrics {
                        Section("Metrics") {
                            VStack(spacing: 8) {
                                // Arterial pressure trace sourced from metrics.
                                if !edgeArterialPressureBuffer.isEmpty {
                                    LineChartView(
                                        orderedPairs: edgeArterialPressureBuffer.map { ($0.date, $0.value) },
                                        title: "Arterial Pressure Trace",
                                        yLabel: "Value",
                                        showYTicks: true
                                    )
                                } else {
                                    Text("No arterial pressure data available")
                                        .foregroundStyle(.secondary)
                                }

                                if !edgePulseRateBuffer.isEmpty {
                                    LineChartView(
                                        orderedPairs: edgePulseRateBuffer.map { ($0.date, $0.value) },
                                        title: "Pulse Rate",
                                        yLabel: "BPM",
                                        showYTicks: true
                                    )
                                } else {
                                    Text("No pulse rate data available")
                                        .foregroundStyle(.secondary)
                                }

                                if !edgePulseRateBuffer.isEmpty {
                                    LineChartView(
                                        orderedPairs: edgePulseRateBuffer.map { ($0.date, $0.confidence) },
                                        title: "Pulse Rate Confidence",
                                        yLabel: "Confidence",
                                        showYTicks: true
                                    )
                                } else {
                                    Text("No pulse rate confidence data available")
                                        .foregroundStyle(.secondary)
                                }

                                if !edgeBreathingRateBuffer.isEmpty {
                                    LineChartView(
                                        orderedPairs: edgeBreathingRateBuffer.map { ($0.date, $0.value) },
                                        title: "Breathing Rate",
                                        yLabel: "BPM",
                                        showYTicks: true
                                    )
                                    LineChartView(
                                        orderedPairs: edgeBreathingRateBuffer.map { ($0.date, $0.confidence) },
                                        title: "Breathing Rate Confidence",
                                        yLabel: "Confidence",
                                        showYTicks: true
                                    )
                                } else {
                                    Text("No breathing rate data available")
                                        .foregroundStyle(.secondary)
                                }

                                // EDA trace (only shown when EDA measurements are enabled).
                                if sdk.edaInferenceEnabled {
                                    if !edgeEdaBuffer.isEmpty {
                                        LineChartView(
                                            orderedPairs: edgeEdaBuffer.map { ($0.date, $0.value) },
                                            title: "EDA",
                                            yLabel: "Value",
                                            showYTicks: true
                                        )
                                    } else {
                                        Text("No EDA data available")
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }

                        if metrics.hasFace && !metrics.face.landmarks.isEmpty && isFaceMeshEnabled {
                            // Visual representation of mesh points from metrics
                            if let latestLandmarks = metrics.face.landmarks.last {
                                GeometryReader { geometry in
                                    ZStack {
                                        ForEach(Array(latestLandmarks.value.enumerated()), id: \.offset) { index, landmark in
                                            Circle()
                                                .fill(Color.blue)
                                                .frame(width: 3, height: 3)
                                                .position(x: CGFloat(landmark.x) * geometry.size.width / 1280.0,
                                                        y: CGFloat(landmark.y) * geometry.size.height / 1280.0)
                                        }
                                    }
                                }
                                .aspectRatio(1, contentMode: .fit)
                            }
                        }
                    } else {
                        Section ("Metrics") {
                            ContentUnavailableView(
                                "No Metrics",
                                systemImage: "waveform.path.ecg",
                                description: Text("Metrics will appear here once available")
                            )
                        }
                    }
                }
                }
            } else {
                Spacer()
            }
        }
        .padding()
        .onChange(of: sdk.metrics) { _, value in
            guard sdk.processingStatus == .running, let metrics = value else { return }
            if metrics.hasBreathing && !metrics.breathing.rate.isEmpty {
                edgeBreathingRateBuffer.appendProtoArray(contentsOf: metrics.breathing.rate)
            }
            if metrics.hasCardio {
                let cardio = metrics.cardio
                if !cardio.pulseRate.isEmpty {
                    edgePulseRateBuffer.appendProtoArray(contentsOf: cardio.pulseRate)
                }
                if !cardio.arterialPressureTrace.isEmpty {
                    edgeArterialPressureBuffer.appendProtoArray(contentsOf: cardio.arterialPressureTrace)
                }
            }
            if sdk.edaInferenceEnabled && !metrics.eda.trace.isEmpty {
                edgeEdaBuffer.appendProtoArray(contentsOf: metrics.eda.trace)
            }
        }
        .onChange(of: sdk.processingStatus) { _, status in
            if status == .running {
                edgePulseRateBuffer.removeAll(keepingCapacity: true)
                edgeArterialPressureBuffer.removeAll(keepingCapacity: true)
                edgeBreathingRateBuffer.removeAll(keepingCapacity: true)
                edgeEdaBuffer.removeAll(keepingCapacity: true)
            }
        }
    }
}

// MARK: - Timestamp Helpers

private extension TimeStamped {
    /// Converts the microsecond-since-epoch timestamp to a Foundation `Date`.
    var date: Date { Date(timeIntervalSince1970: Double(timestamp) / 1_000_000.0) }
}

#Preview {
    CheckupView()
}
