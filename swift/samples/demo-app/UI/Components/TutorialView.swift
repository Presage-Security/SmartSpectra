// TutorialView.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import SwiftUI

struct TutorialPage: Identifiable {
    let id = UUID()
    let imageName: String
    let description: String
}

struct TutorialView: View {
    var onTutorialCompleted: (() -> Void)?
    @State private var currentPage = 0
    @State private var hasCompletedTutorial = false
    @State private var hasReachedLastPage = false

    private let pages: [TutorialPage] = [
        TutorialPage(
            imageName: "tutorial_image1",
            description: "Place your device running SmartSpectra on a stable surface, like a table."
        ),
        TutorialPage(
            imageName: "tutorial_image2",
            description: "SmartSpectra works best when you're in a well-lit environment with natural sunlight for optimal performance."
        ),
        TutorialPage(
            imageName: "tutorial_image3",
            description: "SmartSpectra works best when your face is evenly lit and does not have shadows."
        ),
        TutorialPage(
            imageName: "tutorial_image4",
            description: "Avoid having bright light sources directly behind your face, such as overhead lighting."
        ),
        TutorialPage(
            imageName: "tutorial_image5",
            description: "Stay still and refrain from talking while using SmartSpectra."
        ),
        TutorialPage(
            imageName: "tutorial_image6",
            description: "You'll receive real-time feedback during the measurement process to assist you."
        ),
        TutorialPage(
            imageName: "tutorial_image7",
            description: "Start recording with SmartSpectra upon the 'Hold Still and Record' prompt. A 30-second recording follows, and should feedback appear, comply with the prompts for an auto-restart."
        )
    ]

    var body: some View {
        TabView(selection: $currentPage) {
            ForEach(pages.indices, id: \.self) { index in
                TutorialPageView(
                    page: pages[index],
                    isLastPage: index == pages.count - 1,
                    onTutorialCompleted: completeTutorial
                )
                .tag(index)
            }
        }
        .tabViewStyle(.page)
        .indexViewStyle(.page(backgroundDisplayMode: .always))
        .tint(.black)
        .ignoresSafeArea()
        .onChange(of: currentPage) {
            if currentPage >= pages.count - 1 {
                hasReachedLastPage = true
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if hasReachedLastPage || currentPage >= pages.count - 1 {
                    Button("Finish Tutorial", systemImage: "checkmark.circle.fill", action: completeTutorial)
                        .labelStyle(.iconOnly)
                } else {
                    Button("Skip", action: completeTutorial)
                        .accessibilityLabel("Skip Tutorial")
                }
            }
        }
    }

    private func completeTutorial() {
        guard !hasCompletedTutorial else { return }
        hasCompletedTutorial = true
        onTutorialCompleted?()
    }
}

struct TutorialPageView: View {
    let page: TutorialPage
    let isLastPage: Bool
    let onTutorialCompleted: (() -> Void)?

    var body: some View {
        VStack {
            Color.white
                .ignoresSafeArea()
                .overlay(
                    Image(page.imageName)
                        .resizable()
                        .scaledToFit()
                        .frame(maxHeight: .infinity)
                        .padding()
                        .accessibilityHidden(true)
                )

            Spacer()

            Text(page.description)
                .font(.title3.weight(.medium))
                .fontDesign(.rounded)
                .padding()
                .background(Color.white.opacity(0.9))
                .foregroundStyle(.black)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            Text(isLastPage ? "Tap the checkmark to finish" : "Swipe left/right to navigate")
                .font(.footnote)
                .padding(10)
                .background(Color.black.opacity(0.7))
                .foregroundStyle(.white)
                .clipShape(.rect(cornerRadius: 10))
                .padding(.bottom, 10)
        }
        .background(.white)
    }
}

#Preview {
    TutorialView(
        onTutorialCompleted: { print("Tutorial Completed") }
    )
}
