// DemoAppView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
struct DemoAppView: View {
    var body: some View {
        TabView {
            CheckupView()
                .tabItem {
                    Label("Checkup", systemImage: "heart.fill")
                }
            HeadlessSDKExample()
                .tabItem {
                    Label("Headless Example", systemImage: "heart.text.square.fill")
                }
            VideoTestingView()
                .tabItem {
                    Label("Video Testing", systemImage: "film")
                }
        }
    }
}

#Preview {
    DemoAppView()
}
