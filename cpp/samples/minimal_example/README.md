# Minimal Example

> macOS users: see the [signing section](../README.md#macos-signing) in the top-level samples README before first run.

The smallest runnable SmartSpectra C++ desktop sample. Uses the default
camera device and reports the SDK's default breathing metric set. Flag
parsing is via Abseil flags.

## Build

```bash
cmake --build build --target minimal_example
```

## Run

```bash
./build/samples/minimal_example/minimal_example --api_key=YOUR_API_KEY_HERE
```

## Flags

| Flag        | Description                              |
|-------------|------------------------------------------|
| `--api_key` | API key for the Physiology service.      |

Use `./minimal_example --help=main` to print the flag list.

## What it does

1. Initializes glog and parses Abseil flags.
2. Configures SmartSpectra with the default supported metrics and your API key.
3. Registers `SetOnMetrics` and `SetOnError` callbacks before starting.
4. Builds an input source from the default camera (`UseCamera()`).
5. Starts measurement and blocks on `WaitUntilComplete()`.
6. Stops cleanly on Ctrl+C / EOF.

This sample is intentionally minimal. For a richer demo with HUD, plotters,
keyboard controls, file playback, and metric persistence, see
[`full_example`](../full_example/README.md).
