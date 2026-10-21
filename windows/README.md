# SmartSpectra .NET SDK (Windows)

.NET 8 wrapper for SmartSpectra on Windows. The managed API wraps the
SmartSpectra C ABI shim with `IDisposable`, .NET events, and protobuf metrics
types.

## Supported Platforms

| Platform              | Status        | Notes                   |
| --------------------- | ------------- | ----------------------- |
| Windows 10 / 11 (x64) | Experimental  | .NET 8 runtime required |

For platforms not listed above, contact
[support@presagetech.com](mailto:support@presagetech.com) if you have a
specific need.

## Prerequisites

- **.NET 8 SDK** or Visual Studio 2022 with the **.NET desktop development** workload
- **SmartSpectra native runtime** (`smartspectra.dll`, `smartspectra_capi.dll`, and dependencies)
- **API Key** from [physiology.presagetech.com](https://physiology.presagetech.com/auth/login) — an
  AI assistant connected to the [SmartSpectra MCP Server](../docs/mcp-server.md) can fetch it from
  your account for you

## Installation

Add the public stable feed as a package source (one-time):

```powershell
nuget sources add -Name SmartSpectra `
    -Source https://packages.presagetech.com/nuget/index.json
```

For release candidate builds, also add the RC feed:

```powershell
nuget sources add -Name SmartSpectra-RC `
    -Source https://packages.presagetech.com/nuget-rc/index.json
```

Add the package to your `.csproj`:

```xml
<!-- Stable release -->
<PackageReference Include="SmartSpectra.Net" Version="<version>" />

<!-- RC build -->
<PackageReference Include="SmartSpectra.Net" Version="<version>-rc.<n>" />
```

`Google.Protobuf`, `SmartSpectra.Net.Protos`, and the native runtime DLLs are
copied to your output directory by the package targets.

## Quick Start: Video File

```csharp
using Presage.SmartSpectra;

var config = new SmartSpectraConfig
{
    ApiKey = Environment.GetEnvironmentVariable("SMARTSPECTRA_API_KEY")
        ?? throw new InvalidOperationException("SMARTSPECTRA_API_KEY is required"),
    // Leave empty for the SDK's default breathing metric set, or pass native
    // MetricType integer values to request additional metrics.
    RequestedMetrics = Array.Empty<int>(),
};

using var session = SmartSpectraSession.Create(config);

session.ValidationChanged += (_, hint, timestampUs) =>
    Console.WriteLine($"Validation: {hint} @ {timestampUs}");

session.MetricsReceived += (metrics, timestampUs) =>
{
    var br = metrics.Breathing?.Rate.FirstOrDefault()?.Value;
    if (br.HasValue)
        Console.WriteLine($"Breathing rate: {br:F1} bpm @ {timestampUs / 1_000_000.0:F1}s");
};

session.ErrorOccurred += error =>
    Console.Error.WriteLine($"Error: {error}");

var error = session.RunFile("sample.mp4", timestampsPath: null);
if (!error.Ok)
    throw new SmartSpectraException(error);
```

## Quick Start: Custom Frames

Use custom input when your app owns camera capture:

```csharp
using var session = SmartSpectraSession.Create(config);

var startError = session.StartCustom(FrameTransform.None);
if (!startError.Ok)
    throw new SmartSpectraException(startError);

// Push frames from your capture loop. The buffer is consumed synchronously
// during SendFrame; keep it valid for the duration of the call.
var frameError = session.SendFrame(
    frameBytes,
    width,
    height,
    strideBytes,
    PixelFormat.Nv12,
    timestampUs);
if (!frameError.Ok)
    Console.Error.WriteLine(frameError);

session.Stop();
session.Wait(timeoutMs: 5000);
```

## API Reference

### `SmartSpectraConfig`

| Property | Type | Description |
| --- | --- | --- |
| `ApiKey` | `string` | API key authentication |
| `RequestedMetrics` | `int[]` | Native `MetricType` integer values. Empty uses the SDK default breathing metric set |

### `SmartSpectraSession`

Create sessions with `SmartSpectraSession.Create(config)`. The constructor is
not public.

**Events** — all fire on internal worker threads; marshal to the UI thread if needed.

| Event | Payload | Description |
| --- | --- | --- |
| `StatusChanged` | `ProcessingStatus` | Graph lifecycle state |
| `ValidationChanged` | `(ValidationCode code, string hint, long timestampUs)` | Face / imaging readiness |
| `MetricsReceived` | `(Metrics metrics, long timestampUs)` | Serialized protobuf metrics decoded into managed types |
| `ErrorOccurred` | `SmartSpectraError` | Runtime error |
| `FrameDisposed` | `(bool sent, long timestampUs)` | Custom-input frame acceptance result |

**Lifecycle methods.** The public surface is small:

```csharp
SmartSpectraSession Create(SmartSpectraConfig config);
SmartSpectraError RunFile(string videoPath, string? timestampsPath = null);
SmartSpectraError StartCustom(FrameTransform transform = FrameTransform.None);
SmartSpectraError SendFrame(ReadOnlySpan<byte> data, int width, int height,
    int strideBytes, PixelFormat pixelFormat, long timestampUs);
void Stop();
bool Wait(int timeoutMs = -1);
```

### Error handling

Lifecycle methods (`RunFile`, `StartCustom`, `SendFrame`) return a
`SmartSpectraError` describing the outcome:

| Property | Type | Description |
| --- | --- | --- |
| `Code` | `SmartSpectraErrorCode` | Error category — `Ok` (0) means success |
| `Message` | `string` | Human-readable detail |
| `Retryable` | `bool` | `true` if the caller can safely retry the operation |
| `Ok` | `bool` | Shorthand for `Code == SmartSpectraErrorCode.Ok` |

`Create` throws `SmartSpectraException` on failure (wrapping a
`SmartSpectraError` exposed via the `Error` property) instead of returning
it. `Wait` throws on an unexpected native status; a timeout simply returns
`false`.

### Metrics

`MetricsReceived` uses `Presage.SmartSpectra.Metrics` from
`SmartSpectra.Net.Protos`. It has the same protobuf fields as the native SDK:
`Breathing`, `Cardio`, `Face`, and `Eda`.

### Enums

`ProcessingStatus`, `ValidationCode`, `SmartSpectraErrorCode`,
`FrameTransform`, and `PixelFormat` mirror the native C++ integer values.

## Package Layout

```text
SmartSpectra.Net.<version>.nupkg
├── lib/net8.0/
│   ├── SmartSpectra.Net.dll
│   ├── SmartSpectra.Net.Native.dll
│   └── SmartSpectra.Net.Protos.dll
├── runtimes/win-x64/native/
│   ├── smartspectra.dll
│   ├── smartspectra_capi.dll
│   └── opencv_world4100.dll
└── build/
    └── SmartSpectra.Net.targets
```

## Thread Safety

All events fire on internal worker threads, not the calling thread. Do not call
`SmartSpectraSession` methods from inside an event handler without external
synchronization. For UI updates, dispatch to the UI thread:

```csharp
// WPF
session.MetricsReceived += (metrics, _) =>
    Dispatcher.InvokeAsync(() => UpdateUI(metrics));

// WinUI 3
session.MetricsReceived += (metrics, _) =>
    DispatcherQueue.TryEnqueue(() => UpdateUI(metrics));
```

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `BadImageFormatException` | Ensure the project targets x64, not AnyCPU — the native DLLs are x64-only |
| `DllNotFoundException` | Check that the native runtime DLLs are in the output directory |
| `FileNotFoundException: SmartSpectra.Net.Protos` | The protos DLL must be in the same directory as `SmartSpectra.Net.dll` |
| No metrics callbacks | Check the returned `SmartSpectraError` from `RunFile`, `StartCustom`, or `SendFrame` |
| Events fire on the wrong thread | Use `Dispatcher.InvokeAsync` / `DispatcherQueue.TryEnqueue` to marshal to the UI thread |
