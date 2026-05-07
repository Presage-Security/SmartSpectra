// SmartSpectraButtonCheckup.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI

/// A custom button with a label on the left and a heart fill image on the right.
struct SmartSpectraCheckupButton: View {
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Image(systemName: "heart.fill")
                    .padding(.leading, 16)
                Text("Checkup")
                    .textCase(.uppercase)
                Spacer()
            }
        }
            .labelStyle(.titleAndIcon)
            .font(.title3.bold())
            .foregroundStyle(.white)

    }
}
