// The Swift Programming Language
// https://docs.swift.org/swift-book
import Foundation
import Combine

import PresagePreprocessing
import AVFoundation
import SwiftUI

public typealias MetricsBuffer = Presage_Physiology_MetricsBuffer
public typealias Metrics = Presage_Physiology_Metrics

/// Entry point for interacting with SmartSpectra from Swift.
///
/// Use the ``shared`` singleton to configure the SDK and to access
/// published measurement data. All configuration should be performed before
/// presenting ``SmartSpectraView`` or starting headless processing.
public class SmartSpectraSwiftSDK: ObservableObject {
    public static let shared = SmartSpectraSwiftSDK()
    /// Buffer containing all collected metrics for the current session.
    @Published public var metricsBuffer: MetricsBuffer? {
        didSet {
            if config.smartSpectraMode == .spot {
                //TODO: 10/24/24: Update this for all result once strict results return for continuous
                updateResultText()
            }
        }
    }
    /// Real-time edge metrics emitted while processing is active.
    @Published public var edgeMetrics: Metrics?

    @Published internal var resultText: String = "No Results\n..."
    @Published internal var resultErrorText: String = ""

    internal var config: SmartSpectraSdkConfig = SmartSpectraSdkConfig.shared

    /// Creates the singleton instance.
    ///
    /// - Parameters:
    ///   - apiKey: Optional API key to configure immediately.
    ///   - showFps: Display frames per second overlay when `true`.
    private init(apiKey: String = "", showFps: Bool = false) {
        self.config.apiKey = apiKey
        self.config.showFps = showFps

        // initiate app auth flow
        AuthHandler.shared.startAuthWorkflow { [weak self] error in
            guard let self = self else { return }

            if let error = error {
                print("Authentication failed with error: \(error)")
                self.updateErrorText("Authentication failed. Please try again.")
            } else {
                print("Authentication completed successfully.")
            }
        }

        // Uncomment this to use test server. Use with extreme caution. do not use this in production
        // useTestServer()
    }


    /// Displays the current frame rate while recording.
    /// - Parameter showFps: Set to `true` to overlay FPS in the UI.
    public func setShowFps(_ showFps: Bool) {
        self.config.showFps = showFps
    }

    /// Configures whether the SDK runs in spot or continuous mode.
    /// - Parameter mode: Desired ``SmartSpectraMode`` for new measurements.
    public func setSmartSpectraMode(_ mode: SmartSpectraMode) {
        guard self.config.smartSpectraMode != mode else { return }
        self.config.smartSpectraMode = mode
    }

    /// Sets how long a spot measurement should last.
    /// - Parameter duration: Duration in seconds. Valid range is 20–120.
    public func setMeasurementDuration(_ duration: Double) {
        self.config.measurementDuration = duration
    }

    /// Sets the countdown shown before recording begins.
    /// - Parameter delay: Delay in seconds.
    public func setRecordingDelay(_ delay: Int) {
        self.config.recordingDelay = delay
    }

    /// Chooses which device camera to use.
    /// - Parameter cameraPosition: `.front` or `.back`.
    public func setCameraPosition(_ cameraPosition: AVCaptureDevice.Position) {
        guard self.config.cameraPosition != cameraPosition else { return }
        self.config.cameraPosition = cameraPosition
    }

    /// Shows or hides UI controls on ``SmartSpectraView``.
    public func showControlsInScreeningView(_ showControls: Bool) {
        self.config.showControlsInScreeningView = showControls
    }

    /// Sets the API key used for authentication.
    /// - Parameter apiKey: API key from the Presage developer portal.
    public func setApiKey(_ apiKey: String) {
        self.config.apiKey = apiKey
    }

    /// Controls whether camera preview images are generated and published.
    ///
    /// When disabled, camera frames are still processed for vitals analysis, but no UI preview
    /// images are created, significantly improving performance and reducing memory usage.
    ///
    /// ## Performance Benefits
    /// - Reduces memory pressure by skipping `CVPixelBuffer` to `UIImage` conversion
    /// - Eliminates UI update overhead for better processing performance
    /// - Ideal for server-side processing or background vitals monitoring
    ///
    /// ## Usage Example
    /// ```swift
    /// // Enable image output to show camera feed (default)
    /// SmartSpectraSwiftSDK.shared.setImageOutputEnabled(true)
    ///
    /// // Disable image output for optimal performance
    /// SmartSpectraSwiftSDK.shared.setImageOutputEnabled(false)
    ///
    /// // Important: Reset to enabled when leaving views that disable it
    /// .onDisappear {
    ///     SmartSpectraSwiftSDK.shared.setImageOutputEnabled(true)
    /// }
    /// ```
    ///
    /// - Parameter enabled: When `true`, enables camera preview via ``SmartSpectraVitalsProcessor/imageOutput``.
    ///   When `false`, disables image generation for performance optimization.
    /// - Important: Always reset to `true` when leaving views that disable image output to avoid interfering with other views that might expect image output
    /// - Note: Changes take effect immediately without requiring processing restart.
    public func setImageOutputEnabled(_ enabled: Bool) {
        self.config.imageOutputEnabled = enabled
    }

    /// Enables or disables headless operation mode.
    ///
    /// - Parameter headlessMode: When `true`, disables camera preview and optimizes for performance.
    ///   When `false`, enables camera preview via ``SmartSpectraVitalsProcessor/imageOutput``.
    /// - Note: Changes to headless mode require restarting the processing pipeline to take effect.
    @available(*, deprecated, message: "Use setImageOutputEnabled(!headlessMode) instead. Note: setHeadlessMode(false) becomes setImageOutputEnabled(true)")
    public func setHeadlessMode(_ headlessMode: Bool) {
        setImageOutputEnabled(!headlessMode)
    }

    /// Formats the latest strict pulse and breathing rates for display.
    private func updateResultText() {
        guard let metricsBuffer = metricsBuffer, metricsBuffer.isInitialized else {
            resultText = "No Results\n..."
            return
        }

        let strictPulseRate = round(metricsBuffer.pulse.strict.value)
        let strictBreathingRate = round(metricsBuffer.breathing.strict.value)
        let strictPulseRateInt = Int(strictPulseRate)
        let strictBreathingRateInt = Int(strictBreathingRate)

        let pulseRateText = "Pulse Rate: \(strictPulseRateInt == 0 ? "N/A": "\(strictPulseRateInt) BPM")"
        let breathingRateText = "Breathing Rate: \(strictBreathingRateInt == 0 ? "N/A": "\(strictBreathingRateInt) BPM")"
        resultText = "\(breathingRateText)\n\(pulseRateText)"

        if strictPulseRateInt == 0 || strictBreathingRateInt == 0 {
            // TODO: 9/30/24 Replace print with Swift Logging
            print("Insufficient data for measurement. Strict Pulse Rate: \(strictPulseRate), Strict Breathing Rate: \(strictBreathingRate)")
            resultErrorText = "Your data was insufficient for an accurate measurement. Please move to a better-lit location, hold still, and try again. For more guidance, see the tutorial in the dropdown menu of the 'i' icon next to 'Checkup.'"
        } else {
            resultErrorText = ""
        }
    }

    @available(*, deprecated, message: "This method is experimental and should not be used in production. Only use for testing purposes.")
    private func useTestServer() {
        PresagePreprocessing.useTestServer()
    }

    internal func updateErrorText(_ errorMessage: String) {

        DispatchQueue.main.async {
            if errorMessage.isEmpty {
                self.resultErrorText = ""
            } else {
                self.resultErrorText = "Error: \(errorMessage)"
            }

        }
    }

    /// Clears metrics and result texts from the previous session.
    ///
    /// Resets `metricsBuffer`, `edgeMetrics`, and associated display texts to
    /// their initial values so that stale data is not shown when a new
    /// recording session starts.
    public func resetMetrics() {
        DispatchQueue.main.async {
            self.metricsBuffer = nil
            self.edgeMetrics = nil
            self.resultText = "No Results\n..."
            self.resultErrorText = ""
        }
    }

}
