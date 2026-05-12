---
title: Migration Guide
description: C++-specific migration notes for SmartSpectra SDK upgrades.
---

# SmartSpectra C++ SDK Migration Guide

> Applies to SmartSpectra C++ SDK v3.0 (current: v3.0.0-rc.14).

## C++ SDK v3.0 Migration

Migrating from SDK v1.x to v3.0 (C++).

### Operation Mode Removal

The SDK now operates in continuous mode by default. The `OperationMode` enum and related container type aliases have been removed. Configure the SDK with `SmartSpectraConfig` and use `SmartSpectra` directly.

```cpp
// Before:
Settings<OperationMode::Continuous, IntegrationMode::Rest> settings;
CpuContinuousRestForegroundContainer container(settings);

// After:
presage::smartspectra::SmartSpectraConfig config;
config.api_key = "...";
config.requested_metrics =
    presage::smartspectra::SmartSpectraConfig::BreathingMetrics();

presage::smartspectra::SmartSpectra spectra(config);
```

For timed sessions that previously depended on spot-style container behavior:

```cpp
config.enable_accumulated_output = true;
spectra.UseFile("video.mp4").SetMaxDuration(30000).Build();
// For live camera sessions, keep timing in application code and call Stop().
```

### Container Removal

Use `SmartSpectra` directly instead of `Container` abstractions.

```cpp
// Before:
#include <smartspectra/container/foreground_container.hpp>
container::CpuRestForegroundContainer container(settings);
container.Initialize();
container.Run();
```

```cpp
// After:
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>

presage::smartspectra::SmartSpectraConfig config;
config.api_key = "...";

presage::smartspectra::SmartSpectra spectra(config);
const auto source_error = spectra.UseCamera().Build();
if (!source_error.ok()) {
    LOG(ERROR) << source_error.FullMessage();
    return;
}

if (const auto err = spectra.Start(); !err.ok()) {
    LOG(ERROR) << err.FullMessage();
    return;
}
```

### Public Headers

Use the SmartSpectra public headers and protobuf metric headers directly:

```cpp
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/messages/metrics.h>
```

### Input Source Builders Must Be Materialized

The current API separates source configuration from source creation. After
calling `UseCamera()`, `UseFile()`, or `UseCustomInput()`, call `Build()` and
check the returned `SmartSpectraError` before `Start()`.

```cpp
// Before:
spectra.UseCamera();
spectra.Start();
```

```cpp
// After:
const auto source_error =
    spectra.UseCamera().SetResolution(1280, 720).SetFps(30).Build();
if (!source_error.ok()) {
    LOG(ERROR) << source_error.FullMessage();
    return;
}

if (const auto err = spectra.Start(); !err.ok()) {
    LOG(ERROR) << err.FullMessage();
    return;
}
```

If you omit source configuration entirely, `Start()` still defaults to camera 0.

### Metric Defaults Are Narrower

If an older integration assumed pulse-related output was part of the default
metric set, update it explicitly. `BreathingMetrics()` is the named breathing
bundle. `DefaultSupportedMetrics()` is the fallback used when
`requested_metrics` is empty and currently delegates to `BreathingMetrics()`.

```cpp
presage::smartspectra::SmartSpectraConfig config;
config.api_key = "...";
config.requested_metrics =
    presage::smartspectra::SmartSpectraConfig::BreathingMetrics();
config.AddMetrics(
    presage::smartspectra::SmartSpectraConfig::CardioMetrics());
```

### CMake Target Change

Older container-based integrations linked `SmartSpectra::Container`. The
installed v3 package exposes the public C++ SDK as `SmartSpectra::SDK`.

```cmake
# Before:
target_link_libraries(my_app SmartSpectra::Container)

# After:
target_link_libraries(my_app SmartSpectra::SDK)
```

### Single Package SDK

Older source-build integrations may have linked the Edge target directly as
`Physiology::Edge`. That target is not a customer-facing installed package
target in v3. Use the installed SmartSpectra package and link `SmartSpectra::SDK`.

```bash
sudo apt install libsmartspectra-dev
```

```cmake
# Before:
target_link_libraries(my_app Physiology::Edge)

# After:
target_link_libraries(my_app SmartSpectra::SDK)
```

### Protobuf Namespace: `presage::physiology` → `presage::smartspectra`

The generated protobuf message namespace has been renamed from
`presage::physiology` to `presage::smartspectra`. Older container classes
already lived under `presage::smartspectra::container`; current public SDK
classes and generated `*.pb.h` messages now share the top-level
`presage::smartspectra` namespace.

```cpp
// Before:
presage::physiology::MetricsBuffer metrics_buffer;
presage::physiology::Metrics edge_metrics;

// After:
presage::smartspectra::SmartSpectraConfig config;
presage::smartspectra::Metrics metrics;
```

The shared protobuf message schemas (`insights`, `metric_types`, `metrics`,
`point_types`, `status`) also move from `package presage.physiology` to
`package presage.smartspectra`. The on-the-wire byte format of serialized
messages is unchanged (field numbers are untouched).

#### `Any.type_url` and JSON `@type` non-interop

`google.protobuf.Any.type_url` strings, JSON `@type` fields, and
`DescriptorPool` lookups all key off the fully-qualified message name, which
moves from `presage.physiology.<Message>` to `presage.smartspectra.<Message>`.
A new SDK build cannot decode `Any`-wrapped or JSON-keyed payloads written by
an older build of the SDK, and vice versa. Cross-version interop between old
and new builds is not supported as part of this migration.

If your application persists `Any`-wrapped blobs or stores JSON with `@type`
fields keyed by the old type name, regenerate those artifacts with the new
SDK before deploying. Re-emitting the data is the only supported path.

#### Related identity surfaces

This migration covers the C++ public surface and the proto `package`
declarations. Related platform surfaces are documented separately:

- **Android Java/Kotlin (`com.presage.physiology.proto.*` → `com.presagetech.smartspectra.proto.*`)**:
  consumers building against the new Android SDK release should update their
  generated-proto imports as part of the Android upgrade. See the Android
  Migration Guide for the platform-specific import examples.
- **Python proto wheel (`Physiology-Edge-Protobuf`)**: the top-level
  `physiology` package name and the wheel name itself are unchanged in
  v3.0. The sub-package was renamed (`physiology.modules.messages.*` →
  `physiology.smartspectra.messages.*`) to track the C++ rename. See the
  "Python Proto Wheel" section below for the import examples.

The Android JNI-bound classes `com.presage.physiology.Messages` and
`com.presage.physiology.emd.security.AndroidKeyStoreHelper` are pinned by
native `.so` JNI symbol names and are not renamed by this migration or by
the Java protobuf migration; their cleanup is tracked as a further follow-up.

### Container and Physiology Module Headers → SmartSpectra SDK Headers

The old public surface combined `smartspectra/container/...` headers with
`physiology/modules/...` generated protobuf headers. Current consumers include
the SmartSpectra SDK headers and protobuf metric headers directly.

```cpp
// Before:
#include <smartspectra/container/foreground_container.hpp>
#include <smartspectra/container/settings.hpp>
#include <physiology/modules/messages/metrics.h>

// After:
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>
#include <smartspectra/messages/metrics.h>
```

The SDK now exports a single `-I` root (`<prefix>/include`); the previous
secondary `-I<prefix>/include/physiology` propagation is gone.

### Public CMake Surface Uses `SMARTSPECTRA_*`

Installed CMake package variables now use the one-word `SMARTSPECTRA_*`
prefix. There are no compatibility aliases for the old names, so update
consumer `CMakeLists.txt` files and CI flags in one pass.

| Old | New |
| --- | --- |
| `SMART_SPECTRA_VERSION` | `SMARTSPECTRA_VERSION` |
| `SMART_SPECTRA_VERSION_MAJOR` | `SMARTSPECTRA_VERSION_MAJOR` |
| `SMART_SPECTRA_VERSION_MINOR` | `SMARTSPECTRA_VERSION_MINOR` |
| `SMART_SPECTRA_VERSION_PATCH` | `SMARTSPECTRA_VERSION_PATCH` |
| `SMART_SPECTRA_VERSION_PLAIN` | `SMARTSPECTRA_VERSION_PLAIN` |
| `SMART_SPECTRA_INSTALL_INCLUDE_DIR` | `SMARTSPECTRA_INSTALL_INCLUDE_DIR` |
| `SMART_SPECTRA_INSTALL_LIB_DIR` | `SMARTSPECTRA_INSTALL_LIB_DIR` |
| `SMART_SPECTRA_INSTALL_CMAKE_DIR` | `SMARTSPECTRA_INSTALL_CMAKE_DIR` |
| `PHYSIOLOGY_EDGE_ARCHITECTURE` | `SMARTSPECTRA_ARCHITECTURE` |
| `PHYSIOLOGY_EDGE_MODEL_DIRECTORY` | `SMARTSPECTRA_MODEL_DIRECTORY` |
| `PHYSIOLOGY_EDGE_GRAPH_DIRECTORY` | `SMARTSPECTRA_GRAPH_DIRECTORY` |
| `PHYSIOLOGY_EDGE_MLX_ENABLED` | `SMARTSPECTRA_MLX_ENABLED` |
| `PHYSIOLOGY_EDGE_MLX_METALLIB_PATH` | `SMARTSPECTRA_MLX_METALLIB_PATH` |
| `PHYSIOLOGY_EDGE_AUTO_FINALIZE` | `SMARTSPECTRA_AUTO_FINALIZE` |

The user-facing CMake option for disabling remote model delivery was also
renamed:

```diff
- cmake -DDISABLE_REMOTE_MODEL_DELIVERY=ON ...
+ cmake -DSMARTSPECTRA_DISABLE_REMOTE_MODEL_DELIVERY=ON ...
```

### MLX CMake Helper Rename

The installed MLX helper module was renamed from `PhysiologyEdge_mlx.cmake` to
`SmartSpectra_mlx.cmake`. `SmartSpectraConfig.cmake` includes this helper
automatically on Apple/MLX builds, so only consumers that include the helper
directly need to change anything.

```diff
- include("${CMAKE_CURRENT_LIST_DIR}/PhysiologyEdge_mlx.cmake")
+ include("${CMAKE_CURRENT_LIST_DIR}/SmartSpectra_mlx.cmake")
```

In this release the helper's public CMake API was also renamed from the
`PhysiologyEdge_` / `_PhysiologyEdge_` / `PHYSIOLOGY_EDGE_` /
`physiology_edge_` prefixes to `SmartSpectra_` / `_SmartSpectra_` /
`SMARTSPECTRA_` / `_SMARTSPECTRA_` / `_smartspectra_`. This is a **hard
rename** — no compatibility aliases or deprecation shims are provided, so
consumers calling the old names will see "Unknown CMake command" errors at
configure time.

| Old name                                              | New name                                            |
| ----------------------------------------------------- | --------------------------------------------------- |
| `PhysiologyEdge_finalize_target`                      | `SmartSpectra_finalize_target`                      |
| `PhysiologyEdge_copy_mlx_metallib_to_target`          | `SmartSpectra_copy_mlx_metallib_to_target`          |
| target property `_PHYSIOLOGY_EDGE_FINALIZED`          | target property `_SMARTSPECTRA_FINALIZED`           |
| global property `_PHYSIOLOGY_EDGE_TARGETS_TO_FINALIZE`| global property `_SMARTSPECTRA_TARGETS_TO_FINALIZE` |
| custom target `_physiology_edge_deploy_metallib`      | custom target `_smartspectra_deploy_metallib`       |
| `message(STATUS "PhysiologyEdge: …")`                 | `message(STATUS "SmartSpectra: …")`                 |

The installed SmartSpectra package exposes only the `SmartSpectra::*`
targets (`SmartSpectra::SDK` for application consumers). `Physiology::Edge`
is an internal source-build target and is not part of the installed
package — see "Single Package SDK" above.

To apply all renames mechanically across a downstream consumer's CMake
sources, run from the repo root:

```bash
git ls-files -z '*.cmake' '*.cmake.in' 'CMakeLists.txt' '**/CMakeLists.txt' \
  | xargs -0 sed -i \
      -e 's/PhysiologyEdge_finalize_target/SmartSpectra_finalize_target/g' \
      -e 's/PhysiologyEdge_copy_mlx_metallib_to_target/SmartSpectra_copy_mlx_metallib_to_target/g' \
      -e 's/_PHYSIOLOGY_EDGE_FINALIZED/_SMARTSPECTRA_FINALIZED/g' \
      -e 's/_PHYSIOLOGY_EDGE_TARGETS_TO_FINALIZE/_SMARTSPECTRA_TARGETS_TO_FINALIZE/g' \
      -e 's/_physiology_edge_deploy_metallib/_smartspectra_deploy_metallib/g' \
      -e 's/"PhysiologyEdge: /"SmartSpectra: /g'
```

(On macOS, replace `sed -i` with `sed -i ''`.)

### `messages/` is now first-class

Protobuf metric headers used to live under `physiology/modules/messages/`.
The `modules/` nesting level has been dropped — `messages/` now sits directly
under the new include base.

```cpp
// Before:
#include <physiology/modules/messages/metrics.h>
#include <physiology/modules/messages/metric_types.pb.h>
#include <physiology/modules/messages/status.h>
#include <physiology/modules/protobuf_enum.h>     // re-homed (see below)

// After:
#include <smartspectra/messages/metrics.h>
#include <smartspectra/messages/metric_types.pb.h>
#include <smartspectra/messages/status.h>
#include <smartspectra/messages/protobuf_enum.h>  // moved next to metric_types.h
```

### Pruned Public Headers

Two headers that were previously installed publicly are no longer shipped:

- `<physiology/modules/configuration.h>` — generated build-feature `#define`s,
  never a stable public API. No replacement.
- `<physiology/modules/filesystem_absl.h>` — internal filesystem helper. No
  replacement.

If your code transitively included either header, vendor your own equivalent
or open an issue describing the use case.

### Version Header

The SDK now exposes a single version header at `<smartspectra/version.hpp>`.
The previous `<physiology/version.h>` and the transitional `<smartspectra/edge_version.h>`
are retired; both have been collapsed into `<smartspectra/version.hpp>`.

```cpp
// Before:
#include <physiology/version.h>

// After:
#include <smartspectra/version.hpp>
```

The version macros are now namespaced as `SMART_SPECTRA_VERSION_MAJOR`,
`SMART_SPECTRA_VERSION_MINOR`, `SMART_SPECTRA_VERSION_PATCH`,
`SMART_SPECTRA_VERSION_STRING`, and `SMART_SPECTRA_VERSION_PLAIN`. The
constexpr accessors under `presage::smartspectra::GetVersionMajor()` /
`GetVersionMinor()` / `GetVersionPatch()` / `GetVersionString()` /
`GetVersionPlain()` are unchanged in name and signature.

### Container Headers → `smartspectra*` SDK Headers

Public header filenames in `<prefix>/include/smartspectra/` now expose the
direct SDK surface instead of the old container surface:

```cpp
// Before:                                      // After:
<smartspectra/container/foreground_container.hpp>  <smartspectra/smartspectra.h>
<smartspectra/container/settings.hpp>              <smartspectra/smartspectra_config.h>
<physiology/modules/messages/metrics.h>            <smartspectra/messages/metrics.h>
<physiology/modules/messages/status.h>             <smartspectra/messages/status.h>
```

### Python Proto Wheel

The Python protobuf wheel sub-package was renamed to track the C++ rename. The
top-level `physiology` package name is unchanged; only the sub-package flips:

```python
# Before:
import physiology.modules.messages.metrics_pb2 as metrics
from physiology.modules.messages import status_pb2

# After:
import physiology.smartspectra.messages.metrics_pb2 as metrics
from physiology.smartspectra.messages import status_pb2
```
