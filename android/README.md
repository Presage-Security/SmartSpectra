# Module SmartSpectra Android SDK

## Integration Guide Overview

This guide provides instructions for integrating and utilizing the Presage SmartSpectra SDK publicly hosted on Maven in your Android application to measure physiology metrics.

The app contained in this repo is an example of pulling and using the SmartSpectra SDK from Maven. It should run out of the box as long as your API key is provided.

## Table of Contents

- [Quick Start - Hello Vitals](#quick-start---hello-vitals)
- [Prerequisites](#prerequisites)
- [Demo App Guide](#demo-app-guide)
- [Advanced Integration](#advanced-integration)
- [Usage](#usage)
- [Bugs & Troubleshooting](#bugs--troubleshooting)

## Quick Start - Hello Vitals

Get measuring vitals in under 5 minutes! This minimal example gets you up and running with heart rate and breathing measurements.

### Prerequisites

- **Android Studio Giraffe or later**
- **Minimum SDK level 26**
- **API Key** from [physiology.presagetech.com](https://physiology.presagetech.com)

### Hello Vitals Example

Create a new Android project and add the SmartSpectra dependency.

**settings.gradle.kts (or build.gradle for older projects):**

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Required: JitPack repository for MPAndroidChart dependency
        maven { url = uri("https://jitpack.io") }
    }
}
```

**app/build.gradle.kts:**

```gradle
dependencies {
    implementation("com.presagetech:smartspectra:1.0.26")
}
```

**MainActivity.kt:**

```kotlin
package com.example.smartspectraexample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.presagetech.smartspectra.SmartSpectraView
import com.presagetech.smartspectra.SmartSpectraSdk

class MainActivity : AppCompatActivity() {
    private lateinit var smartSpectraView: SmartSpectraView

    // Replace with your API key from https://physiology.presagetech.com
    private val smartSpectraSdk = SmartSpectraSdk.getInstance().apply {
        setApiKey("YOUR_API_KEY")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        smartSpectraView = findViewById(R.id.smart_spectra_view)
    }
}
```

**res/layout/activity_main.xml:**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.presagetech.smartspectra.SmartSpectraView
        android:id="@+id/smart_spectra_view"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

</LinearLayout>
```

### Run and Test

1. **Build and run** your app on a physical device (camera required)
2. **Allow camera permission** when prompted
3. **Tap the measurement button** and follow on-screen instructions
4. **View your vitals** - heart rate, breathing rate, and more!

The SDK automatically handles camera permissions, measurement UI, and result display.

![Android Quickstart Demo](media/android-quickstart.gif)

### Next Steps

- **Customize measurements**: See [Advanced Integration](#advanced-integration) for configuration options
- **Access raw metrics**: Learn about [metrics extraction](#extracting-and-using-metrics-data)
- **Try the demo app**: Follow the [Demo App Guide](#demo-app-guide) for a full-featured example

### Using Git LFS

This repository utilizes Git Large File Storage (LFS) for managing binary files such as .so, .jar, .tflite, etc.

To use Git LFS:

1. Ensure Git LFS is installed [docs.github.com](https://docs.github.com/en/repositories/working-with-files/managing-large-files/installing-git-large-file-storage).
2. Run `git lfs install`. This step is only required once after installation.
3. If you've installed Git LFS after already cloning this repository, execute `git lfs pull`.
Git LFS functions similarly to `.gitignore`. The configuration for files managed as LFS objects is in the `.gitattributes` file.

### Camera Permission Setup

The SmartSpectra SDK requires camera access to measure vitals. The SDK handles camera permissions automatically:

#### Automatic Permission Declaration

The SmartSpectra SDK automatically declares the required camera permission in its manifest, so you **don't need to manually add** it to your app's `AndroidManifest.xml`:

```xml
<!-- Already included in the SDK manifest -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="true" />
```

#### Runtime Permission Handling

The SDK automatically handles runtime permission requests when you launch `SmartSpectraView` or `SmartSpectraButton`:

1. On first launch, the SDK checks for camera permission
2. If not granted, it displays a permission request screen with the rationale
3. Users can either "Allow" the permission or open system settings
4. Once granted, the SDK proceeds to the measurement screen

#### Customizing the Permission Rationale (Optional)

To customize the permission rationale message shown to users, add this string to your app's `res/values/strings.xml`:

```xml
<string name="camera_permission_hint">Your custom message explaining why camera access is needed.</string>
```

The default message is: "Metrics measurements require camera permission."

#### Pre-Requesting Permissions (Optional)

If you want to request camera permission before launching the SDK (e.g., during onboarding), you can do so manually:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

// Check if permission is already granted
if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
    == PackageManager.PERMISSION_GRANTED) {
    // Permission already granted, proceed with SDK
} else {
    // Request permission using Activity Result API
    val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, can now use SmartSpectra SDK
        } else {
            // Permission denied, handle accordingly
        }
    }

    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
}
```

### Authentication

You'll need either an API key or Oauth config to use the SmartSpectra Android SDK. You can find instructions on how to do that in the [Authentication Guide](../docs/authentication.md)

- **API Key Option**: Add your API key to the [MainActivity.kt](samples/demo-app/src/main/java/com/presagetech/smartspectra_example/MainActivity.kt) file by replacing the placeholder `"YOUR_API_KEY"`.
- **Oauth Config Option**: Currently only supported for playstore releases. With the downloaded Oauth xml config from obtained during [Authentication](../docs/authentication.md) and place into your app's `src/main/res/xml` directory.
![xml file location in repo](media/xml_location_in_repo.png)

> **NOTE**
> Oauth config is currently only supported for playstore releases.

## Demo App Guide

1. Clone the repository and open the Android project in Android Studio:

    ```bash
    git clone https://github.com/Presage-Security/SmartSpectra/
    ```

2. Open the project by selecting `smartspectra/android` folder in Android Studio.
3. Add your API key to the [MainActivity.kt](samples/demo-app/src/main/java/com/presagetech/smartspectra_example/MainActivity.kt) file by replacing the placeholder `"YOUR_API_KEY"` with your actual API key.
4. The workspace setup contains internal and extenal build flavor. If you want to build with the local sdk folder, select `internalDebug` build flavor from the build variants tab.
5. Connect your Android device to your computer.
6. Select your device as the target in Android Studio.
7. Click the "Run" button in Android Studio to build and run the demo app on your device.
8. Follow the on-screen instructions in the app to conduct a measurement and view the results.

## Advanced Integration

To integrate the SmartSpectra SDK into your Android project, follow these steps:

### Repository Configuration

Add the JitPack repository to your project's `settings.gradle` (for newer projects) or `build.gradle` (for older projects):

**settings.gradle:**

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Required: JitPack repository for MPAndroidChart dependency
        maven { url 'https://jitpack.io' }
    }
}
```

**Or for older projects in build.gradle:**

```gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        // Required: JitPack repository for MPAndroidChart dependency
        maven { url 'https://jitpack.io' }
    }
}
```

### Dependency Declaration

Add the SmartSpectra dependency to your app's `build.gradle` file:

```gradle
android {
    compileSdk 34

    defaultConfig {
        minSdk 26  // Required: SDK minimum version
        targetSdk 34
        // ... other configs
    }
}

dependencies {
    implementation("com.presagetech:smartspectra:1.0.26")
}
```

## Kotlin API Overview

The library exposes a few key classes:

- **SmartSpectraSdk**: singleton for configuring and receiving measurement data.
- **SmartSpectraView**: composite view containing the measurement button and result display.
- **SmartSpectraButton** and **SmartSpectraResultView** can also be used individually.
- **ScreeningPlotView**: optional chart view for continuous mode.

## Usage

### Example Code

### Initialize Components and Configure SDK Parameters

In your activity or fragment, initialize the `smartSpectraSdk` and `SmartSpectraView` (The view consists of a checkup button and result view). Also, configure the SmartSpectra SDK parameters such as the API key, SmartSpectra mode, camera position, and measurement duration.

You can obtain an Oauth config or API key from PresageTech's developer portal (<https://physiology.presagetech.com/>).

Here's a minimal example of how to use our SDK in your Android app:

```kotlin
// SmartSpectra SDK Specific Imports
import com.presagetech.smartspectra.SmartSpectraView
import com.presagetech.smartspectra.SmartSpectraSdk

class MainActivity : AppCompatActivity() {
    private lateinit var smartSpectraView: SmartSpectraView

    // (Required) Authentication. Only need to use one of the two options: API Key or OAuth below
    // Authentication with OAuth is currently only supported for apps in the Play Store
    // Option 1: (Authentication with API Key) Set the API key. Obtain the API key from https://physiology.presagetech.com. Leave default or remove if you want to use OAuth. OAuth overrides the API key.
    private var apiKey = "YOUR_API_KEY"

    // Option 2: (OAuth) If you want to use OAuth, copy the OAuth config (`presage_services.xml`) from PresageTech's developer portal (<https://physiology.presagetech.com/>) to your src/main/res/xml/ directory.
    // No additional code is needed for OAuth.

    private val smartSpectraSdk: SmartSpectraSdk = SmartSpectraSdk.getInstance().apply {
        //Required configurations: Authentication
        setApiKey(apiKey) // Use this if you are authenticating with an API key
        // If OAuth is configured, it will automatically override the API key
    }

    override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            // Setting up SmartSpectra Results/Views
            smartSpectraView = findViewById(R.id.smart_spectra_view)

    }
}
```

If you want further customization, you can add UI elements to change the SmartSpectra mode, measurement duration, and camera position at runtime. See examples of how you can implement UI elements or programmatically change the SmartSpectra mode, measurement duration, and camera position at runtime in [MainActivity.kt](samples/demo-app/src/main/java/com/presagetech/smartspectra_example/MainActivity.kt).

```kotlin
// SmartSpectra SDK Specific Imports
import com.presagetech.smartspectra.SmartSpectraView
import com.presagetech.smartspectra.SmartSpectraMode
import com.presagetech.smartspectra.SmartSpectraSdk

class MainActivity : AppCompatActivity() {
    private lateinit var smartSpectraView: SmartSpectraView

    // define smartSpectra mode to SPOT or CONTINUOUS. Defaults to CONTINUOUS when not set
    private var smartSpectraMode = SmartSpectraMode.CONTINUOUS
    // define front or back camera to use
    private var cameraPosition = CameraSelector.LENS_FACING_FRONT
    // measurement duration (valid ranges are between 20.0 and 120.0) Defaults to 30.0 when not set
    // For continuous SmartSpectra mode currently defaults to infinite
    private var measurementDuration = 30.0

    // (Required) Authentication. Only need to use one of the two options: API Key or OAuth below
    // Authentication with OAuth is currently only supported for apps in the Play Store
    // Option 1: (Authentication with API Key) Set the API key. Obtain the API key from https://physiology.presagetech.com. Leave default or remove if you want to use OAuth. OAuth overrides the API key.
    private var apiKey = "YOUR_API_KEY"

    // Option 2: (OAuth) If you want to use OAuth, copy the OAuth config (`presage_services.xml`) from PresageTech's developer portal (<https://physiology.presagetech.com/>) to your src/main/res/xml/ directory.
    // No additional code is needed for OAuth.

    // get instance of SmartSpectraSdk and apply optional configurations
    private val smartSpectraSdk: SmartSpectraSdk = SmartSpectraSdk.getInstance().apply {
        //Required configurations: Authentication
        setApiKey(apiKey) // Use this if you are authenticating with an API key
        // If OAuth is configured, it will automatically override the API key

        // Optional configurations
        // Valid range for spot time is between 20.0 and 120.0
        setMeasurementDuration(measurementDuration)
        setShowFps(false)
        //Recording delay defaults to 3 if not provided
        setRecordingDelay(3)

        // smartSpectra mode (SPOT or CONTINUOUS. Defaults to CONTINUOUS when not set)
        setSmartSpectraMode(smartSpectraMode)

        // select camera (front or back, defaults to front when not set)
        setCameraPosition(cameraPosition)

        // Optional: Only need to set it if you want to access metrics to do any processing
        setMetricsBufferObserver { metricsBuffer ->
            handleMetricsBuffer(metricsBuffer)
        }

        // Optional: Only need to set it if you want to access edge metrics and dense face landmarks
        setEdgeMetricsObserver { edgeMetrics ->
            handleEdgeMetrics(edgeMetrics)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            // Setting up SmartSpectra Results/Views
            smartSpectraView = findViewById(R.id.smart_spectra_view)

    }
}
```

## SmartSpectra Mode

The SmartSpectra SDK supports two modes:

- Spot Mode (`SmartSpectraMode.SPOT`): In this mode, the SDK will take a single measurement for the specified duration.
- Continuous Mode (`SmartSpectraMode.CONTINUOUS`): In this mode, the SDK will continuously take measurements for the specified duration. Currently defaults to infinite duration and manual stop. During continuous mode, live pulse and breathing rate is displayed; along with live plots of the pulse and breathing trace.

## Switching SmartSpectra Mode, Measurement Duration, and Camera Position at Runtime

See examples of how you can implement UI elements or programmatically change the SmartSpectra mode, measurement duration, and camera position at runtime in [MainActivity.kt](samples/demo-app/src/main/java/com/presagetech/smartspectra_example/MainActivity.kt).

### Extracting and Using Metrics Data

To retrieve and use metrics, you can attach a `metricsBufferObserver` to get the metrics to process. Please refer to [MainActivity.kt](samples/demo-app/src/main/java/com/presagetech/smartspectra_example/MainActivity.kt) for example usage and plotting of different metrics such as pulse rate, breathing rates, etc.

```kotlin
import com.presage.physiology.proto.MetricsProto.MetricsBuffer

private val smartSpectraSdk: SmartSpectraSdk = SmartSpectraSdk.getInstance().apply {
    //...
    //...
    //...
    //...

    // Optional: Only need to set it if you want to access metrics to do any processing
    setMetricsBufferObserver { metricsBuffer ->
        handleMetricsBuffer(metricsBuffer)
    }
}

override fun onCreate(savedInstanceState: Bundle?) {
    //...
    //...
    //...
    //...
}

private fun handleMetricsBuffer(metrics: MetricsBuffer) {
    // get the relevant metrics
    val pulse = metrics.pulse
    val breathing = metrics.breathing

    // Plot the results

    // Pulse plots
    if (pulse.traceCount > 0) {
        addChart(pulse.traceList.map { Entry(it.time, it.value) },  "Pulse Pleth", false)
    }
    // Breathing plots
    if (breathing.upperTraceCount > 0) {
        addChart(breathing.upperTraceList.map { Entry(it.time, it.value) }, "Breathing Pleth", false)
    }
    // TODO: See examples of plotting other metrics in MainActivity.kt
}

```

For dense face landmarks, you can access them through the edge metrics system. The dense face landmarks are now available through the edge metrics for continuous mode processing. To see a complete example of using a scatter chart to visualize the mesh points, please refer to [MainActivity.kt](samples/demo-app/src/main/java/com/presagetech/smartspectra_example/MainActivity.kt). Reference to the index of the mesh points and their corresponding face landmarks can be seen [in face landmark reference](https://storage.googleapis.com/mediapipe-assets/documentation/mediapipe_face_landmark_fullsize.png)

```kotlin
import com.presage.physiology.proto.MetricsProto.Metrics

private val smartSpectraSdk: SmartSpectraSdk = SmartSpectraSdk.getInstance().apply {
    //...
    //...
    //...
    //...
    // Optional: Only need to set it if you want to access edge metrics and dense face landmarks
    setEdgeMetricsObserver { edgeMetrics ->
        handleEdgeMetrics(edgeMetrics)
    }
}

override fun onCreate(savedInstanceState: Bundle?) {
    //...
    //...
    //...
    //...
}

private fun handleEdgeMetrics(edgeMetrics: Metrics) {
    // Handle dense face landmarks from edge metrics
    if (edgeMetrics.hasFace() && edgeMetrics.face.landmarksCount > 0) {
        // Get the latest landmarks from edge metrics
        val latestLandmarks = edgeMetrics.face.landmarksList.lastOrNull()
        latestLandmarks?.let { landmarks ->
            val meshPoints = landmarks.valueList.map { landmark ->
                Pair(landmark.x.toInt(), landmark.y.toInt())
            }
            handleMeshPoints(meshPoints)
        }
    }
}

private fun handleMeshPoints(meshPoints: List<Pair<Int, Int>>) {
    Timber.d("Observed mesh points: ${meshPoints.size}")
    // TODO: Update UI or handle the points as needed. See examples of plotting in MainActivity.kt
}
```

### Detailed `MetricsBuffer` Class Descriptions

> **TIP**
> If you need to use the types directly, the `MetricsBuffer` and corresponding classes are under the `com.presage.physiology.proto.MetricsProto` namespace. You can import it from `MetricsProto.MetricsBuffer` for easier usage:
>
> ```kotlin
> import com.presage.physiology.proto.MetricsProto.MetricsBuffer
> ```
>
> **TIP**
> `MetricsBuffer` is generated from a Protobuf message. The full descriptions of `MetricsBuffer` and `Metrics` messages, as well as the messages composing them, can currently be found in [SmartSpectra Protocol Buffer Reference](https://docs.physiology.presagetech.com/cpp/protobuf_reference.html).

`MetricsBuffer` contains the following parent classes:

```kotlin
class MetricsBuffer {
    var pulse: Pulse
    var breathing: Breathing
    var bloodPressure: BloodPressure
    var face: Face
    var metadata: Metadata
}
```

### Measurement Types

- **`Measurement` Class**: Represents a measurement with time and value:

```kotlin
class Measurement {
    var time: Float
    var value: Float
    var stable: Boolean
}
```

- **`MeasurementWithConfidence` Class**: Includes confidence with the measurement:

```kotlin
class MeasurementWithConfidence {
    var time: Float
    var value: Float
    var stable: Boolean
    var confidence: Float
}
```

- **`DetectionStatus` Class**: Used for events like apnea or face detection (blinking/talking):

```kotlin
class DetectionStatus {
    var time: Float
    var detected: Boolean
    var stable: Boolean
}
```

#### Metric Types

- **`Pulse` Class**: Contains pulse-related measurements, including rate, trace, and strict values:

```kotlin
class Pulse {
    var rateList: List<MeasurementWithConfidence>
    var traceList: List<Measurement>
    var strict: Strict
}
```

- **`Breathing` Class**: Handles breathing-related data with upper and lower traces, amplitude, apnea status, and other metrics:

```kotlin
class Breathing {
    var rateList: List<MeasurementWithConfidence>
    var upperTraceList: List<Measurement>
    var lowerTraceList: List<Measurement>
    var amplitudeList: List<Measurement>
    var apneaList: List<DetectionStatus>
    var respiratoryLineLengthList: List<Measurement>
    var inhaleExhaleRatioList: List<Measurement>
    var strict: Strict
}
```

- **`BloodPressure` Class**: Handles blood pressure measurements:

> [!CAUTION]
> Currently not available publicly, currently returned results are a duplicate of pulse pleth

```kotlin
class BloodPressure {
    var phasicList: List<MeasurementWithConfidence>
}
```

- **`Face` Class**: Includes detection statuses for blinking and talking:

```kotlin
class Face {
    var blinkingList: List<DetectionStatus>
    var talkingList: List<DetectionStatus>
}
```

- **`Metadata` Class**: Includes metadata information:

```kotlin
class Metadata {
    var id: String
    var uploadTimestamp: String
    var apiVersion: String
}
```

#### Encoding and Decoding Protobuf Messages

To serialize `MetricsBuffer` into binary format:

```kotlin
try {
    val data: ByteArray = metricsBuffer.toByteArray()
    // Send `data` to your backend or save it
} catch (e: Exception) {
    Timber.e("Failed to serialize metrics: ${e.message}")
}
```

To decode binary protobuf data into `MetricsBuffer`:

```kotlin
try {
    val decodedMetrics = MetricsBuffer.parseFrom(data)
    // Use `decodedMetrics` as needed
} catch (e: Exception) {
    Timber.e("Failed to decode metrics: ${e.message}")
}
```

## Bugs & Troubleshooting

### Common Build Issues

#### Could not resolve dependency errors

**Problem**: `Could not resolve com.github.PhilJay:MPAndroidChart:v3.1.0`

**Solution**: Add the JitPack repository to your `settings.gradle` or `build.gradle`:

```gradle
repositories {
    google()
    mavenCentral()
    maven { url 'https://jitpack.io' }  // Required for MPAndroidChart
}
```

#### Manifest merger failed - minSdkVersion error

**Problem**: `Manifest merger failed : uses-sdk:minSdkVersion 24 cannot be smaller than version 26`

**Solution**: Update your app's `build.gradle` to use minSdk 26:

```gradle
android {
    defaultConfig {
        minSdk 26  // SmartSpectra SDK requires minimum API 26
    }
}
```

#### Missing imports errors

**Problem**: `Unresolved reference: AppCompatActivity` or similar import errors

**Solution**: Ensure all required imports are included:

```kotlin
package com.example.smartspectraexample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.presagetech.smartspectra.SmartSpectraView
import com.presagetech.smartspectra.SmartSpectraSdk
```

#### Resource not found errors

**Problem**: `AAPT: error: resource mipmap/ic_launcher not found`

**Solution**: Remove icon references from AndroidManifest.xml or add the missing resources:

```xml
<application
    android:allowBackup="true"
    android:label="@string/app_name"
    android:supportsRtl="true"
    android:theme="@style/Theme.AppCompat">
```

### Runtime Issues

#### Camera permission denied

**Problem**: App crashes or fails to start measurement when camera permission is denied

**Solution**: The SDK handles permissions automatically, but ensure you're testing on a physical device with a working camera.

#### API authentication errors

**Problem**: Authentication failures or 401/403 errors

**Solution**:

1. Verify your API key is correct in MainActivity.kt
2. Ensure you have an active subscription at [physiology.presagetech.com](https://physiology.presagetech.com)
3. Check your internet connection

### General Troubleshooting

If you encounter build issues after updating the SDK:

1. **Clean and rebuild**: `Build → Clean Project`, then `Build → Rebuild Project`
2. **Sync Gradle files**: Click the elephant icon in the toolbar or `File → Sync Project with Gradle Files`
3. **Invalidate caches**: `File → Invalidate Caches and Restart`

![GradleSyncError.png](media/GradleSyncError.png)

If the `R` value stops getting resolved by the AndroidStudio linter, run `Sync Project With Gradle Files` (the elephant icon in the toolbar).

### Getting Help

For additional support:

- Contact <[support@presagetech.com](mailto:support@presagetech.com)>
- [Submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues)
- Check the [FAQ and documentation](https://physiology.presagetech.com)
