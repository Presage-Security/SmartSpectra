# GStreamer Example

> macOS users: see the [signing section](../README.md#macos-signing) in the top-level samples README before first run.

A SmartSpectra C++ sample that pulls frames from a GStreamer pipeline
(via OpenCV's `cv::VideoCapture` GStreamer backend) and pushes them
into the SDK through `UseCustomInput()`. Useful as a starting point
for non-V4L2 capture stacks (e.g. RTSP, GenICam, or platform-specific
hardware pipelines). Flag parsing is via Abseil flags.

The capture device is selected by the GStreamer pipeline string in
`main.cc`, not by a flag. Edit the pipeline in source if you need a
different device, format, or transport.

## Build

```bash
cmake --build build --target gstreamer_example
```

This sample depends on OpenCV being built with GStreamer support and on
GStreamer being installed on the system.

## Run

```bash
./build/samples/gstreamer_example/gstreamer_example --api_key=YOUR_API_KEY_HERE
```

## Flags

| Flag        | Description                              |
|-------------|------------------------------------------|
| `--api_key` | API key for the Physiology service.      |

Use `./gstreamer_example --help=main` to print the flag list.

## See also

- [`full_example`](../full_example/README.md) for a richer interactive demo with HUD overlays and live plotters.
- [Top-level samples README](../README.md) for build, signing, and platform notes.
