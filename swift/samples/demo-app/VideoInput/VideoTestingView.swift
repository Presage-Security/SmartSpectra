// VideoTestingView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
import AVFoundation
import SwiftUI
import SmartSpectra

/// The app decodes video and submits it through the public custom-input API.
struct VideoTestingView: View {
    private let sdk = SmartSpectraSDK.shared
    @State private var selectedVideoPath = ""
    @State private var selectedTimestampPath = ""
    @State private var playbackTask: Task<Void, Never>?
    @State private var stopping = false
    @State private var playbackError: String?

    var body: some View {
        VStack {
            if let image = sdk.imageOutput {
                Image(uiImage: image).resizable().scaledToFit().frame(maxHeight: 300)
            }
            Text("Status: \(String(describing: sdk.processingStatus))")
            if let validation = sdk.validationStatus { Text(validation.hint) }
            SmartSpectraResultView().environment(\.smartSpectraSDK, sdk)
            if let message = playbackError ?? sdk.error?.message {
                Text(message).foregroundStyle(.red)
            }
            Button(playbackTask == nil ? "Measure Video" : "Stop") {
                if let playbackTask {
                    stopping = true
                    playbackTask.cancel()
                } else {
                    startVideo()
                }
            }
            .disabled(selectedVideoPath.isEmpty || stopping)
            VideoInputControlPanel(
                selectedVideoPath: $selectedVideoPath,
                selectedTimestampPath: $selectedTimestampPath
            )
            .disabled(playbackTask != nil)
            Spacer()
        }
        .padding()
        .onDisappear { playbackTask?.cancel() }
    }

    private func startVideo() {
        let videoURL = URL(fileURLWithPath: selectedVideoPath)
        let timestampPath = selectedTimestampPath
        playbackError = nil
        playbackTask = Task { @MainActor in
            defer {
                playbackTask = nil
                stopping = false
            }
            do {
                // Keep the authentication configured by the demo app.
                let input = try sdk.useCustomInput()
                do {
                    try await sdk.start()
                    // One worker owns the decoder and submits frames serially.
                    let feed = Task.detached {
                        let asset = AVURLAsset(url: videoURL)
                        let tracks = try await asset.loadTracks(withMediaType: .video)
                        guard !tracks.isEmpty else {
                            throw CocoaError(.fileReadCorruptFile, userInfo: [NSLocalizedDescriptionKey: "The file has no video track."])
                        }
                        let reader = try AVAssetReader(asset: asset)
                        let output = AVAssetReaderVideoCompositionOutput(
                            videoTracks: tracks,
                            videoSettings: [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
                        )
                        // Apply display orientation before submitting upright pixels.
                        output.videoComposition = try await AVVideoComposition.videoComposition(withPropertiesOf: asset)
                        guard reader.canAdd(output) else {
                            throw CocoaError(.fileReadCorruptFile, userInfo: [NSLocalizedDescriptionKey: "This video cannot be decoded."])
                        }
                        reader.add(output)
                        // Optional sidecar: one integer millisecond timestamp per frame.
                        let timestamps: [Int64]? = try timestampPath.isEmpty ? nil :
                            String(contentsOfFile: timestampPath, encoding: .utf8)
                                .split(whereSeparator: \.isNewline).map { line in
                                    guard let value = Int64(line.trimmingCharacters(in: .whitespaces)),
                                          value >= 0, value < (Int64.max - 2) / 1_000 else {
                                        throw CocoaError(.fileReadCorruptFile, userInfo: [NSLocalizedDescriptionKey: "Invalid timestamp sidecar value."])
                                    }
                                    return value * 1_000
                                }
                        try Task.checkCancellation()
                        guard reader.startReading() else {
                            throw reader.error ?? CocoaError(.fileReadCorruptFile, userInfo: [NSLocalizedDescriptionKey: "Video decoding did not start."])
                        }
                        defer { reader.cancelReading() }
                        var frameIndex = 0
                        let clock = ContinuousClock()
                        let startedAt = clock.now
                        var firstTimestampUs: Int64?
                        var previousTimestampUs: Int64?
                        while let sample = output.copyNextSampleBuffer() {
                            try Task.checkCancellation()
                            let pts = CMSampleBufferGetPresentationTimeStamp(sample)
                            let scaled = CMTimeConvertScale(pts, timescale: 1_000_000, method: .roundTowardZero)
                            guard scaled.isNumeric, scaled.epoch == 0, scaled.value >= 0,
                                  scaled.value < Int64.max - 2 else {
                                throw CocoaError(.fileReadCorruptFile, userInfo: [NSLocalizedDescriptionKey: "Invalid video presentation timestamp."])
                            }
                            if let timestamps, frameIndex >= timestamps.count {
                                throw CocoaError(.fileReadCorruptFile, userInfo: [NSLocalizedDescriptionKey: "The timestamp sidecar has too few entries."])
                            }
                            let timestampUs = timestamps?[frameIndex] ?? scaled.value
                            if let previousTimestampUs {
                                guard timestampUs > previousTimestampUs else {
                                    throw CocoaError(.fileReadCorruptFile, userInfo: [NSLocalizedDescriptionKey: "Video timestamps must increase."])
                                }
                                guard timestampUs - previousTimestampUs <= 2_000_000 else {
                                    throw CocoaError(.fileReadCorruptFile, userInfo: [NSLocalizedDescriptionKey: "The video has a timestamp gap over two seconds."])
                                }
                            }
                            let first = firstTimestampUs ?? timestampUs
                            firstTimestampUs = first
                            previousTimestampUs = timestampUs
                            // Preserve clip timing without blocking the UI thread.
                            try await clock.sleep(until: startedAt.advanced(by: .microseconds(timestampUs - first)))
                            let result: FrameSubmissionResult
                            if timestamps != nil, let pixels = CMSampleBufferGetImageBuffer(sample) {
                                result = input.sendFrame(pixels, timestampUs: timestampUs)
                            } else {
                                result = input.sendFrame(sample)
                            }
                            // Keep the borrowed buffer alive until submission returns.
                            if case .rejected(let error) = result { throw error }
                            frameIndex += 1
                        }
                        if let error = reader.error { throw error }
                        guard frameIndex > 0 else {
                            throw CocoaError(.fileReadCorruptFile, userInfo: [NSLocalizedDescriptionKey: "The file produced no video frames."])
                        }
                        if let timestamps, frameIndex != timestamps.count {
                            throw CocoaError(.fileReadCorruptFile, userInfo: [NSLocalizedDescriptionKey: "The timestamp sidecar has too many entries."])
                        }
                    }
                    try await withTaskCancellationHandler {
                        try await feed.value
                    } onCancel: {
                        feed.cancel()
                    }
                    try await sdk.stop()
                    try sdk.useCamera()
                } catch {
                    // Await teardown even after cancellation or failed startup.
                    try await sdk.reset()
                    try sdk.useCamera()
                    throw error
                }
            } catch is CancellationError {
                // Stop and leaving the tab both cancel playback.
            } catch {
                playbackError = error.localizedDescription
            }
        }
    }
}
