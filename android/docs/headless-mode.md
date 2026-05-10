---
title: Headless Mode
description: Wire SmartSpectra SDK lifecycle and metrics LiveData into your own Android UI — useful for background monitoring or custom interfaces.
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

Lifecycle states (same across all platforms):

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

## Reading Metrics

`sdk.metrics` is the same LiveData property here as in any other
integration — there's no separate "headless" API. See
[Android Metrics](metrics.md) for the metric request configuration and the
field-by-field reading guide.
