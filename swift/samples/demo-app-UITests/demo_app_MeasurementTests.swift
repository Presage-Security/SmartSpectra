// demo_app_MeasurementTests.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import XCTest

final class demo_app_MeasurementTests: UITestBase {

    override func setUpWithError() throws {
        try super.setUpWithError()

        // Handle camera permissions on real device
        if isRealDevice {
            app.handleCameraPermissionIfNeeded()
        }
    }

    /// Dismiss the tutorial sheet if it appears (shown on first launch after tapping Checkup).
    /// The tutorial is a TabView presented as a sheet with a "Skip Tutorial" toolbar button.
    private func dismissTutorialIfNeeded() {
        // The tutorial uses PageTabViewStyle which exposes page indicators
        let pageIndicators = app.pageIndicators.firstMatch
        guard pageIndicators.waitForExistence(timeout: 4) else {
            print("No tutorial detected, skipping...")
            return
        }

        print("Tutorial detected, tapping Skip...")
        let skipButton = app.buttons["Skip Tutorial"]
        if skipButton.waitForExistence(timeout: 3) {
            skipButton.tap()
        } else {
            // Fallback: swipe through all pages then tap the finish checkmark
            for i in 0..<7 {
                print("  Swiping tutorial page \(i + 1)...")
                app.swipeLeft()
                Thread.sleep(forTimeInterval: 0.3)
            }
            let finishButton = app.buttons["Finish Tutorial"]
            if finishButton.waitForExistence(timeout: 3) {
                finishButton.tap()
            }
        }
        Thread.sleep(forTimeInterval: 0.5)
        print("Tutorial dismissed")
    }

    /// Accept a legal agreement (Terms of Service or Privacy Policy) if its sheet appears.
    /// The LegalDocumentView has an "Agree" button that enables after web content loads.
    private func acceptAgreementIfNeeded(name: String, timeout: TimeInterval = 10) {
        let agreeButton = app.buttons["Agree"]
        guard agreeButton.waitForExistence(timeout: timeout) else {
            print("No \(name) agreement presented, skipping...")
            return
        }

        print("\(name) presented, waiting for Agree button to be enabled...")
        // The Agree button is disabled until web content loads; wait for it to be hittable
        let deadline = Date().addingTimeInterval(timeout)
        while !agreeButton.isEnabled && Date() < deadline {
            Thread.sleep(forTimeInterval: 0.5)
        }

        if agreeButton.isEnabled {
            agreeButton.tap()
            print("\(name) accepted")
        } else {
            print("WARNING: \(name) Agree button never became enabled, tapping anyway")
            agreeButton.tap()
        }
        Thread.sleep(forTimeInterval: 0.5)
    }

    /// Handle the full onboarding flow that appears after tapping the Checkup button:
    /// Tutorial (skip) -> Terms of Service (agree) -> Privacy Policy (agree) -> camera permission.
    private func handleOnboardingFlow() {
        dismissTutorialIfNeeded()
        acceptAgreementIfNeeded(name: "Terms of Service")
        acceptAgreementIfNeeded(name: "Privacy Policy")

        // Handle camera permission system dialog
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        if springboard.alerts.firstMatch.waitForExistence(timeout: 3) {
            let alert = springboard.alerts.firstMatch
            let allow = alert.buttons["Allow"].exists ? alert.buttons["Allow"] : alert.buttons["OK"]
            if allow.exists {
                print("Allowing camera permission...")
                allow.tap()
            }
        }
    }

    @MainActor
    func testContinuousMeasurementFlow() throws {
        // Test full continuous measurement workflow
        XCTAssertTrue(waitForAppToLoad(), "App should launch")

        // Find and tap the SmartSpectra Checkup button
        let checkupButton = app.buttons.containing(.image, identifier: "Love").firstMatch
        if checkupButton.waitForExistence(timeout: 3) {
            print("Found SmartSpectra Checkup button")
            checkupButton.tap()

            // Handle onboarding flow: tutorial -> terms -> privacy -> camera permission
            handleOnboardingFlow()

            // The screening view auto-starts measurement on appear in continuous
            // mode — the record button transitions Record → Stop as the SDK
            // moves .idle → .starting → .running. Poll both labels so the
            // assertion is robust to that transition timing.
            let recordButton = app.buttons["Record"]
            let stopButton = app.buttons["Stop"]
            let deadline = Date().addingTimeInterval(15)
            var measurementButton: XCUIElement? = nil
            while Date() < deadline {
                if recordButton.exists { measurementButton = recordButton; break }
                if stopButton.exists { measurementButton = stopButton; break }
                Thread.sleep(forTimeInterval: 0.5)
            }
            guard let measurementButton else {
                print("❌ Neither Record nor Stop button appeared")
                takeScreenshot(name: "Continuous Mode - No Measurement Button")
                XCTFail("Could not find measurement control (Record/Stop) in screening UI")
                return
            }
            print("✅ Measurement control visible (label=\(measurementButton.label))")

            // Let the continuous measurement run for a short while.
            print("⏱️ Recording for 10 seconds...")
            Thread.sleep(forTimeInterval: 10)

            // Stop the measurement. After auto-start the button is "Stop";
            // if the SDK is still in .starting, the button reads "Record" but
            // is disabled — a tap on either is a no-op in that case, so the
            // back-nav fallback handles cleanup.
            let liveStopButton = app.buttons["Stop"]
            if liveStopButton.waitForExistence(timeout: 2) {
                print("✅ Tapping Stop to end measurement")
                liveStopButton.tap()
                Thread.sleep(forTimeInterval: 3)
            } else {
                print("ℹ️ Stop button not present — SDK may still be initializing")
                takeScreenshot(name: "Continuous Mode - No Stop Button")
            }

            // Press back button to return to main screen
            let backButton = app.navigationBars.buttons.element(boundBy: 0)
            if backButton.waitForExistence(timeout: 2) {
                print("✅ Found back button, returning to main screen")
                backButton.tap()
                Thread.sleep(forTimeInterval: 1)
            } else {
                let backButton2 = app.buttons["Back"]
                if backButton2.waitForExistence(timeout: 1) {
                    print("✅ Found 'Back' button, returning to main screen")
                    backButton2.tap()
                    Thread.sleep(forTimeInterval: 1)
                } else {
                    print("⚠️ Back button not found, may still be in measurement view")
                    takeScreenshot(name: "Continuous Mode - No Back Button")
                }
            }
        } else {
            print("❌ SmartSpectra Checkup button not found")
            takeScreenshot(name: "Continuous Mode - No Checkup Button")
            XCTFail("Could not find SmartSpectra Checkup button")
            return
        }

        // Check for results without scrolling (they might be visible)
        let sectionHeaders = ["Pulse", "Breathing", "Blood Pressure", "Face"]
        var foundResults = false

        for header in sectionHeaders {
            let section = app.staticTexts[header]
            if section.exists {
                print("✅ Found \(header) section")
                foundResults = true
            }
        }

        // Also check for any chart titles that might appear
        let chartTitles = ["Pulse Pleth", "Breathing Pleth", "Pulse Rates", "Breathing Rates"]
        for title in chartTitles {
            let chart = app.staticTexts[title]
            if chart.exists {
                print("✅ Found \(title) chart")
                foundResults = true
            }
        }

        if foundResults {
            takeScreenshot(name: "Continuous Mode - Data Found")
        } else {
            print("ℹ️ No immediate data visible - continuous mode may need more time")
            takeScreenshot(name: "Continuous Mode - Waiting for Data")
        }
    }

    @MainActor
    func testMeasurementPreparation() throws {
        // Helper test to prepare for manual measurement testing
        // This test sets up the app in the right state for manual testing

        XCTAssertTrue(waitForAppToLoad(), "App should launch")

        takeScreenshot(name: "App Setup")

        // Show all available controls
        print("Available buttons:")
        for button in app.buttons.allElementsBoundByIndex {
            if button.exists {
                print("- \(button.label)")
            }
        }

        takeScreenshot(name: "App Ready for Manual Testing")
    }
}
