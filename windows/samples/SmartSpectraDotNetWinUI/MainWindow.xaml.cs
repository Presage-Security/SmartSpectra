// MainWindow.xaml.cs — minimal live-camera use of the SmartSpectra .NET SDK
// consumed from the published NuGet package (SmartSpectra.Net).
//
// This is the .NET analog of the Node/Electron quickstart: instead of a
// pre-recorded file, it drives the SDK from the live webcam. The .NET SDK's
// live-input path is StartCustom() + SendFrame(): the app owns frame capture
// and pushes each frame into the on-device graph. Here a MediaFrameReader
// (native WinRT — no extra dependencies) delivers BGRA8 frames; each frame is
// pushed to the SDK and mirrored into the preview. The SDK streams status /
// validation / metrics back through events, which we marshal to the UI thread
// via DispatcherQueue.
using System;
using System.Linq;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;
using Google.Protobuf.Collections;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media.Imaging;
using Presage.SmartSpectra;
using Windows.Graphics.Imaging;
using Windows.Media.Capture;
using Windows.Media.Capture.Frames;
using Windows.Media.MediaProperties;
using WinRT;

namespace SmartSpectraDotNetWinUI;

public sealed partial class MainWindow : Window
{
    // Request the breathing + cardio bundles so both readout cards populate.
    // These are MetricType integer codes; omitting RequestedMetrics is
    // equivalent to the breathing bundle alone.
    private static readonly int[] RequestedMetrics =
    {
        0, 1, 2,              // breathing: chest / abdomen / rate
        15, 16, 17,           // cardio: pulse rate / arterial pressure trace / HRV
    };

    private readonly DispatcherQueue _dispatcher = DispatcherQueue.GetForCurrentThread();
    private readonly SoftwareBitmapSource _previewSource = new();

    private SmartSpectraSession? _session;
    private MediaCapture? _mediaCapture;
    private MediaFrameReader? _frameReader;
    private int _previewBusy;   // 0/1 latch so we drop preview frames instead of queueing a backlog
    private bool _shuttingDown; // guards ResetToIdleAsync against overlapping teardowns (UI thread only)

    public MainWindow()
    {
        InitializeComponent();
        PreviewImage.Source = _previewSource;
    }

    private async void OnStartClick(object sender, RoutedEventArgs e)
    {
        var apiKey = (ApiKeyBox.Password ?? string.Empty).Trim();
        if (apiKey.Length == 0)
        {
            Log("Enter your SmartSpectra API key first.");
            return;
        }

        StartButton.IsEnabled = false;
        try
        {
            _session = SmartSpectraSession.Create(new SmartSpectraConfig
            {
                ApiKey = apiKey,
                RequestedMetrics = RequestedMetrics,
            });
            _session.StatusChanged += status =>
                _dispatcher.TryEnqueue(() => StatusText.Text = $"Status: {status}");
            _session.ValidationChanged += (code, hint, timestampUs) =>
                _dispatcher.TryEnqueue(() => ValidationText.Text = $"Validation: {code} {hint}".Trim());
            _session.MetricsReceived += (metrics, timestampUs) =>
                _dispatcher.TryEnqueue(() => OnMetrics(metrics));
            // Errors (e.g. a bad API key surfaces asynchronously as
            // AuthenticationFailed) are terminal — tear the measurement down and
            // return to idle so Start works again.
            _session.ErrorOccurred += error =>
                _dispatcher.TryEnqueue(async () =>
                {
                    Log($"Error: {error}");
                    await ResetToIdleAsync();
                });

            // Start the graph in custom-input mode, then begin capture. The
            // camera is user-facing, so mirror horizontally to match the
            // familiar "selfie" preview.
            var start = _session.StartCustom(FrameTransform.MirrorHorizontal);
            if (!start.Ok)
            {
                Log($"StartCustom failed: {start}");
                await ResetToIdleAsync();
                return;
            }

            await StartCameraAsync();
            StopButton.IsEnabled = true;
            Log("Measuring — keep your face centered and hold still.");
        }
        catch (SmartSpectraException ex)
        {
            Log($"Session error: {ex.Error}");
            await ResetToIdleAsync();
        }
        catch (Exception ex)
        {
            Log($"Camera error: {ex.Message}");
            await ResetToIdleAsync();
        }
    }

    private async void OnStopClick(object sender, RoutedEventArgs e)
    {
        await ResetToIdleAsync();
        Log("Stopped.");
    }

    // Tears down capture + session and returns the UI to the idle state
    // (Start enabled, Stop disabled). Idempotent and safe to call from any
    // failure path or the async error callback; the guard prevents overlapping
    // teardowns racing each other's awaits.
    private async Task ResetToIdleAsync()
    {
        if (_shuttingDown) return;
        _shuttingDown = true;
        try
        {
            StopButton.IsEnabled = false;
            await StopAsync();
        }
        finally
        {
            StartButton.IsEnabled = true;
            _shuttingDown = false;
        }
    }

    private async Task StartCameraAsync()
    {
        // Pick the first source group that exposes a color camera.
        var groups = await MediaFrameSourceGroup.FindAllAsync();
        var pick = groups
            .Select(g => new
            {
                Group = g,
                Info = g.SourceInfos.FirstOrDefault(si => si.SourceKind == MediaFrameSourceKind.Color),
            })
            .FirstOrDefault(x => x.Info != null)
            ?? throw new InvalidOperationException("No color camera found.");

        _mediaCapture = new MediaCapture();
        await _mediaCapture.InitializeAsync(new MediaCaptureInitializationSettings
        {
            SourceGroup = pick.Group,
            // CPU memory so each frame exposes a readable SoftwareBitmap.
            MemoryPreference = MediaCaptureMemoryPreference.Cpu,
            SharingMode = MediaCaptureSharingMode.ExclusiveControl,
            StreamingCaptureMode = StreamingCaptureMode.Video,
        });

        var source = _mediaCapture.FrameSources[pick.Info!.Id];
        // Ask the reader for BGRA8 so frames arrive in a layout the SDK and the
        // preview both consume directly.
        _frameReader = await _mediaCapture.CreateFrameReaderAsync(source, MediaEncodingSubtypes.Bgra8);
        _frameReader.FrameArrived += OnFrameArrived;
        await _frameReader.StartAsync();
    }

    private void OnFrameArrived(MediaFrameReader sender, MediaFrameArrivedEventArgs args)
    {
        // Fires on a capture worker thread. Any escaping exception here would be
        // an unhandled exception on a threadpool thread and crash the process,
        // so keep the whole handler defensive and just drop a bad frame.
        try
        {
            using var frame = sender.TryAcquireLatestFrame();
            var bitmap = frame?.VideoMediaFrame?.SoftwareBitmap;
            if (bitmap is null) return;

            // SystemRelativeTime is monotonic — the SDK rejects non-monotonic
            // timestamps. 1 tick = 100 ns, so /10 converts to microseconds.
            var timestampUs = (frame!.SystemRelativeTime ?? TimeSpan.Zero).Ticks / 10;

            // The SDK consumes BGRA byte layout (alpha ignored); the reader already
            // delivers Bgra8, but guard in case a backend ignored the request.
            using var bgra = bitmap.BitmapPixelFormat == BitmapPixelFormat.Bgra8
                ? null
                : SoftwareBitmap.Convert(bitmap, BitmapPixelFormat.Bgra8, BitmapAlphaMode.Straight);
            var sendBitmap = bgra ?? bitmap;

            SendFrameToSdk(sendBitmap, timestampUs);
            UpdatePreview(sendBitmap);
        }
        catch (Exception ex)
        {
            _dispatcher.TryEnqueue(() => Log($"Frame error: {ex.Message}"));
        }
    }

    private unsafe void SendFrameToSdk(SoftwareBitmap bitmap, long timestampUs)
    {
        var session = _session;
        if (session is null) return;

        using var buffer = bitmap.LockBuffer(BitmapBufferAccessMode.Read);
        using var reference = buffer.CreateReference();
        // CsWinRT projects the buffer reference as a WinRT object, so a direct
        // (IMemoryBufferByteAccess) cast throws InvalidCastException. Query the
        // COM interface via WinRT interop instead.
        var byteAccess = reference.As<IMemoryBufferByteAccess>();
        byteAccess.GetBuffer(out var data, out var capacity);
        var stride = buffer.GetPlaneDescription(0).Stride;

        var error = session.SendFrame(
            new ReadOnlySpan<byte>(data, (int)capacity),
            bitmap.PixelWidth,
            bitmap.PixelHeight,
            stride,
            PixelFormat.Bgra,
            timestampUs);

        if (!error.Ok && error.Code != SmartSpectraErrorCode.InvalidState)
            _dispatcher.TryEnqueue(() => Log($"SendFrame: {error}"));
    }

    private void UpdatePreview(SoftwareBitmap bitmap)
    {
        // Drop preview frames while a previous SetBitmapAsync is in flight so
        // capture never blocks on the UI thread.
        if (Interlocked.CompareExchange(ref _previewBusy, 1, 0) != 0) return;

        // SoftwareBitmapSource requires Bgra8 + premultiplied alpha, and the
        // frame's bitmap is released when this callback returns, so hand the UI
        // thread an independent copy.
        var copy = SoftwareBitmap.Convert(bitmap, BitmapPixelFormat.Bgra8, BitmapAlphaMode.Premultiplied);
        if (!_dispatcher.TryEnqueue(async () =>
        {
            try
            {
                await _previewSource.SetBitmapAsync(copy);
            }
            catch
            {
                // SetBitmapAsync can throw transiently (e.g. reentrancy or a
                // torn-down source during shutdown). This runs in an async void
                // continuation, so an escaping exception would crash the WinUI
                // process — swallow it and drop the frame.
            }
            finally
            {
                copy.Dispose();
                Volatile.Write(ref _previewBusy, 0);
            }
        }))
        {
            // Dispatcher refused the work (e.g. window closing): don't leak the
            // copy or wedge the busy latch.
            copy.Dispose();
            Volatile.Write(ref _previewBusy, 0);
        }
    }

    private void OnMetrics(Metrics metrics)
    {
        try
        {
            var pulse = LastStable(metrics.Cardio?.PulseRate);
            if (pulse is { } p)
                PulseText.Text = $"{p:F0} BPM";

            var breathing = LastStable(metrics.Breathing?.Rate);
            if (breathing is { } b)
                BreathingText.Text = $"{b:F0} BR/min";
        }
        catch (Exception ex)
        {
            Log($"Metrics error: {ex.Message}");
        }
    }

    // Each metrics packet is the windowed slice since the previous emit; take
    // the most recent stable reading (falling back to the last reading).
    private static float? LastStable(RepeatedField<MeasurementWithConfidence>? series)
    {
        if (series is null || series.Count == 0) return null;
        var stable = series.LastOrDefault(m => m.Stable);
        return (stable ?? series[^1]).Value;
    }

    private async Task StopAsync()
    {
        if (_frameReader is not null)
        {
            _frameReader.FrameArrived -= OnFrameArrived;
            try { await _frameReader.StopAsync(); } catch { /* already stopped */ }
            _frameReader.Dispose();
            _frameReader = null;
        }

        _mediaCapture?.Dispose();
        _mediaCapture = null;

        if (_session is not null)
        {
            _session.Stop();
            await Task.Run(() => _session.Wait(5000));
            _session.Dispose();
            _session = null;
        }
    }

    private void Log(string message)
    {
        // Newest line on top so the latest event is always visible without scrolling.
        LogText.Text = $"{DateTimeOffset.Now:HH:mm:ss}  {message}{Environment.NewLine}{LogText.Text}";
    }

    [ComImport]
    [Guid("5B0D3235-4DBA-4D44-865E-8F1D0E4FD04D")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private unsafe interface IMemoryBufferByteAccess
    {
        void GetBuffer(out byte* buffer, out uint capacity);
    }
}
