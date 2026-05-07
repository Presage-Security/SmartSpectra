// ContinuousVitalsPlotView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import SmartSpectra

/// Live graph of breathing traces (and cardio / EDA traces when enabled) during
/// continuous mode. Sample-local reimplementation of the v3 SDK view; reads the
/// SDK's public observable surface (`metrics`, `requestedMetrics`).
struct ContinuousVitalsPlotView: View {
    private enum TraceWindow {
        static let breathing = 400
        static let arterialPressure = 400
        static let eda = 400
    }

    @Environment(\.smartSpectraSDK) private var envSDK
    private let injectedSDK: SmartSpectraSDK?
    @State private var edgePulseRate: Int = 0
    @State private var breathingRate: Int = 0
    @State private var bloodPressure: Int = 0
    @State private var breathingTrace: [SmartSpectra.Measurement] = []
    @State private var arterialPressureTrace: [MeasurementWithConfidence] = []
    @State private var edaLevel: Float = 0
    @State private var edaTrace: [SmartSpectra.Measurement] = []

    private var sdk: SmartSpectraSDK { injectedSDK ?? envSDK }

    /// Creates a continuous vitals plot bound to a specific SDK instance.
    ///
    /// - Parameter sdk: Optional SDK instance this view renders against.
    ///   When `nil` (default), the view resolves its SDK from the environment
    ///   via `\.smartSpectraSDK`, which itself defaults to `SmartSpectraSDK.shared`.
    init(sdk: SmartSpectraSDK? = nil) {
        self.injectedSDK = sdk
    }

    var body: some View {
        VStack(spacing: 4) {
            VitalSection(
                title: "Breathing Rate",
                valueText: breathingRate > 0 ? "\(breathingRate) bpm" : "-- bpm",
                icon: "lungs.fill",
                color: .blue
            ) {
                TraceLineView(
                    data: breathingTrace,
                    color: .blue,
                    recentCount: TraceWindow.breathing
                )
            }

            if sdk.cardioMeasurementsEnabled {
                VitalSection(
                    title: "Cardio",
                    valueText: edgePulseRate > 0 ? "\(edgePulseRate) bpm" : "-- bpm",
                    icon: "heart.fill",
                    color: .purple
                ) {
                    TraceLineView(
                        data: arterialPressureTrace,
                        color: .purple,
                        recentCount: TraceWindow.arterialPressure
                    )
                }
            }

            if sdk.edaInferenceEnabled {
                VitalSection(
                    title: "EDA",
                    valueText: edaLevel > 0 ? "\(edaLevel)" : nil, // TODO: Display EDA value once presentation is finalized.
                    icon: "waveform.path.ecg",
                    color: .green
                ) {
                    TraceLineView(
                        data: edaTrace,
                        color: .green,
                        recentCount: TraceWindow.eda
                    )
                }
            }
        }
        .padding(.top, 10)
        .padding(.bottom, 2)
        .padding(.horizontal, 4)
        .environment(\.smartSpectraSDK, sdk)
        .onChange(of: sdk.metrics) { _, metrics in
            guard let metrics else { return }

            if metrics.hasBreathing {
                let breathing = metrics.breathing
                if let latestRate = breathing.rate.last {
                    breathingRate = Int(latestRate.value.rounded())
                }
                if !breathing.upperTrace.isEmpty {
                    breathingTrace.appendProtoArray(contentsOf: breathing.upperTrace)
                    breathingTrace = breathingTrace.suffix(TraceWindow.breathing)
                }
            }

            if sdk.cardioMeasurementsEnabled {
                if metrics.hasCardio {
                    let cardio = metrics.cardio
                    if let latestPulse = cardio.pulseRate.last {
                        edgePulseRate = Int(latestPulse.value.rounded())
                    }
                    if let lastValue = cardio.arterialPressureTrace.last?.value {
                        bloodPressure = Int(lastValue.rounded())
                    }
                    let arterialTrace = cardio.arterialPressureTrace
                    arterialPressureTrace.appendProtoArray(contentsOf: arterialTrace)
                    arterialPressureTrace = arterialPressureTrace.suffix(TraceWindow.arterialPressure)
                }
            } else {
                edgePulseRate = 0
                arterialPressureTrace.removeAll(keepingCapacity: true)
                bloodPressure = 0
            }

            if sdk.edaInferenceEnabled {
                edaLevel = metrics.eda.trace.last?.value ?? 0
                edaTrace = metrics.eda.trace.suffix(TraceWindow.eda)
            } else {
                edaLevel = 0
                edaTrace.removeAll(keepingCapacity: true)
            }
        }
        .onAppear {
            breathingTrace = []
            arterialPressureTrace = []
            edaTrace = []

            edgePulseRate = 0
            breathingRate = 0
            bloodPressure = 0
            edaLevel = 0
        }
        .onDisappear {
            breathingTrace = []
            arterialPressureTrace = []
            edaTrace = []

            edgePulseRate = 0
            breathingRate = 0
            bloodPressure = 0
            edaLevel = 0
        }
    }
}
