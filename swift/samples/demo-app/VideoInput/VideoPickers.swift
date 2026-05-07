// VideoPickers.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI
import UniformTypeIdentifiers
import PhotosUI

// MARK: - Files app document picker (e.g., .mov, .mp4, .txt)

struct DocumentPicker: UIViewControllerRepresentable {
    let contentTypes: [UTType]
    let onPick: (URL) -> Void

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: contentTypes, asCopy: true)
        picker.delegate = context.coordinator
        picker.allowsMultipleSelection = false
        return picker
    }

    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPick: onPick) }

    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        let onPick: (URL) -> Void
        init(onPick: @escaping (URL) -> Void) { self.onPick = onPick }
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            guard let url = urls.first else { return }
            onPick(url)
        }
        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {}
    }
}

// MARK: - Photo Library video picker (returns a temp file URL)

struct LibraryVideoPicker: UIViewControllerRepresentable {
    let onPick: (URL) -> Void

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration(photoLibrary: .shared())
        config.selectionLimit = 1
        config.filter = .videos
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPick: onPick) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let onPick: (URL) -> Void
        init(onPick: @escaping (URL) -> Void) { self.onPick = onPick }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            defer { picker.dismiss(animated: true, completion: nil) }
            guard let provider = results.first?.itemProvider else { return }

            // Preferred: general movie type
            let candidates: [UTType] = [.movie, .mpeg4Movie, .quickTimeMovie]
            guard let type = candidates.first(where: { provider.hasItemConformingToTypeIdentifier($0.identifier) }) else {
                return
            }

            provider.loadFileRepresentation(forTypeIdentifier: type.identifier) { url, error in
                guard let url = url else { return }
                // Copy to a persistent temp location; the provided URL may be sandboxed and short‑lived.
                let tmpDir = FileManager.default.temporaryDirectory
                let dest = tmpDir.appendingPathComponent("selected_video_\(UUID().uuidString)").appendingPathExtension(url.pathExtension)
                do {
                    if FileManager.default.fileExists(atPath: dest.path) {
                        try FileManager.default.removeItem(at: dest)
                    }
                    try FileManager.default.copyItem(at: url, to: dest)
                    Task { @MainActor in self.onPick(dest) }
                } catch {
                    // Fallback: try loadInPlace
                    provider.loadInPlaceFileRepresentation(forTypeIdentifier: type.identifier) { inPlaceURL, _, _ in
                        guard let inPlaceURL = inPlaceURL else { return }
                        Task { @MainActor in self.onPick(inPlaceURL) }
                    }
                }
            }
        }
    }
}

