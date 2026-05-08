import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var model: AppModel

    private let coral = Color(red: 1.0, green: 0.42, blue: 0.42)
    private let teal = Color(red: 0.31, green: 0.80, blue: 0.77)

    var body: some View {
        HStack(spacing: 0) {
            preview

            Divider()

            controls
                .frame(width: 320)
        }
    }

    private var preview: some View {
        ZStack {
            Color.black

            if let frame = model.frame {
                Image(nsImage: frame)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                VStack(spacing: 12) {
                    Image(systemName: "video")
                        .font(.system(size: 44, weight: .regular))
                    Text("Camera preview")
                        .font(.title3)
                }
                .foregroundStyle(.secondary)
            }

            VStack {
                HStack {
                    validationPill
                    Spacer()
                }
                .padding(18)

                Spacer()

                metricsOverlay
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var validationPill: some View {
        Text(validationHint)
            .font(.callout.weight(.medium))
            .lineLimit(2)
            .foregroundStyle(.white)
            .padding(.horizontal, 14)
            .padding(.vertical, 9)
            .background(Color.black.opacity(0.62), in: RoundedRectangle(cornerRadius: 8))
            .opacity(model.validationStatus.isEmpty || model.validationStatus == "waiting" ? 0 : 1)
    }

    private var validationHint: String {
        guard let colon = model.validationStatus.firstIndex(of: ":") else {
            return model.validationStatus
        }

        return model.validationStatus[model.validationStatus.index(after: colon)...]
            .trimmingCharacters(in: .whitespaces)
    }

    private var metricsOverlay: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(model.processingStatus)
                        .font(.headline)
                        .foregroundStyle(.white)
                    Text(model.diagnostics)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.white.opacity(0.62))
                }

                Spacer()

                Text(model.hasLiveMetrics ? "Live" : "Waiting")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(model.hasLiveMetrics ? teal : .white.opacity(0.72))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
            }

            HStack(spacing: 12) {
                VitalTile(
                    title: "Heart Rate",
                    value: model.pulseRateText,
                    unit: "bpm",
                    confidence: model.pulseConfidenceText,
                    icon: "heart.fill",
                    color: coral,
                    points: model.pulseTraceHistory.isEmpty ? model.pulseRateTrendHistory : model.pulseTraceHistory
                )

                VitalTile(
                    title: "Breathing Rate",
                    value: model.breathingRateText,
                    unit: "brpm",
                    confidence: model.breathingConfidenceText,
                    icon: "wind",
                    color: teal,
                    points: model.breathingTraceHistory
                )
            }
        }
        .padding(18)
        .background {
            LinearGradient(
                colors: [
                    Color.black.opacity(0.0),
                    Color.black.opacity(0.78),
                    Color.black.opacity(0.94),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        }
    }

    private var controls: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("SmartSpectra")
                        .font(.title2.weight(.semibold))
                    Text("Native macOS example")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("API Key")
                        .font(.headline)
                    SecureField("SMARTSPECTRA_API_KEY", text: $model.apiKey)
                        .textFieldStyle(.roundedBorder)
                }

                HStack {
                    Button {
                        model.start()
                    } label: {
                        Label("Start", systemImage: "play.fill")
                    }
                    .disabled(model.isRunning)

                    Button {
                        model.stop()
                    } label: {
                        Label("Stop", systemImage: "stop.fill")
                    }
                    .disabled(!model.isRunning)
                }

                if !model.errorMessage.isEmpty {
                    Text(model.errorMessage)
                        .font(.callout)
                        .foregroundStyle(.red)
                        .fixedSize(horizontal: false, vertical: true)
                        .textSelection(.enabled)
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text("Latest Metrics")
                        .font(.headline)
                    Text("Last update: \(model.lastMetricTime)")
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                    ForEach(model.metrics, id: \.self) { metric in
                        Text(metric)
                            .font(.system(.body, design: .monospaced))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                Spacer(minLength: 0)
            }
            .padding(20)
        }
    }
}

private struct VitalTile: View {
    let title: String
    let value: String
    let unit: String
    let confidence: String
    let icon: String
    let color: Color
    let points: [Double]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .foregroundStyle(color)
                    .font(.title3.weight(.semibold))
                    .frame(width: 24)
                Text(title)
                    .font(.headline)
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Spacer()
            }

            HStack(alignment: .firstTextBaseline, spacing: 5) {
                Text(value)
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(color)
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text(unit)
                    .font(.callout.weight(.medium))
                    .foregroundStyle(.white.opacity(0.58))
                Spacer()
                if !confidence.isEmpty {
                    Text(confidence)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.white.opacity(0.54))
                }
            }

            Sparkline(points: points, color: color)
                .frame(height: 46)
                .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                .overlay {
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(color.opacity(0.22), lineWidth: 1)
                }
        }
        .padding(14)
        .frame(maxWidth: .infinity, minHeight: 152)
        .background(Color.white.opacity(0.09), in: RoundedRectangle(cornerRadius: 8))
    }
}

private struct Sparkline: View {
    let points: [Double]
    let color: Color

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                Path { path in
                    for fraction in [0.25, 0.5, 0.75] {
                        let y = proxy.size.height * (1.0 - fraction)
                        path.move(to: CGPoint(x: 0, y: y))
                        path.addLine(to: CGPoint(x: proxy.size.width, y: y))
                    }
                }
                .stroke(Color.white.opacity(0.08), lineWidth: 0.5)

                if points.count >= 2 {
                    SparklinePath(points: points)
                        .stroke(color.opacity(0.35), style: StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round))
                    SparklinePath(points: points)
                        .stroke(color, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                }
            }
            .padding(4)
        }
    }
}

private struct SparklinePath: Shape {
    let points: [Double]

    func path(in rect: CGRect) -> Path {
        var path = Path()
        guard points.count >= 2,
              let minValue = points.min(),
              let maxValue = points.max() else {
            return path
        }

        let range = maxValue - minValue
        for (index, value) in points.enumerated() {
            let x = rect.minX + rect.width * CGFloat(index) / CGFloat(points.count - 1)
            let normalized = range > 0 ? (value - minValue) / range : 0.5
            let y = rect.maxY - rect.height * CGFloat(normalized)
            if index == 0 {
                path.move(to: CGPoint(x: x, y: y))
            } else {
                path.addLine(to: CGPoint(x: x, y: y))
            }
        }
        return path
    }
}
