// Copyright © 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import SmartSpectra

/// Each opening discovers a fresh snapshot. Selection belongs to the SDK,
/// so navigating between demo screens does not replace the chosen camera.
struct CameraSelectionButton: View {
    var sdk: SmartSpectraSDK = .shared
    @State private var showingPicker = false

    var body: some View {
        Button("Select Camera", systemImage: "camera.rotate") {
            showingPicker = true
        }
        .accessibilityIdentifier("selectCamera")
        .disabled(sdk.processingStatus != .idle && sdk.processingStatus != .error)
        .sheet(isPresented: $showingPicker) {
            CameraSelectionSheet(sdk: sdk)
        }
    }
}

private struct CameraSelectionSheet: View {
    let sdk: SmartSpectraSDK
    @Environment(\.dismiss) private var dismiss
    @State private var cameras: [CameraInfo] = []
    @State private var errorMessage: String?
    @State private var loading = true
    @State private var refreshGeneration = 0

    var body: some View {
        NavigationStack {
            List {
                if loading {
                    ProgressView("Finding cameras…")
                }
                if let errorMessage {
                    Text(errorMessage).foregroundStyle(.red)
                        .accessibilityIdentifier("cameraPickerError")
                }
                if !loading && cameras.isEmpty && errorMessage == nil {
                    Text("No cameras found. Check camera permission, connect a camera, then refresh.")
                        .accessibilityIdentifier("cameraPickerEmpty")
                }
                if !loading && sdk.processingStatus != .idle {
                    Text("Stop processing before selecting a camera.")
                }
                Section {
                    Button("Default") { select(.default) }
                        .accessibilityIdentifier("cameraDefault")
                    ForEach(cameras, id: \.id) { camera in
                        Button { select(.byId(camera.id)) } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(camera.name ?? "Camera")
                                Text("\(facingLabel(camera.facing)) · \(lensLabel(camera.lensType)) · ID: \(camera.id)")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                .disabled(loading || sdk.processingStatus != .idle)
            }
            .navigationTitle("Select Camera")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button("Refresh") { refreshGeneration += 1 }
                        .accessibilityIdentifier("cameraRefresh")
                        .disabled(loading || (sdk.processingStatus != .idle && sdk.processingStatus != .error))
                }
            }
            .task(id: refreshGeneration) { await refresh() }
        }
    }

    @MainActor
    private func refresh() async {
        loading = true
        errorMessage = nil
        cameras = []
        defer { loading = false }
        do {
            // reset also handles failures before a processing session exists.
            if sdk.processingStatus == .error { try await sdk.reset() }
            try Task.checkCancellation()
            cameras = try SmartSpectraSDK.availableCameras()
        } catch is CancellationError {
            // Closing the sheet cancels its discovery task.
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func select(_ selection: CameraSelection) {
        do {
            try sdk.useCamera(selection)
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func lensLabel(_ lens: CameraLensType) -> String {
        switch lens {
        case .wideAngle: "Wide-angle"
        case .ultraWide: "Ultra-wide"
        case .telephoto: "Telephoto"
        case .unknown: "Unknown lens"
        }
    }

    private func facingLabel(_ facing: CameraFacing) -> String {
        switch facing {
        case .front: "Front"
        case .back: "Back"
        case .unknown: "Unknown facing"
        }
    }
}
