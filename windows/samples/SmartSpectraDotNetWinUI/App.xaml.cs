// App.xaml.cs — entry point for the SmartSpectra .NET WinUI sample.
using System;
using System.IO;
using Microsoft.UI.Xaml;

namespace SmartSpectraDotNetWinUI;

public partial class App : Application
{
    private Window? _window;

    public App()
    {
        InitializeComponent();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        // The native graph resolves resources like "graph/models/*.tflite"
        // relative to the process working directory. Pin it to the app folder so
        // the sample works however it's launched (double-click, dotnet run,
        // Start-Process) rather than depending on the caller's CWD.
        try { Directory.SetCurrentDirectory(AppContext.BaseDirectory); } catch { }

        _window = new MainWindow();
        _window.Activate();
    }
}
