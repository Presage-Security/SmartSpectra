# SmartSpectra .NET WinUI sample

A minimal **WinUI 3 / .NET 8** desktop app that consumes the **published
`SmartSpectra.Net` NuGet package** to run a **live on-device physiology
measurement from the webcam**. It is the .NET analog of the Node/Electron
quickstart: rather than processing a recorded file, it captures camera frames
and streams them into the SDK in real time.

Unlike the C++ `winui3_example` (which builds against an unzipped SDK via
`SmartSpectraSdkDir`), this sample pulls the SDK straight from the public
NuGet feed via a `<PackageReference>` — the same way an external .NET consumer
would. The package's MSBuild targets copy the native runtime DLLs
(`smartspectra.dll`, `smartspectra_capi.dll`, `opencv_world*.dll`) next to the
app automatically, so there is no manual `PATH`/xcopy step.

## Prerequisites

- Windows 10 (1809+) or Windows 11, **x64**
- .NET 8 SDK (or newer)
- A webcam, with camera access allowed for desktop apps
  (Settings → Privacy & security → Camera)
- A SmartSpectra API key

## Build & run

The feeds are already configured in [`nuget.config`](nuget.config) (the Presage
stable + RC feeds, plus nuget.org for the Windows App SDK).

```powershell
# From this directory:
dotnet build -c Release

# Pin a specific published version (default is the stable release in the .csproj):
dotnet build -c Release -p:SmartSpectraNetVersion=3.4.0

# Run
dotnet run -c Release
```

Then in the app: paste your API key and click **Start**. The webcam preview
appears; keep your face centered and hold still. Status and validation hints
update live, and the pulse / breathing-rate cards fill in as the measurement
progresses. Click **Stop** to end.

### Pre-release (RC) builds

The RC feed (`SmartSpectra-RC`) is wired in `nuget.config`. To use an RC build,
point the version at one, e.g.:

```powershell
dotnet build -c Release -p:SmartSpectraNetVersion=3.5.0-rc.2
```

## What this exercises

`SmartSpectraSession.Create` → subscribe to `StatusChanged`, `ValidationChanged`,
`MetricsReceived`, `ErrorOccurred` → `StartCustom` → push webcam frames with
`SendFrame` → `Stop`/`Wait`/`Dispose`. Frames come from a WinRT
`MediaFrameReader` (BGRA8), so there are no extra capture dependencies. See
[`MainWindow.xaml.cs`](MainWindow.xaml.cs).
