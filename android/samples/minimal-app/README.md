# Minimal Android Sample

Set your SmartSpectra API key directly in `MainActivity.kt`:

```kotlin
private val apiKey = "YOUR_API_KEY"
```

Obtain a key from <https://physiology.presagetech.com>.

Build and install:

```bash
cd smartspectra/android
./gradlew :samples:minimal-app:assembleInternalDebug
./gradlew :samples:minimal-app:installInternalDebug
```
