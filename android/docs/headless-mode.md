---
title: Headless Mode on Android
description: Use SmartSpectra Android lifecycle and metrics LiveData with your own UI, or submit custom camera frames through direct buffers, Bitmap, and CameraX ImageProxy.
sidebarTitle: Headless Mode
---

# Headless Mode (Android)

The SDK doesn't ship UI. `SmartSpectraSdk.shared` exposes LiveData for
`processingStatus`, `validationStatus`, `metrics`, `error`, and
(optionally) `imageOutput`. Observe them from your Fragment or Activity
with `observe(viewLifecycleOwner)`. The sample apps include a measurement
UI; your own integration looks however you want.

Use this when you want to:

- Monitor vitals in the background while the app shows other content
- Build a custom measurement UI

## Processing Status

Lifecycle states:

| Status | Meaning |
| --- | --- |
| **Idle** | Pipeline is not running |
| **Starting** | Pipeline is initializing |
| **Running** | Actively measuring — data is flowing |
| **Stopping** | Teardown in progress, will return to Idle |
| **Error** | Something went wrong |

## Example

Use `SmartSpectraSdk.shared` directly for headless processing:

```kotlin
import android.Manifest
import android.os.Bundle
import android.content.pm.PackageManager
import android.view.View
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.presagetech.smartspectra.CameraPosition
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraError
import com.presagetech.smartspectra.SmartSpectraSdk
import kotlinx.coroutines.launch

class HeadlessFragment : Fragment() {
    private val sdk by lazy {
        SmartSpectraSdk.shared.apply {
            config.apiKey = "YOUR_API_KEY"
            config.cameraPosition = CameraPosition.FRONT
            config.imageOutputEnabled = true
        }
    }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startMonitoring()
            } else {
                showCameraPermissionUi()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val previewImage: ImageView = view.findViewById(R.id.headless_preview_image)

        sdk.processingStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                ProcessingStatus.IDLE -> showIdleUi()
                ProcessingStatus.STARTING -> showLoadingUi()
                ProcessingStatus.RUNNING -> showRecordingUi()
                ProcessingStatus.STOPPING -> showStoppingUi()
                ProcessingStatus.ERROR -> showErrorUi()
            }
        }
        sdk.validationStatus.observe(viewLifecycleOwner) { status ->
            updateStatusHint(status?.hint.orEmpty())
        }
        sdk.imageOutput.observe(viewLifecycleOwner) { bitmap ->
            previewImage.setImageBitmap(bitmap)
        }
        sdk.metrics.observe(viewLifecycleOwner) { metrics ->
            renderMetrics(metrics)
        }
        sdk.error.observe(viewLifecycleOwner) { error ->
            if (error?.code == SmartSpectraError.Code.INPUT_UNAVAILABLE) {
                showCameraPermissionUi()
            }
        }
    }

    private fun startMonitoring() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            sdk.start()
        }
    }

    private fun stopMonitoring() {
        viewLifecycleOwner.lifecycleScope.launch {
            sdk.stop()
        }
    }
}
```

## Caller-owned camera and custom input

Use `sdk.useCustomInput()` when your app already captures video, such as a
camera shared with a video call, or supplies decoded video frames. In this
mode the SDK does not discover, open, or control a camera and does not
require camera permission. Your app remains responsible for permissions
needed by its own capture code.

Select the input while stopped, await `start()`, and then submit frames:

```kotlin
import com.presagetech.smartspectra.FrameSubmissionResult
import com.presagetech.smartspectra.FrameTransform
import com.presagetech.smartspectra.SmartSpectraSdk

val sdk = SmartSpectraSdk.shared
sdk.config.apiKey = "YOUR_API_KEY"
val input = sdk.useCustomInput(frameTransform = FrameTransform.NONE)

sdk.start() // Suspends until ready; call from a coroutine.
try {
    // Supply an upright Bitmap and its capture timestamp in microseconds.
    when (val result = input.sendFrame(bitmap, timestampUs)) {
        FrameSubmissionResult.Accepted -> Unit
        is FrameSubmissionResult.Rejected -> showFrameError(result.error)
    }
} finally {
    sdk.stop()
}
```

`Accepted` means the frame was submitted, not that its measurement has
finished. Continue observing `sdk.metrics`, `sdk.validationStatus`, and
`sdk.error` as with SDK-owned capture. A rejected submission carries a typed
`SmartSpectraError`; do not assume every submitted frame was accepted.

### Buffers and image ownership

Use `VideoFrame.Packed` for a direct pixel buffer, or `VideoFrame.Yuv420`
for three YUV planes:

```kotlin
import com.presagetech.smartspectra.FramePlane
import com.presagetech.smartspectra.VideoFrame

val frame = VideoFrame.Yuv420(
    width = width,
    height = height,
    y = FramePlane(yBuffer, yRowStride, 1),
    u = FramePlane(uBuffer, uRowStride, uvPixelStride),
    v = FramePlane(vBuffer, vRowStride, uvPixelStride),
)
val result = input.sendFrame(frame, timestampUs)
```

For packed pixels, supply `VideoFrame.Packed(buffer, width, height,
rowStride, pixelFormat)` with a typed `PixelFormat`. Dimensions describe
the supplied pixels; row and pixel strides are measured in bytes.
Buffers must be direct `ByteBuffer` instances. Each buffer's current
`position` identifies the first byte and its `limit` bounds the readable
region. Submission leaves those values unchanged.

The caller owns the buffers. Keep their pixels unchanged until
`sendFrame()` returns; they can then be reused. The Bitmap overload is a
convenience for callers without direct buffers.

The CameraX overload, `input.sendFrame(imageProxy)`, borrows an
`ImageProxy` and uses its capture timestamp. It accepts only
`YUV_420_888` with a full-image crop. Unrotated images borrow the planes
directly; rotated images require a Bitmap copy. Always close the image
yourself, including after rejection:

```kotlin
try {
    val result = input.sendFrame(imageProxy)
    if (result is FrameSubmissionResult.Rejected) {
        showFrameError(result.error)
    }
} finally {
    imageProxy.close()
}
```

### CameraX capture example

The following helper binds a CameraX analyzer and submits each image to the SDK.
Configure SDK authentication and grant camera permission before calling `start()`.
Supply your `ProcessCameraProvider`, a worker `Executor`, and an error callback.
Call `start()` and `stop()` serially from a coroutine, passing your `LifecycleOwner`,
`CameraSelector`, and display rotation to `start()`. The error callback runs on the
analyzer's worker thread; dispatch UI updates to the main thread.

The helper closes each image after submission and unbinds only its own capture
use case. Your app owns the supplied executor and its shutdown.

```kotlin
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.presagetech.smartspectra.FrameSubmissionResult
import com.presagetech.smartspectra.SmartSpectraError
import com.presagetech.smartspectra.SmartSpectraSdk
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class CustomCameraInput(
    private val sdk: SmartSpectraSdk,
    private val provider: ProcessCameraProvider,
    private val worker: Executor,
    private val onFrameError: (SmartSpectraError) -> Unit,
) {
    private var analysis: ImageAnalysis? = null

    suspend fun start(owner: LifecycleOwner, selector: CameraSelector, displayRotation: Int) {
        check(analysis == null) { "Capture is already started" }
        val input = sdk.useCustomInput()
        sdk.start()
        try {
            withContext(Dispatchers.Main.immediate) {
                val capture = ImageAnalysis.Builder()
                    .setTargetRotation(displayRotation)
                    .setOutputImageRotationEnabled(true)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis = capture
                capture.setAnalyzer(worker) { image ->
                    try {
                        val result = input.sendFrame(image)
                        if (result is FrameSubmissionResult.Rejected) onFrameError(result.error)
                    } finally {
                        image.close()
                    }
                }
                provider.bindToLifecycle(owner, selector, capture)
            }
        } catch (error: Exception) {
            withContext(NonCancellable) { stop() }
            throw error
        }
    }

    suspend fun stop() {
        withContext(Dispatchers.Main.immediate) {
            analysis?.let { capture ->
                capture.clearAnalyzer()
                provider.unbind(capture)
            }
            analysis = null
        }
        sdk.stop()
    }
}
```

### Orientation and timing

With `FrameTransform.NONE`, submit upright pixels in the orientation you
want measured. The Bitmap and buffer overloads have no rotation metadata;
normalize their pixels or select a fixed `FrameTransform`. The `ImageProxy`
overload applies `imageInfo.rotationDegrees` (0, 90, 180, or 270 degrees)
first, then the session transform; other rotation values are rejected.
Avoid applying the same rotation twice. Use the same orientation convention
throughout the run.

Supply strictly increasing timestamps in **microseconds**, on one
monotonic timeline per run. Values must be nonnegative and less than
`Long.MAX_VALUE - 2`. For camera timestamps in nanoseconds, divide
by `1_000L`; for decoded video use the clip's presentation timestamps.
A gap greater than two seconds is rejected. After a feed interruption
that creates such a gap, call `sdk.stop()` and then `sdk.start()` before
resuming submission. Do not rewrite live timestamps to hide the gap.

### Switching sources and reusing handles

Call `useCustomInput()` and `useCamera()` only while stopped. Source
selection during processing throws `SmartSpectraException`. To return to
SDK-owned capture, stop, call `sdk.useCamera()`, then start; the SDK uses
your existing camera configuration.

Handles survive stop/start and reset. Selecting another source—including
another `useCustomInput()` call—invalidates them. Frames submitted while
stopped or through an invalid handle are rejected.

Reset stops processing and clears measurement state while preserving the
selected input and transform. Await reset, then start before submitting more frames.

## Reading Metrics

`sdk.metrics` is the same LiveData property here as in any other
integration — there's no separate "headless" API. See
[Android Metrics](metrics.md) for the metric request configuration and the
field-by-field reading guide.
