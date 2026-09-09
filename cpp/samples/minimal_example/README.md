# Minimal Example

> macOS users: see the [signing section](../README.md#macos-signing) in the top-level samples README before first run.

The smallest runnable SmartSpectra C++ desktop sample. Uses the default
camera device by default, or a file input when `--input_video_path` is
supplied. Reports the SDK's default breathing metric set unless
`--requested_metrics` selects other metrics. Flag parsing is via Abseil flags.

## Build

```bash
cmake --build build --target minimal_example
```

## Run

```bash
./build/samples/minimal_example/minimal_example --api_key=YOUR_API_KEY_HERE
```

To run headlessly against a recording instead of a live camera:

```bash
./build/samples/minimal_example/minimal_example \
  --api_key=YOUR_API_KEY_HERE \
  --input_video_path=/path/to/video.mp4 \
  --requested_metrics=breathing_rate,pulse_rate,hrv
```

## Flags

| Flag | Description |
| --- | --- |
| `--api_key` | API key for the Physiology service. |
| `--input_video_path` | Path to a video file (omit for camera). |
| `--interframe_delay` | Delay in milliseconds between file input frames. Defaults to `20`, matching `full_example`. |
| `--requested_metrics` | Comma-separated metric names, such as `breathing_rate,pulse_rate,hrv`. Omit to use the default breathing metrics. |

Use `./minimal_example --help=main` to print the flag list.

## What it does

1. Parses Abseil flags.
2. Configures SmartSpectra with the requested metrics (or defaults) and your API key.
3. Registers `SetOnMetrics` and `SetOnError` callbacks before starting.
4. Builds an input source from `--input_video_path` when present, otherwise
   from the default camera (`UseCamera()`).
5. Starts measurement and blocks on `WaitUntilComplete()`.
6. Stops cleanly on Ctrl+C / EOF.

This sample is intentionally minimal. For a richer demo with HUD, plotters,
keyboard controls, file playback, and metric persistence, see
[`full_example`](../full_example/README.md).
