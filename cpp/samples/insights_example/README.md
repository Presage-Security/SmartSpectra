# Insights Example

> macOS users: see the [signing section](../README.md#macos-signing) in the top-level samples README before first run.

A SmartSpectra C++ desktop sample focused on the higher-level "insights"
metric stream. Reads from a camera or a local video file and reports the
default supported metrics with insight-style summaries. Flag parsing is
via Abseil flags.

## Build

```bash
cmake --build build --target insights_example
```

## Run

```bash
# Camera input (default device)
./build/samples/insights_example/insights_example --api_key=YOUR_API_KEY_HERE

# Specific camera device
./build/samples/insights_example/insights_example --api_key=YOUR_API_KEY_HERE --camera_device_index=1

# Video file input
./build/samples/insights_example/insights_example --api_key=YOUR_API_KEY_HERE --input_video_path=/path/to/video.mp4
```

## Flags

| Flag                      | Description                                       |
|---------------------------|---------------------------------------------------|
| `--api_key`               | API key for the Physiology service.               |
| `--camera_device_index`   | The index of the camera device to use (default 0).|
| `--input_video_path`      | Path to a video file (omit for camera input).     |

Use `./insights_example --help=main` to print the full flag list.

## See also

- [`full_example`](../full_example/README.md) for a richer interactive demo with HUD overlays and live plotters.
- [Top-level samples README](../README.md) for build, signing, and platform notes.
