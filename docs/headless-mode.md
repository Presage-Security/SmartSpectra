---
title: Headless Mode
description: Run vital sign processing without the SDK's built-in UI — useful for background monitoring or custom interfaces.
---

Headless mode lets you run the SmartSpectra processing pipeline without the built-in measurement UI. Use this when you want to:

- Monitor vitals in the background while showing other content
- Build a completely custom measurement interface
- Integrate into an existing camera pipeline

## Processing Status

All platforms expose the same lifecycle states:

| Status | Meaning |
|--------|---------|
| **Idle** | Pipeline is not running |
| **Starting** | Pipeline is initializing |
| **Running** | Actively measuring — data is flowing |
| **Stopping** | Teardown in progress, will return to Idle |
| **Error** | Something went wrong |

## Platform Examples


### Android
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


### iOS
Use `SmartSpectraSDK.shared` directly for headless processing:

```swift
import SwiftUI
import SmartSpectra

struct HeadlessExample: View {
    private let sdk = SmartSpectraSDK.shared
    @State private var isMonitoring = false
    @State private var showCameraFeed = false

    init() {
        sdk.config.apiKey = "YOUR_API_KEY"
        sdk.config.cameraPosition = .front
    }

    var body: some View {
        VStack {
            if let metrics = sdk.metrics, metrics.hasBreathing,
               let rate = metrics.breathing.rate.last {
                Text("Breathing: \(Int(rate.value.rounded())) bpm")
            }
            if let metrics = sdk.metrics, metrics.hasCardio,
               let pulse = metrics.cardio.pulseRate.last {
                Text("Pulse: \(Int(pulse.value.rounded())) bpm")
            }
            Text("Status: \(sdk.validationStatus?.hint ?? "")")

            if let error = sdk.error {
                Text(error.message)
                    .foregroundStyle(.red)
            }

            Toggle("Camera Preview", isOn: $showCameraFeed)
                .onChange(of: showCameraFeed) {
                    sdk.config.imageOutputEnabled = showCameraFeed
                }

            if showCameraFeed, let image = sdk.imageOutput {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
            }

            Button(isMonitoring ? "Stop" : "Start") {
                isMonitoring.toggle()
                if isMonitoring {
                    Task { try? await sdk.start() }
                } else {
                    Task { try? await sdk.stop() }
                }
            }
            // Disable the start button when the SDK has an unrecoverable
            // input-unavailable error (e.g. camera permission denied).
            // All other error states either recover on `start()` or
            // return a throwable error that you can surface to the user.
            .disabled(sdk.error?.code == .inputUnavailable && !isMonitoring)
        }
    }
}
```


### C++
The C++ SDK is headless by default — there is no built-in UI.

```cpp
SmartSpectraConfig config;
config.api_key = "YOUR_API_KEY";
config.requested_metrics = SmartSpectraConfig::BreathingMetrics();

SmartSpectra spectra(std::move(config));

spectra.SetOnMetrics([](const presage::smartspectra::Metrics& metrics, int64_t ts) {
    // Process metrics
});

spectra.SetOnVideoOutput([](const presage::smartspectra::FrameBuffer& frame, int64_t ts) {
    // Optional: render frame in your own UI
});

if (const auto source_error =
        spectra.UseCamera().SetResolution(1280, 720).SetFps(30).Build();
    !source_error.ok()) {
    // Handle setup error
} else if (const auto err = spectra.Start(); !err.ok()) {
    // Handle startup error
}

// ... run until done ...

spectra.Stop();
```

For custom frame input instead of the built-in camera:

```cpp
std::shared_ptr<CustomInput> handle;
if (auto err = spectra.UseCustomInput().Build(handle); !err.ok()) {
    // Handle setup error: err.FullMessage()
}
// Feed frames manually:
// handle->Send(frame, timestamp_us);
// timestamp_us must be strictly monotonic.
```


## Accessing Metrics in Headless Mode

Metrics work the same way in headless mode as with the built-in UI. See the platform guide for details on subscribing to and reading edge metrics data:

Configure metrics: [Android](android/metrics.md), [C++](cpp/metrics.md).
