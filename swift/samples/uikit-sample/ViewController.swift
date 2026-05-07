// ViewController.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import UIKit
import SmartSpectra

class ViewController: UIViewController {

    @IBOutlet private var previewImageView: UIImageView!
    @IBOutlet private var validationLabel: PaddedLabel!
    @IBOutlet private var heartRateLabel: UILabel!
    @IBOutlet private var breathingRateLabel: UILabel!
    @IBOutlet private var breathingGraphView: LineGraphView!
    @IBOutlet private var bloodPressureGraphView: LineGraphView!
    @IBOutlet private var statusLabel: UILabel!
    @IBOutlet private var toggleButton: UIButton!
    @IBOutlet private var bottomPanel: UIView!
    @IBOutlet private var insightLabel: UILabel!
    @IBOutlet private var insightButton: UIButton!

    private let coralColor = UIColor(red: 1.0, green: 0.42, blue: 0.42, alpha: 1.0)
    private let tealColor = UIColor(red: 0.31, green: 0.80, blue: 0.77, alpha: 1.0)
    private let violetColor = UIColor(red: 0.65, green: 0.55, blue: 0.98, alpha: 1.0)

    private let sdk = SmartSpectraSDK.shared
    private let gradientLayer = CAGradientLayer()

    private var portraitConstraints: [NSLayoutConstraint] = []
    private var landscapeConstraints: [NSLayoutConstraint] = []

    // Set your API key here
    private var API_KEY = "INSERT_API_KEY_HERE"
    
    override func viewDidLoad() {
        super.viewDidLoad()
        overrideUserInterfaceStyle = .dark

        sdk.config.apiKey = API_KEY
        sdk.config.imageOutputEnabled = true
        sdk.config.requestedMetrics = SmartSpectraConfig.cardioMetrics + SmartSpectraConfig.breathingMetrics

        // Panel gradient
        gradientLayer.colors = [
            UIColor.clear.cgColor,
            UIColor.black.withAlphaComponent(0.85).cgColor,
            UIColor.black.withAlphaComponent(0.95).cgColor,
        ]
        gradientLayer.locations = [0.0, 0.25, 1.0]
        bottomPanel.layer.insertSublayer(gradientLayer, at: 0)

        // Validation label pill
        validationLabel.backgroundColor = UIColor.black.withAlphaComponent(0.6)
        validationLabel.layer.cornerRadius = 12
        validationLabel.clipsToBounds = true

        // Heart rate
        heartRateLabel.textColor = coralColor

        // Graphs
        breathingGraphView.lineColor = tealColor
        bloodPressureGraphView.lineColor = violetColor

        // Button
        applyButtonStyle(title: "Start", isPrimary: true)

        previewImageView.translatesAutoresizingMaskIntoConstraints = false
        bottomPanel.translatesAutoresizingMaskIntoConstraints = false

        setupLayoutConstraints()
        applyLayout(for: view.bounds.size)
        bindSDK()
    }

    // MARK: - Adaptive Layout

    private func setupLayoutConstraints() {
        let safe = view.safeAreaLayoutGuide

        // Portrait: preview fills screen, panel overlays bottom
        portraitConstraints = [
            previewImageView.topAnchor.constraint(equalTo: view.topAnchor),
            previewImageView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            previewImageView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            previewImageView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            bottomPanel.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            bottomPanel.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            bottomPanel.bottomAnchor.constraint(equalTo: safe.bottomAnchor),
        ]

        // Landscape: preview on left, panel on right (safe area aware)
        landscapeConstraints = [
            previewImageView.topAnchor.constraint(equalTo: view.topAnchor),
            previewImageView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            previewImageView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            previewImageView.trailingAnchor.constraint(equalTo: bottomPanel.leadingAnchor),

            bottomPanel.topAnchor.constraint(equalTo: safe.topAnchor),
            bottomPanel.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            bottomPanel.bottomAnchor.constraint(equalTo: safe.bottomAnchor),
            bottomPanel.widthAnchor.constraint(equalTo: view.widthAnchor, multiplier: 0.42),
        ]
    }

    private func applyLayout(for size: CGSize) {
        let isLandscape = size.width > size.height

        NSLayoutConstraint.deactivate(portraitConstraints)
        NSLayoutConstraint.deactivate(landscapeConstraints)
        NSLayoutConstraint.activate(isLandscape ? landscapeConstraints : portraitConstraints)

        if isLandscape {
            gradientLayer.startPoint = CGPoint(x: 0, y: 0.5)
            gradientLayer.endPoint = CGPoint(x: 1, y: 0.5)
        } else {
            gradientLayer.startPoint = CGPoint(x: 0.5, y: 0)
            gradientLayer.endPoint = CGPoint(x: 0.5, y: 1)
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        gradientLayer.frame = bottomPanel.bounds
    }

    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransition(to: size, with: coordinator)
        coordinator.animate { _ in
            self.applyLayout(for: size)
            self.view.layoutIfNeeded()
        }
    }

    // MARK: - Button Style

    private func applyButtonStyle(title: String, isPrimary: Bool) {
        var config = UIButton.Configuration.filled()
        config.title = title
        config.cornerStyle = .capsule
        config.baseBackgroundColor = isPrimary ? coralColor : UIColor(white: 0.3, alpha: 1.0)
        config.baseForegroundColor = .white
        toggleButton.configuration = config
    }

    // MARK: - SDK Binding

    /// Observes a single `@Observable` SDK property and re-arms on each change.
    /// UIKit has no `.onChange` modifier, so we thread observation updates
    /// through a `withObservationTracking` re-arm loop.
    ///
    /// `@MainActor` guarantees both the initial synchronous emit and the
    /// re-arm path touch UI state on the main thread — the SDK's
    /// `@MainActor`-isolated storage requires it.
    @MainActor
    private func observeSDK<T>(
        _ keyPath: KeyPath<SmartSpectraSDK, T>,
        _ handler: @escaping @MainActor (T) -> Void
    ) {
        withObservationTracking {
            _ = sdk[keyPath: keyPath]
        } onChange: { [weak self] in
            Task { @MainActor [weak self] in
                guard let self else { return }
                handler(self.sdk[keyPath: keyPath])
                self.observeSDK(keyPath, handler)
            }
        }
        // Emit the initial value so UI reflects current state on first bind.
        handler(sdk[keyPath: keyPath])
    }

    private func bindSDK() {
        observeSDK(\.imageOutput) { [weak self] image in
            self?.previewImageView.image = image
        }
        observeSDK(\.validationStatus) { [weak self] status in
            self?.validationLabel.text = status?.hint
            self?.validationLabel.isHidden = (status?.hint ?? "").isEmpty
        }
        observeSDK(\.processingStatus) { [weak self] status in
            self?.updateStatus(status)
        }
        observeSDK(\.error) { [weak self] error in
            self?.updateError(error)
        }
        observeSDK(\.metrics) { [weak self] metrics in
            guard let self, let metrics else { return }
            self.updateMetrics(metrics)
        }
        observeSDK(\.insight) { [weak self] insight in
            guard let self, let insight else { return }
            switch insight.result {
            case .analysis(let text):
                self.insightLabel.text = text
            case .error(let text):
                self.insightLabel.text = "Error: \(text)"
            case .none:
                break
            case .some:
                break
            }
            self.insightButton.isEnabled = true
            self.insightButton.configuration?.showsActivityIndicator = false
        }
    }

    // MARK: - Metrics

    private func updateMetrics(_ metrics: Metrics) {
        if let latest = metrics.cardio.pulseRate.last {
            let value = "\(Int(latest.value))"
            let unit = " bpm"
            let attr = NSMutableAttributedString(string: value + unit)
            attr.addAttributes([
                .font: UIFont.systemFont(ofSize: 28, weight: .bold),
                .foregroundColor: coralColor,
            ], range: NSRange(location: 0, length: value.count))
            attr.addAttributes([
                .font: UIFont.systemFont(ofSize: 14, weight: .medium),
                .foregroundColor: UIColor.white.withAlphaComponent(0.5),
            ], range: NSRange(location: value.count, length: unit.count))
            heartRateLabel.attributedText = attr
        }

        if let latest = metrics.breathing.rate.last {
            breathingRateLabel.text = "Breathing Rate \u{2014} \(Int(latest.value)) brpm"
        }
        breathingGraphView.append(contentsOf: metrics.breathing.upperTrace.map(\.value))
        bloodPressureGraphView.append(contentsOf: metrics.cardio.arterialPressureTrace.map(\.value))
    }

    // MARK: - Status

    private func updateStatus(_ status: ProcessingStatus) {
        switch status {
        case .idle:
            statusLabel.text = sdk.error.map { "Error: \(Self.userFacingMessage(for: $0))" } ?? "Idle"
            applyButtonStyle(title: "Start", isPrimary: true)
            toggleButton.isEnabled = true
        case .starting:
            statusLabel.text = "Starting"
            toggleButton.isEnabled = false
        case .running:
            statusLabel.text = "Running"
            applyButtonStyle(title: "Stop", isPrimary: false)
            toggleButton.isEnabled = true
        case .stopping:
            statusLabel.text = "Stopping"
            toggleButton.isEnabled = false
        case .error:
            statusLabel.text = sdk.error.map { "Error: \(Self.userFacingMessage(for: $0))" } ?? "Error"
            applyButtonStyle(title: "Start", isPrimary: true)
            toggleButton.isEnabled = true
        @unknown default:
            statusLabel.text = "-"
        }
    }

    private func updateError(_ error: SmartSpectraError?) {
        guard let error else {
            if sdk.processingStatus == .idle {
                statusLabel.text = "Idle"
            }
            return
        }

        statusLabel.text = "Error: \(Self.userFacingMessage(for: error))"
    }

    // MARK: - Actions

    @IBAction private func insightTapped() {
        guard sdk.processingStatus == .running else { return }
        insightButton.isEnabled = false
        insightButton.configuration?.showsActivityIndicator = true
        insightLabel.text = "Analyzing\u{2026}"
        do {
            try sdk.requestInsight("Summarize my current vital signs and flag anything unusual.")
        } catch {
            insightLabel.text = "Error: \(Self.userFacingMessage(for: error))"
            insightButton.isEnabled = true
            insightButton.configuration?.showsActivityIndicator = false
        }
    }

    @IBAction private func toggleTapped() {
        Task {
            do {
                if sdk.processingStatus == .running {
                    try await sdk.stop()
                } else {
                    heartRateLabel.text = "-- bpm"
                    heartRateLabel.textColor = coralColor
                    breathingRateLabel.text = "Breathing Rate"
                    insightLabel.text = "Tap Ask AI to get an analysis of your vitals."
                    breathingGraphView.reset()
                    bloodPressureGraphView.reset()
                    try await sdk.start()
                }
            } catch {
                statusLabel.text = "Error: \(Self.userFacingMessage(for: error))"
                applyButtonStyle(title: "Start", isPrimary: true)
                toggleButton.isEnabled = true
            }
        }
    }

    // MARK: - Error Handling

    private static func userFacingMessage(for error: Error) -> String {
        guard let sdkError = error as? SmartSpectraError else {
            return error.localizedDescription
        }
        switch sdkError.code {
        case .authenticationFailed:
            return "Authentication failed. Check your API key."
        case .creditExhausted:
            return "Account credits exhausted."
        case .networkError:
            return sdkError.retryable
                ? "Network issue — please try again."
                : "Network error: \(sdkError.message)"
        case .inputUnavailable:
            return "Camera is unavailable. Check permissions."
        case .configurationFailed:
            return "Configuration failed: \(sdkError.message)"
        case .invalidState:
            return "SDK is in an invalid state for this action."
        case .serverError, .processingFailed, .frameConversionFailed, .nonMonotonicTimestamp:
            return sdkError.message
        }
    }
}
