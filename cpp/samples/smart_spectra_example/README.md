# SmartSpectra Example

> macOS users: see the [signing section](../README.md#macos-signing) in the top-level samples README before first run.

A SmartSpectra C++ desktop sample that streams metrics and validation
status to the terminal while displaying the camera (or input video)
feed. Demonstrates the standard SmartSpectra setup: configure → register
callbacks → choose source → Start. Flag parsing is via Abseil flags.

## Build

```bash
cmake --build build --target smart_spectra_example
```

## Run

```bash
# Camera input (default device)
./build/samples/smart_spectra_example/smart_spectra_example --api_key=YOUR_API_KEY_HERE

# Discover cameras without an API key (Linux, macOS, Windows)
./build/samples/smart_spectra_example/smart_spectra_example --list_cameras

# Select an exact ID from the listing
./build/samples/smart_spectra_example/smart_spectra_example --api_key=YOUR_API_KEY_HERE --camera_id="ID"

# Legacy camera device index
./build/samples/smart_spectra_example/smart_spectra_example --api_key=YOUR_API_KEY_HERE --camera_device_index=1

# Video file input
./build/samples/smart_spectra_example/smart_spectra_example --api_key=YOUR_API_KEY_HERE --input_video_path=/path/to/video.mp4
```

## Flags

| Flag | Description |
| --- | --- |
| `--api_key` | API key for the Physiology service. |
| `--camera_device_index` | The index of the camera device to use (default 0). |
| `--list_cameras` | Print IDs, names, facing, and lens type, then exit without authentication. |
| `--camera_id` | Select an exact discovered ID; overrides `--camera_device_index`. |
| `--input_video_path` | Path to a video file (omit for camera input). |

Use `./smart_spectra_example --help=main` to print the full flag list.

Camera discovery and ID selection work on Linux, macOS, and Windows.
Treat IDs as opaque and quote them when passing them on the command line.
`--camera_id` cannot be combined with `--input_video_path`; a missing camera
fails instead of selecting another device. Without an ID, legacy index selection
is preserved.

## See also

- [`full_example`](../full_example/README.md) for a richer interactive demo with HUD overlays and live plotters.
- [Top-level samples README](../README.md) for build, signing, and platform notes.
