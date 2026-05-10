# Dense Facemesh Example

> macOS users: see the [signing section](../README.md#macos-signing) in the top-level samples README before first run.

A SmartSpectra C++ desktop sample that overlays the dense face landmark
mesh on the live camera feed. Useful for verifying landmark stability,
debugging tracking, or building a face-aligned visualization on top of
SmartSpectra output. Flag parsing is via Abseil flags.

## Build

```bash
cmake --build build --target dense_facemesh
```

The executable target name is `dense_facemesh` (the source file is
`main.cc`).

## Run

```bash
# Default camera
./build/samples/dense_facemesh_example/dense_facemesh --api_key=YOUR_API_KEY_HERE

# Specific camera device
./build/samples/dense_facemesh_example/dense_facemesh --api_key=YOUR_API_KEY_HERE --camera_device_index=1
```

Press `q` or `ESC` in the preview window to quit.

## Flags

| Flag                      | Description                                       |
|---------------------------|---------------------------------------------------|
| `--api_key`               | API key for the Physiology service.               |
| `--camera_device_index`   | The index of the camera device to use (default 0).|

Use `./dense_facemesh --help=main` to print the full flag list.

## See also

- [`full_example`](../full_example/README.md) for a richer interactive demo with HUD overlays and live plotters.
- [Top-level samples README](../README.md) for build, signing, and platform notes.
