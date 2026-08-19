---
title: Android Troubleshooting
description: Solutions to common build, runtime, and integration issues with the SmartSpectra Android SDK.
sidebarTitle: Troubleshooting
---

# Android Troubleshooting

## Build & Setup

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

### `AAR requires API 36.1` (or a compile SDK version that is too low)

SmartSpectra 3.2.x requires `compileSdk 36.1` or later. Update your app module
and Android build tooling, then sync Gradle again:

```kotlin
android {
    compileSdk = 36
    compileSdkMinor = 1
}
```

Use AGP 8.10.1 or later and Kotlin 2.2.x. If your project uses Gradle 9, use an
AGP 9.x release. See [Option 1: API Key](option-1-api-key.md#requirements)
for the complete compatible toolchain.

---

### `Unresolved reference: ComponentActivity` (or similar import errors)

The quickstarts extend `ComponentActivity`, which comes from
`androidx.activity:activity-ktx`. Confirm that dependency is declared, then check
the imports in your activity file:

```kotlin
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.presagetech.smartspectra.SmartSpectraSdk
```

If you are following older material that used `AppCompatActivity`, either switch to
`ComponentActivity` or add `androidx.appcompat:appcompat` yourself — the SDK does not
pull it in.

---

### `AAPT: error: resource mipmap/ic_launcher not found`

Remove icon references from `AndroidManifest.xml` or add the missing drawable resources. A minimal application tag that avoids this:

```xml
<application
    android:allowBackup="true"
    android:label="@string/app_name"
    android:supportsRtl="true"
    android:theme="@style/Theme.Material3.DayNight">
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
2. Confirm your subscription is active at [physiology.presagetech.com](https://physiology.presagetech.com/auth/login).
3. Check that the device has an active internet connection — the SDK requires network access for subscription validation.

### OAuth not working in local or debug builds

Android OAuth is currently documented for Play Store releases only. For local development, internal QA, or sideloaded debug builds, use an API key instead.

If you're preparing a Play Store release, register the SHA-256 fingerprint for the signing certificate used by that release, then re-download `presage_services.xml`.
Run:

```bash
keytool -list -v -keystore <path-to-keystore> -alias <key-alias> -storepass <store-password> | grep SHA256
```

Register that fingerprint under **Account → OAuth Registration** alongside your package name, then re-download and replace `presage_services.xml`.

> **Note:** Each package name can only be registered once. You cannot create multiple OAuth configs for the same package name.

---

## Getting Help

- Email: [support@presagetech.com](mailto:support@presagetech.com)
- [Submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues)
- [Docs and FAQ](https://smartspectra.presagetech.com)
- [Developer Admin Portal](https://physiology.presagetech.com/auth/login)
