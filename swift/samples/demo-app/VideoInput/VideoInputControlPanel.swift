// VideoInputControlPanel.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import UniformTypeIdentifiers
import PhotosUI

/// Control panel for picking video and timestamp files for video input testing.
///
/// The parent view owns decoding and submission. This panel only selects files.
struct VideoInputControlPanel: View {
    @Binding var selectedVideoPath: String
    @Binding var selectedTimestampPath: String
    @State private var showVideoFilePicker: Bool = false
    @State private var showVideoLibraryPicker: Bool = false
    @State private var showTimestampPicker: Bool = false

    var body: some View {
        Group {
            HStack {
                Button("Pick From Files", systemImage: "folder") { showVideoFilePicker = true }
                Button("Pick From Library", systemImage: "photo.on.rectangle") { showVideoLibraryPicker = true }
                Button("Pick Timestamps", systemImage: "text.append") { showTimestampPicker = true }
            }

            if !selectedVideoPath.isEmpty {
                Text("Video: \(URL(fileURLWithPath: selectedVideoPath).lastPathComponent)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            if !selectedTimestampPath.isEmpty {
                Text("Timestamps: \(URL(fileURLWithPath: selectedTimestampPath).lastPathComponent)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .sheet(isPresented: $showVideoFilePicker) {
            DocumentPicker(contentTypes: [.movie, .mpeg4Movie, .quickTimeMovie]) { url in
                selectedVideoPath = url.path
            }
        }
        .sheet(isPresented: $showVideoLibraryPicker) {
            LibraryVideoPicker { url in
                selectedVideoPath = url.path
            }
        }
        .sheet(isPresented: $showTimestampPicker) {
            DocumentPicker(contentTypes: [.plainText]) { url in
                selectedTimestampPath = url.path
            }
        }
    }
}
