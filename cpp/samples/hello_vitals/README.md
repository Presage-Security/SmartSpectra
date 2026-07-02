# Hello Vitals

This is the shared SmartSpectra C++ quickstart example. The Linux, macOS, and
Windows documentation pages inline `hello_vitals.cpp` from this directory.

Build it as a standalone consumer project:

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build
```

On Windows, set `SMARTSPECTRA_SDK_PATH` (or pass `-DCMAKE_PREFIX_PATH=...`) so
`find_package(SmartSpectra CONFIG REQUIRED)` can locate the extracted SDK ZIP.
