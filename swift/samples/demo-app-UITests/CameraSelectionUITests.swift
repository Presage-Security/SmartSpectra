// Copyright © 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
import XCTest

final class CameraSelectionUITests: XCTestCase {
    @MainActor
    private func launch() -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        // Exercise failure recovery without depending on customer credentials.
        app.launchEnvironment["SMARTSPECTRA_API_KEY"] = "invalid-camera-picker-test-key"
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 15))
        return app
    }

    @MainActor
    private func waitUntilEnabled(_ element: XCUIElement) {
        XCTAssertTrue(element.waitForExistence(timeout: 15))
        let enabled = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: element)
        XCTAssertEqual(XCTWaiter.wait(for: [enabled], timeout: 30), .completed)
    }

    @MainActor
    func testPickerRefreshAndDefaultAcrossTabs() {
        let app = launch()
        for tab in ["Checkup", "Headless Example"] {
            app.tabBars.buttons[tab].tap()
            let picker = app.buttons["selectCamera"]
            waitUntilEnabled(picker)
            picker.tap()
            let refresh = app.buttons["cameraRefresh"]
            waitUntilEnabled(refresh)
            refresh.tap()
            let defaultCamera = app.buttons["cameraDefault"]
            waitUntilEnabled(defaultCamera)
            defaultCamera.tap()
            XCTAssertTrue(picker.waitForExistence(timeout: 5))
            XCTAssertFalse(defaultCamera.exists)
        }
    }

    @MainActor
    func testPickerRecoversAfterFailedStart() {
        let app = launch()
        app.tabBars.buttons["Headless Example"].tap()
        let start = app.buttons["Start"]
        XCTAssertTrue(start.waitForExistence(timeout: 5))
        start.tap()
        XCTAssertTrue(app.staticTexts["startupError"].waitForExistence(timeout: 60))
        let picker = app.buttons["selectCamera"]
        waitUntilEnabled(picker)
        picker.tap()
        let defaultCamera = app.buttons["cameraDefault"]
        waitUntilEnabled(defaultCamera)
        defaultCamera.tap()
        waitUntilEnabled(start)
        XCTAssertFalse(app.staticTexts["startupError"].exists)
    }
}
