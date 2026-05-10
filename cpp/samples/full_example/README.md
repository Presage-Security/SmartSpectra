# Full Example

This example demonstrates continuous physiological monitoring with real-time visualization using the SmartSpectra C++ SDK.

## Overview

The full example shows how to:

- Set up continuous monitoring from a camera or video file
- Display real-time vitals data overlaid on the video stream
- Plot physiological data in real-time using OpenCV

## Key Features

- **Continuous monitoring** - Real-time measurement rather than single spot readings
- **Real-time visualization** - Live HUD display with vitals data
- **Data plotting** - Real-time graphs of heart rate and breathing patterns

## Usage

```bash
# Build the example (from smartspectra/cpp directory)
cmake --build build --target full_example

# Run with camera input
./build/samples/full_example/full_example --also_log_to_stderr --camera_device_index=0 --api_key=YOUR_API_KEY_HERE

# Run with video file input
./build/samples/full_example/full_example --also_log_to_stderr --input_video_path=/path/to/video.mp4 --api_key=YOUR_API_KEY_HERE
```

## macOS Signing

If you run `full_example` on macOS, sign it through an app-style wrapper so the process can use the protected keychain path. The required steps are documented in the [samples README](../README.md#macos-signing).

## Configuration

Before running, make sure to:

1. Set your API key via the `--api_key` parameter
2. Configure camera or video file input
3. Optionally adjust visualization and processing parameters

## Keyboard Controls

During execution:

- `q` or `ESC`: Exit the application

## Code Structure

The example demonstrates:

1. **Settings Configuration** - Sets up continuous measurement and requested metrics
2. **SmartSpectra Setup** - Configures `SmartSpectraConfig` and the input source
3. **Callback Registration** - Sets up handlers for metrics, validation, and video output
4. **Real-time Processing** - Continuous measurement with live feedback
5. **Visualization** - HUD overlay with physiological data and trends

This example is ideal for applications requiring continuous monitoring with immediate visual feedback, such as fitness applications, health monitoring systems, or research tools.
