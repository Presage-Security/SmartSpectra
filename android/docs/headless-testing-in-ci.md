---
title: Headless Testing in CI
description: Smoke-test that the SmartSpectra Android SDK builds, launches, and initializes on an emulator in CI.
---

# Headless Testing in CI (Android)

See [Headless Testing in CI](../../docs/headless-testing-in-ci.md) for the
cross-platform overview of what's automatable and why. This page covers the
Android specifics.

## What's different on Android

The SDK measures from the live device camera and doesn't accept a recorded
video file, so a video-fed measurement isn't possible in CI.

What you *can* automate on Android is a **headless build-integration smoke
test**: prove that your integration builds, links, launches, and initializes
the SDK — all with public API, no built-in SDK UI involved.

## What you can automate

Drive `SmartSpectraSdk.shared` directly, the same way you would for any
[headless integration](headless-mode.md), and run it as an instrumented test
on an **Android emulator**:

1. **Builds and launches** — the test APK links your app and the SDK and the
   emulator boots it.
2. **Initializes** — set `sdk.config.apiKey` and call `sdk.start()`.
3. **Doesn't hang or crash** — `start()` returning at all — whether it
   succeeds or throws a typed `SmartSpectraException` — is the smoke signal
   that the SDK built, launched, and initialized correctly end to end.

```kotlin
import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.presagetech.smartspectra.SmartSpectraException
import com.presagetech.smartspectra.SmartSpectraSdk
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeadlessSmokeTest {
    @get:Rule
    val cameraPermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun sdkInitializesHeadless() = runBlocking {
        val sdk = SmartSpectraSdk.shared
        sdk.config.apiKey =
            InstrumentationRegistry.getArguments().getString("smartspectraApiKey").orEmpty()

        try {
            sdk.start()
            sdk.stop()
        } catch (e: SmartSpectraException) {
            println("SmartSpectra reported: ${e.message}")
        }
    }
}
```

Keep the check at this smoke level — the emulator's simulated camera feed has
no real face in it, so don't assert on a measurement result. The smoke test
confirms the build/launch/init path, not measurement accuracy (which needs a
physical device and a live camera).

## A CI pipeline, in general terms

1. **Expose the API key** as a job secret and pass it to the test as an
   instrumentation argument.
2. **Run the instrumented test** on an emulator — the runner needs hardware
   acceleration (KVM on Linux) for the emulator to boot in CI.
3. **Fail the job** if the test APK doesn't build or the smoke test fails.

A minimal, provider-neutral sketch (GitHub Actions) using
[Gradle Managed Devices](https://developer.android.com/studio/test/gradle-managed-devices),
which provisions and boots the emulator headlessly for you. Declare the
device in your app module:

```kotlin
// build.gradle.kts
android {
    testOptions {
        managedDevices {
            localDevices {
                create("headlessSmoke") {
                    device = "Pixel 8"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}
```

Then run it in CI:

```yaml
name: smartspectra-android-headless-smoke
on: [push]

jobs:
  headless:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Enable KVM for the emulator
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
            | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Headless build-integration smoke
        run: |
          ./gradlew headlessSmokeDebugAndroidTest \
            -Pandroid.testInstrumentationRunnerArguments.smartspectraApiKey="${{ secrets.SMARTSPECTRA_API_KEY }}"
```

## Limitations

- **No video-fed measurement.** Unlike the desktop SDKs, Android has no
  public way to feed a recorded video file in place of the camera, so this
  pattern can only smoke-test the build/launch/init path, not a full
  measurement.
- **No offline mode.** Like every SmartSpectra SDK, `start()` authenticates
  against the SmartSpectra service, so the runner needs network access.
