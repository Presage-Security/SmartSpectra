import AppKit
import SwiftUI

final class AppModel: NSObject, ObservableObject, SmartSpectraRunnerDelegate {
    @Published var frame: NSImage?
    @Published var metrics: [String] = ["Waiting for metrics..."]
    @Published var pulseRateText = "--"
    @Published var pulseConfidenceText = ""
    @Published var breathingRateText = "--"
    @Published var breathingConfidenceText = ""
    @Published var pulseRateTrendHistory: [Double] = []
    @Published var pulseTraceHistory: [Double] = []
    @Published var breathingTraceHistory: [Double] = []
    @Published var hasLiveMetrics = false
    @Published var processingStatus = "not started"
    @Published var validationStatus = "waiting"
    @Published var errorMessage = ""
    @Published var diagnostics = "Frames: 0 | accepted: 0 | blocked: 0"
    @Published var lastMetricTime = "never"
    @Published var apiKey: String
    @Published var isRunning = false

    private let runner = SmartSpectraRunner()

    override init() {
        apiKey = ProcessInfo.processInfo.environment["SMARTSPECTRA_API_KEY"] ?? ""
        super.init()
        runner.delegate = self
    }

    func start() {
        let trimmedAPIKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        apiKey = trimmedAPIKey

        errorMessage = ""
        metrics = ["Waiting for metrics..."]
        pulseRateText = "--"
        pulseConfidenceText = ""
        breathingRateText = "--"
        breathingConfidenceText = ""
        pulseRateTrendHistory = []
        pulseTraceHistory = []
        breathingTraceHistory = []
        hasLiveMetrics = false
        lastMetricTime = "never"
        diagnostics = "Frames: 0 | accepted: 0 | blocked: 0"
        if let message = runner.start(withAPIKey: trimmedAPIKey) {
            errorMessage = message
            isRunning = false
            return
        }
        isRunning = true
    }

    func stop() {
        runner.stop()
        isRunning = false
        processingStatus = "stopped"
    }

    func smartSpectraRunnerDidUpdateFrame(_ image: NSImage) {
        frame = image
    }

    func smartSpectraRunnerDidUpdateStatus(_ processing: String, validation: String) {
        if !processing.isEmpty {
            processingStatus = processing
        }
        if !validation.isEmpty {
            validationStatus = validation
        }
    }

    func smartSpectraRunnerDidUpdateBreathingTrace(
        _ breathingTrace: [NSNumber],
        arterialPressureTrace: [NSNumber],
        timestampUs: Int64
    ) {
        append(breathingTrace, to: \.breathingTraceHistory)
        append(arterialPressureTrace, to: \.pulseTraceHistory)
        if !breathingTrace.isEmpty || !arterialPressureTrace.isEmpty {
            hasLiveMetrics = true
            lastMetricTime = "\(timestampUs) us"
        }
    }

    func smartSpectraRunnerDidUpdateMetrics(_ metrics: [String], timestampUs: Int64) {
        guard !metrics.isEmpty else {
            return
        }

        self.metrics = metrics
        hasLiveMetrics = true
        lastMetricTime = "\(timestampUs) us"
        updateVitalDisplays(from: metrics)
    }

    func smartSpectraRunnerDidUpdateDiagnostics(_ diagnostics: String) {
        self.diagnostics = diagnostics
    }

    func smartSpectraRunnerDidFail(_ message: String) {
        errorMessage = message
        if isRunning {
            stop()
        }
        processingStatus = "failed"
    }

    deinit {
        runner.stop()
    }

    private func updateVitalDisplays(from metrics: [String]) {
        for metric in metrics {
            if metric.hasPrefix("Pulse rate:") {
                updateRate(
                    metric,
                    value: \.pulseRateText,
                    confidence: \.pulseConfidenceText,
                    trend: \.pulseRateTrendHistory
                )
            } else if metric.hasPrefix("Breathing rate:") {
                updateRate(metric, value: \.breathingRateText, confidence: \.breathingConfidenceText)
            }
        }
    }

    private func updateRate(
        _ line: String,
        value valueKeyPath: ReferenceWritableKeyPath<AppModel, String>,
        confidence confidenceKeyPath: ReferenceWritableKeyPath<AppModel, String>,
        trend trendKeyPath: ReferenceWritableKeyPath<AppModel, [Double]>? = nil
    ) {
        guard let colon = line.firstIndex(of: ":") else {
            return
        }

        let remainder = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
        guard let firstToken = remainder.split(separator: " ").first,
              let numericValue = Double(firstToken) else {
            return
        }

        self[keyPath: valueKeyPath] = "\(Int(numericValue.rounded()))"
        if let trendKeyPath {
            append(numericValue, to: trendKeyPath)
        }

        if let open = line.lastIndex(of: "("), let close = line.lastIndex(of: ")"), open < close {
            let confidence = line[line.index(after: open)..<close]
            self[keyPath: confidenceKeyPath] = String(confidence)
        }
    }

    private func append(_ samples: [NSNumber], to historyKeyPath: ReferenceWritableKeyPath<AppModel, [Double]>) {
        guard !samples.isEmpty else {
            return
        }
        var history = self[keyPath: historyKeyPath]
        history.append(contentsOf: samples.map(\.doubleValue))
        if history.count > 80 {
            history.removeFirst(history.count - 80)
        }
        self[keyPath: historyKeyPath] = history
    }

    private func append(_ sample: Double, to historyKeyPath: ReferenceWritableKeyPath<AppModel, [Double]>) {
        var history = self[keyPath: historyKeyPath]
        history.append(sample)
        if history.count > 80 {
            history.removeFirst(history.count - 80)
        }
        self[keyPath: historyKeyPath] = history
    }
}
