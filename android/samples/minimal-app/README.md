# Minimal Android Sample

Set your SmartSpectra API key directly in `MainActivity.kt`:

```kotlin
private val apiKey = "YOUR_API_KEY"
```

Obtain a key from <https://physiology.presagetech.com/auth/login>.

While stopped, tap **Select camera** to discover cameras by name, facing, and ID.
Choose a device for the next measurement, **Default** for automatic selection,
or **Refresh** to discover cameras again. The SDK retains the selection across
stop/start/reset. Discovery requires no API key; starting a measurement does.
The picker is available in portrait and landscape and is disabled while processing.
After a failed Start, **Select camera** first stops the failed session, then opens
the list so you can choose another camera and try again.

Build and install:

```bash
cd smartspectra/android
./gradlew :samples:minimal-app:assembleInternalDebug
./gradlew :samples:minimal-app:installInternalDebug
```
