// HeadlessSDKExample.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import SmartSpectra

struct HeadlessSDKExample: View {
    private let sdk = SmartSpectraSDK.shared
    @State private var isVitalMonitoringEnabled: Bool = false
    @State private var showCameraFeed: Bool = false
    @State private var showInsightsChat: Bool = false
    // Hoisted here so its messages survive sheet dismissals. Creating it
    // inside `InsightsChatView` would tie its lifetime to the sheet,
    // wiping chat history on every close.
    @State private var chatViewModel = InsightsChatViewModel()

    init() {
        sdk.config.cameraPosition = .front
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
        VStack(spacing: 20) {
            // Vitals (always at top)
            GroupBox(label: Text("Vitals")) {
                ContinuousVitalsPlotView()
                HStack {
                    Text("Status: \(sdk.validationStatus?.hint ?? "")")
                        .font(.caption)
                    Spacer()
                    Button(isVitalMonitoringEnabled ? "Stop": "Start") {
                        isVitalMonitoringEnabled.toggle()
                        if isVitalMonitoringEnabled {
                            startVitalsMonitoring()
                        } else {
                            stopVitalsMonitoring()
                        }
                    }
                    .disabled(sdk.error?.code == .inputUnavailable && !isVitalMonitoringEnabled)
                    .opacity((sdk.error?.code != .inputUnavailable || isVitalMonitoringEnabled) ? 1.0 : 0.6)
                }
                .padding(.horizontal, 10)

                if let startupError = sdk.error, startupError.code == .inputUnavailable {
                    HStack {
                        Text(startupError.message)
                            .font(.caption)
                            .foregroundStyle(.red)
                        Spacer()
                    }
                    .padding(.horizontal)
                }

                if sdk.processingStatus == .error {
                    HStack {
                        Text(sdk.error?.message ?? "An error occurred during processing.")
                            .font(.caption)
                            .foregroundStyle(.red)
                        Spacer()
                    }
                    .padding(.horizontal)
                }
            }

            // Camera Preview Toggle
            HStack {
                Text("Camera Preview")
                Spacer()
                Toggle("Camera Preview", isOn: $showCameraFeed)
                    .labelsHidden()
                    .onChange(of: showCameraFeed) {
                        sdk.config.imageOutputEnabled = showCameraFeed
                    }
            }
            .padding(.horizontal)

            // Camera Feed (only shows when enabled)
            if showCameraFeed {
                Group {
                    if let image = sdk.imageOutput {
                        Image(uiImage: image)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                    } else {
                        ContentUnavailableView {
                            Label("Camera Feed", systemImage: "camera.fill")
                        } description: {
                            if !isVitalMonitoringEnabled {
                                Text("Start monitoring to see live frames")
                            } else {
                                Text("Starting camera feed...")
                            }
                        }
                    }
                }
                .frame(height: 200)
                .clipShape(.rect(cornerRadius: 8))
            }

            Spacer()
        }
        .padding()
        .onDisappear {
            stopVitalsMonitoring()
        }

        // Insights chat FAB
        Button("Health Insights", systemImage: "bubble.left.and.bubble.right.fill") {
            showInsightsChat = true
        }
        .labelStyle(.iconOnly)
        .font(.system(size: 20, weight: .semibold))
        .foregroundStyle(.white)
        .padding(14)
        .background(Color.accentColor, in: Circle())
        .shadow(color: .black.opacity(0.25), radius: 4, y: 2)
        .padding(.trailing, 20)
        .padding(.bottom, 16)
        .sheet(isPresented: $showInsightsChat) {
            InsightsChatView(viewModel: chatViewModel)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        } // ZStack
    }

    func startVitalsMonitoring() {
        Task {
            do {
                try await sdk.start()
            } catch {
                print("Failed to start vitals monitoring: \(error.localizedDescription)")
            }
        }
    }

    func stopVitalsMonitoring() {
        Task {
            do {
                try await sdk.stop()
            } catch {
                print("Failed to stop vitals monitoring: \(error.localizedDescription)")
            }
        }
    }
}
