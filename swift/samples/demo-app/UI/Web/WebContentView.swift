// WebContentView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import WebKit

struct WebContentView: UIViewRepresentable {
    let url: URL
    var onLoadStateChange: ((Bool) -> Void)?

    func makeCoordinator() -> Coordinator {
        Coordinator(onLoadStateChange: onLoadStateChange)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = false
        onLoadStateChange?(false)
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate {
        private let onLoadStateChange: ((Bool) -> Void)?

        init(onLoadStateChange: ((Bool) -> Void)?) {
            self.onLoadStateChange = onLoadStateChange
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            let hideScript = "document.documentElement.style.webkitTouchCallout='none';" +
                "document.documentElement.style.webkitUserSelect='none';"
            webView.evaluateJavaScript(hideScript, completionHandler: nil)
            onLoadStateChange?(true)
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            onLoadStateChange?(false)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            onLoadStateChange?(false)
        }
    }
}
