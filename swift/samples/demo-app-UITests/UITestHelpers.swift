// UITestHelpers.swift
//
// Copyright © 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary
import XCTest

extension XCUIElement {
    func waitAndTap(timeout: TimeInterval = 5) -> Bool {
        if self.waitForExistence(timeout: timeout) {
            self.tap()
            return true
        }
        return false
    }
    
    func scrollToElement(maxSwipes: Int = 5) {
        var swipeCount = 0
        while !self.isHittable && swipeCount < maxSwipes {
            self.firstMatch.swipeUp()
            swipeCount += 1
        }
    }
}

extension XCUIApplication {
    func isRunningOnRealDevice() -> Bool {
        #if targetEnvironment(simulator)
        return false
        #else
        return true
        #endif
    }
    
    func handleCameraPermissionIfNeeded() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let allowButton = springboard.buttons["Allow"]
        let okButton = springboard.buttons["OK"]
        
        if allowButton.waitForExistence(timeout: 3) {
            allowButton.tap()
        } else if okButton.waitForExistence(timeout: 3) {
            okButton.tap()
        }
    }
    
    func dismissKeyboardIfPresent() {
        if self.keyboards.count > 0 {
            self.toolbars.buttons["Done"].tap()
            if self.keyboards.count > 0 {
                self.tap()
            }
        }
    }
}

class UITestBase: XCTestCase {
    var app: XCUIApplication!
    var isRealDevice: Bool = false
    
    override func setUpWithError() throws {
        try super.setUpWithError()
        continueAfterFailure = false
        
        app = XCUIApplication()

        #if !targetEnvironment(simulator)
        isRealDevice = true
        #endif

        // Forward CI API key to the app under test
        if let apiKey = ProcessInfo.processInfo.environment["SMARTSPECTRA_API_KEY"] {
            app.launchEnvironment["SMARTSPECTRA_API_KEY"] = apiKey
        }

        app.launch()
    }
    
    override func tearDownWithError() throws {
        app = nil
        try super.tearDownWithError()
    }
    
    func takeScreenshot(name: String) {
        let screenshot = app.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
    
    func waitForAppToLoad(timeout: TimeInterval = 10) -> Bool {
        let tabBar = app.tabBars.firstMatch
        return tabBar.waitForExistence(timeout: timeout)
    }
}