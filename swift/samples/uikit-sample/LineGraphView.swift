// LineGraphView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import UIKit

class LineGraphView: UIView {

    var lineColor: UIColor = .systemBlue {
        didSet { setNeedsDisplay() }
    }

    var showGradientFill: Bool = true {
        didSet { setNeedsDisplay() }
    }
    var showGridLines: Bool = true {
        didSet { setNeedsDisplay() }
    }
    var maxPoints: Int = 200

    private var points: [Float] = []

    func append(_ value: Float) {
        points.append(value)
        if points.count > maxPoints {
            points.removeFirst(points.count - maxPoints)
        }
        setNeedsDisplay()
    }

    func append(contentsOf values: [Float]) {
        points.append(contentsOf: values)
        if points.count > maxPoints {
            points.removeFirst(points.count - maxPoints)
        }
        setNeedsDisplay()
    }

    func reset() {
        points.removeAll()
        setNeedsDisplay()
    }

    override func draw(_ rect: CGRect) {
        guard points.count >= 2, let ctx = UIGraphicsGetCurrentContext() else { return }

        let minVal = points.min()!
        let maxVal = points.max()!
        let range = maxVal - minVal
        let inset: CGFloat = 4
        let drawRect = rect.insetBy(dx: inset, dy: inset)

        // Grid lines
        if showGridLines {
            UIColor.white.withAlphaComponent(0.06).setStroke()
            for fraction in [0.25, 0.5, 0.75] as [CGFloat] {
                let y = drawRect.minY + drawRect.height * (1.0 - fraction)
                let gridPath = UIBezierPath()
                gridPath.move(to: CGPoint(x: drawRect.minX, y: y))
                gridPath.addLine(to: CGPoint(x: drawRect.maxX, y: y))
                gridPath.lineWidth = 0.5
                gridPath.stroke()
            }
        }

        // Build line path
        let path = UIBezierPath()
        for (i, value) in points.enumerated() {
            let x = drawRect.minX + drawRect.width * CGFloat(i) / CGFloat(points.count - 1)
            let normalized: CGFloat = range > 0 ? CGFloat((value - minVal) / range) : 0.5
            let y = drawRect.maxY - normalized * drawRect.height
            if i == 0 {
                path.move(to: CGPoint(x: x, y: y))
            } else {
                path.addLine(to: CGPoint(x: x, y: y))
            }
        }

        // Gradient fill under the line
        if showGradientFill {
            ctx.saveGState()
            let fillPath = path.copy() as! UIBezierPath
            let lastX = drawRect.minX + drawRect.width
            fillPath.addLine(to: CGPoint(x: lastX, y: drawRect.maxY))
            fillPath.addLine(to: CGPoint(x: drawRect.minX, y: drawRect.maxY))
            fillPath.close()
            fillPath.addClip()

            let colors = [
                lineColor.withAlphaComponent(0.35).cgColor,
                lineColor.withAlphaComponent(0.0).cgColor,
            ] as CFArray
            if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0.0, 1.0]) {
                ctx.drawLinearGradient(gradient,
                    start: CGPoint(x: rect.midX, y: drawRect.minY),
                    end: CGPoint(x: rect.midX, y: drawRect.maxY),
                    options: [])
            }
            ctx.restoreGState()
        }

        // Glow stroke
        lineColor.withAlphaComponent(0.3).setStroke()
        path.lineWidth = 6
        path.lineCapStyle = .round
        path.lineJoinStyle = .round
        path.stroke()

        // Main stroke
        lineColor.setStroke()
        path.lineWidth = 2
        path.stroke()
    }
}
