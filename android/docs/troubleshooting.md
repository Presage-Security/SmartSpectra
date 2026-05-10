---
title: Troubleshooting
description: Solutions to common build, runtime, and integration issues with the SmartSpectra Android SDK.
---

# Android Troubleshooting

## Build & Setup

### `Could not resolve com.github.PhilJay:MPAndroidChart:v3.1.0`

The JitPack repository is missing. Add it to `settings.gradle` (or `build.gradle` for older projects):

```groovy
repositories {
    google()
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

---

### `Manifest merger failed: uses-sdk:minSdkVersion 24 cannot be smaller than version 28`

Set `minSdk 28` in your `app/build.gradle`:

```groovy
android {
    defaultConfig {
        minSdk 28
    }
}
```

---

### `Unresolved reference: AppCompatActivity` (or similar import errors)

Ensure all required imports are present in your activity file:

```kotlin
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.presagetech.smartspectra.SmartSpectraSdk
```

---

### `AAPT: error: resource mipmap/ic_launcher not found`

Remove icon references from `AndroidManifest.xml` or add the missing drawable resources. A minimal application tag that avoids this:

```xml
<application
    android:allowBackup="true"
    android:label="@string/app_name"
    android:supportsRtl="true"
    android:theme="@style/Theme.AppCompat">
```

---

### General build failures after an SDK update

1. **Clean Project** — Build → Clean Project
2. **Rebuild** — Build → Rebuild Project
3. **Sync Gradle** — File → Sync Project with Gradle Files (or the elephant icon in the toolbar)
4. **Invalidate Caches** — File → Invalidate Caches and Restart

If the `R` class stops resolving in the linter, Sync Project with Gradle Files typically fixes it.

---

## Camera & Permissions

### Camera permission denied / measurement won't start

The host app is responsible for requesting Android's runtime camera permission
before calling `sdk.start()`. The SDK does not show the permission dialog itself.
Common causes:

- Testing on an emulator — a physical device with a working camera is required.
- Permission was previously denied — guide the user to re-enable camera access in system Settings.
- `start()` was called before permission was granted — observe `sdk.error` for `SmartSpectraError(code = INPUT_UNAVAILABLE, retryable = true)`.

### Customizing the permission rationale message

Because the host app owns the permission prompt, keep the rationale string in
your app resources and show it from your onboarding or permission UI:

```xml
<string name="camera_permission_hint">Your custom message explaining why camera access is needed.</string>
```

### Requesting permission before starting the SDK

Request camera permission with the modern `ActivityResultLauncher` pattern:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

private lateinit var requestCameraPermission: ActivityResultLauncher<String>

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startMeasurement()
    }
}

private fun startMeasurement() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED
    ) {
        requestCameraPermission.launch(Manifest.permission.CAMERA)
        return
    }

    lifecycleScope.launch {
        sdk.start()
    }
}
```

If `start()` is called without permission, the SDK publishes a
`SmartSpectraError(code = INPUT_UNAVAILABLE, retryable = true)` to `sdk.error`.
Observe that error to surface recovery UI and retry after the user grants access.

---

## Authentication

### Auth errors (401 / 403)

1. Verify the API key string is correct in your code.
2. Confirm your subscription is active at [physiology.presagetech.com](https://physiology.presagetech.com).
3. Check that the device has an active internet connection — the SDK requires network access for subscription validation.

### OAuth not working in local or debug builds

Android OAuth is currently documented for Play Store releases only. For local development, internal QA, or sideloaded debug builds, use an API key instead.

If you're preparing a Play Store release, register the SHA-256 fingerprint for the signing certificate used by that release, then re-download `presage_services.xml`.
Run:

```bash
keytool -list -v -keystore <path-to-keystore> -alias <key-alias> -storepass <store-password> | grep SHA256
```

Register that fingerprint under **Account → Registered App for OAuth** alongside your package name, then re-download and replace `presage_services.xml`.

> **Note:** Each package name can only be registered once. You cannot create multiple OAuth configs for the same package name.

---

## Getting Help

- Email: [support@presagetech.com](mailto:support@presagetech.com)
- [Submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues)
- [FAQ and developer portal](https://physiology.presagetech.com)
