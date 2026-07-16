---
title: Headless Testing in CI
description: Run a full SmartSpectra measurement on an Android emulator in CI by feeding a recorded video, or smoke-test that the SDK builds and initializes.
---

# Headless Testing in CI (Android)

See [Headless Testing in CI](../../docs/headless-testing-in-ci.md) for the
cross-platform overview of what's automatable and why. This page covers the
Android specifics.

## What's different on Android

The SDK normally measures from the live camera, but it also ships a
**testing-only video-input API**: your test decodes a recorded clip and feeds
the frames into the same pipeline a live camera would drive. An emulator's
simulated camera has no real face in it — with video input, that no longer
matters, so CI can run a **full video-fed measurement** as an instrumented
test.

The API is gated behind a Kotlin opt-in annotation so it can't leak into
production code by accident: it is an error to call it without
`@OptIn(SmartSpectraTestingApi::class)`.

```kotlin
@OptIn(SmartSpectraTestingApi::class)

sdk.setVideoInputEnabled(true)          // camera off, frames in; toggleable
sdk.addVideoFrame(bitmap, timestampUs)  // one decoded frame per call
```

While video input is enabled the SDK does not open the camera, so the test
needs no camera hardware and no `CAMERA` permission. Unlike the iOS SDK,
the Android SDK does not decode the file itself — your test supplies decoded
frames (for example via `MediaMetadataRetriever`, as below, or `MediaCodec`)
with **microsecond timestamps, strictly increasing**, taken from the clip's
own timing.

Two levels of CI coverage, pick per test:

1. **[Video-fed measurement](#option-1-the-video-fed-test)** — a full
   measurement from a recorded clip, asserting that real readings came out.
2. **[Build-integration smoke](#option-2-the-build-integration-smoke)** — no
   clip needed; proves the SDK builds, launches, and initializes.

## Option 1: The video-fed test

Drive `SmartSpectraSdk.shared` directly, the same way you would for any
[headless integration](headless-mode.md), and run it as an instrumented test
on an **Android emulator**. Feed the clip, read the `metrics` LiveData, and
assert that real readings appeared — a pulse rate and a breathing rate — not
their exact values.

```kotlin
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.SmartSpectraTestingApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoMeasurementTest {
    @OptIn(SmartSpectraTestingApi::class)
    @Test
    fun measuresFromRecordedVideo() = runBlocking {
        val sdk = SmartSpectraSdk.shared
        sdk.config.apiKey =
            InstrumentationRegistry.getArguments().getString("smartspectraApiKey").orEmpty()
        // The default request is breathing-only; ask for cardio too so a
        // pulse rate can appear. See the metrics guide.
        sdk.config.requestedMetrics =
            SmartSpectraConfig.breathingMetrics + SmartSpectraConfig.cardioMetrics

        sdk.setVideoInputEnabled(true)
        try {
            sdk.start()

            // A short clip of a well-lit, mostly still face, bundled in the
            // test APK's assets (assets are not compressed for .mp4).
            val retriever = MediaMetadataRetriever()
            InstrumentationRegistry.getInstrumentation().context.assets
                .openFd("face.mp4").use { afd ->
                    retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
                }
            val frameCount = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)!!.toInt()
            val durationUs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() * 1_000L
            val frameIntervalUs = durationUs / frameCount

            var sawPulse = false
            var sawBreathing = false
            for (index in 0 until frameCount) {
                val frame = retriever.getFrameAtIndex(index) ?: break
                sdk.addVideoFrame(frame, index * frameIntervalUs)
                sdk.metrics.value?.let { m ->
                    if (!sawPulse) sawPulse = m.cardio.pulseRateList.any { it.value > 0f }
                    if (!sawBreathing) sawBreathing = m.breathing.rateList.any { it.value > 0f }
                }
                if (sawPulse && sawBreathing) break
                // Emulators software-render the pipeline: feed no faster than
                // ~10 fps so frames aren't dropped. Timestamps carry the real
                // timing, so throttling the feed doesn't skew computed rates.
                Thread.sleep(100)
            }
            retriever.release()
            sdk.stop()

            assertTrue("no pulse reading came out of the recorded clip", sawPulse)
            assertTrue("no breathing reading came out of the recorded clip", sawBreathing)
        } finally {
            sdk.setVideoInputEnabled(false)
        }
    }
}
```

## Option 2: The build-integration smoke

If you don't have a recorded clip yet (or want a faster job on every push),
skip the video calls entirely and keep the check at smoke level — no opt-in
needed. This variant runs the normal camera path against the emulator's
simulated feed, so it grants the `CAMERA` permission:

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

The emulator's simulated camera feed has no real face in it, so don't assert
on a measurement result here: `start()` returning at all — whether it
succeeds or throws a typed `SmartSpectraException` — is the smoke signal
that the SDK built, launched, and initialized correctly end to end.

## The recorded video

Supply your own short clip and keep it in your test assets:

- Around **30–60 seconds** of a **well-lit, mostly still face**, framed like
  a real measurement — long enough for the pipeline to compute rates (a
  measurement runs about 30 seconds); a clip of only a few seconds won't
  produce readings.
- Any container/codec your decoder handles; MP4 (H.264) with
  `MediaMetadataRetriever` is a safe choice.
- Bitmaps are converted to `ARGB_8888` internally when needed.
- Timestamps are **microseconds**, strictly increasing, on one time base for
  the whole session — derive them from the clip (`index * frameIntervalUs`,
  or `MediaExtractor` sample times).

See [Android Metrics](metrics.md) for which metrics to request and how to
read them.

## A CI pipeline, in general terms

1. **Expose the API key** as a job secret and pass it to the test as an
   instrumentation argument.
2. **Run the instrumented test** on an emulator — the runner needs hardware
   acceleration (KVM on Linux) for the emulator to boot in CI.
3. **Fail the job** if the test APK doesn't build or the test fails.

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
                create("headlessVideo") {
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
name: smartspectra-android-headless-video
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

      - name: Video-fed measurement test
        run: |
          ./gradlew headlessVideoDebugAndroidTest \
            -Pandroid.testInstrumentationRunnerArguments.smartspectraApiKey="${{ secrets.SMARTSPECTRA_API_KEY }}"
```

## Limitations

- **Testing only.** The video-input API is opt-in-gated for a reason: keep
  `@OptIn(SmartSpectraTestingApi::class)` out of production code. The API
  may change without a migration path.
- **No offline mode.** Like every SmartSpectra SDK, a measurement
  authenticates against the SmartSpectra service, so the runner needs
  network access.
- **Don't mix inputs.** Within one session, feed frames exclusively via
  `addVideoFrame` — don't toggle back to the camera mid-measurement.
- **Smoke, not accuracy.** A recorded-clip run confirms the integration and
  model pipeline end to end; it is not an accuracy benchmark.
