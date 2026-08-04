---
title: LLM Insights
description: Request and receive LLM Insights from the C++ SmartSpectra SDK.
---

# C++ LLM Insights

Platform-specific usage for the C++ SDK. For what LLM Insights are, the
request/response model, required metrics, and the privacy notice, see the
[LLM Insights overview](../../docs/llm-insights/index.md).

## Enable the required metrics

Insights summarize the buffered vitals, so breathing (the default set) and
cardio must both be active:

```cpp
spectra::SmartSpectraConfig config;
config.api_key = my_api_key;
config.AddMetrics(spectra::SmartSpectraConfig::DefaultSupportedMetrics()); // breathing defaults
config.AddMetrics(spectra::SmartSpectraConfig::CardioMetrics());           // pulse, HRV, arterial pressure trace
```

`CardioMetrics()` includes `ARTERIAL_PRESSURE_TRACE`, which drives the on-screen
pulse waveform.

## Receive responses

Register a callback before starting. It receives **both** the auto-fired
periodic vitals insights and on-demand responses; correlate an on-demand reply
by matching `Insight::request_id()` against the ID you got from `RequestInsight`.

```cpp
using presage::smartspectra::Insight;

std::mutex insight_mutex;
smart_spectra.SetOnInsight([&](const Insight& insight) {
  std::lock_guard<std::mutex> lock(insight_mutex); // callback runs on a background thread
  if (insight.has_analysis()) {
    // insight.analysis() — the LLM text; insight.request_id() correlates the reply
  } else if (insight.has_error()) {
    // insight.error() — failure message
  }
});
```

`OnInsightFn` is `std::function<void(const Insight&)>`. The callback is invoked
on a **background thread**, so synchronize any state it shares with your
application.

## Request an insight

Call `RequestInsight` on a running session. The response arrives asynchronously
through the callback above; correlate it via `Insight::request_id()`.

```cpp
[[nodiscard]] SmartSpectraError RequestInsight(
    const std::string& text,
    int32_t* out_request_id = nullptr);
```

```cpp
int32_t request_id = 0;
if (const auto err = smart_spectra.RequestInsight("Summarize the user's stress level.", &request_id);
    !err.ok()) {
  std::cerr << err.FullMessage() << '\n';
}
```

- `text` — the prompt. Combined with the latest buffered metrics when they exist,
  otherwise sent prompt-only.
- `out_request_id` — if non-null, receives the request ID for correlation.
- Returns `kInvalidState` (no active session) or `kProcessingFailed` (dispatch
  failed — e.g. no insight callback registered, or a server error).

## Reading the Insight

Branch on `has_analysis()` / `has_error()` (exactly one is set), read the text
with `analysis()` / `error()`, and correlate with `request_id()`. Every insight
is currently delivered with `type()` == `INSIGHT_TYPE_VITALS` (`SPEECH` and
`COMBINED` are reserved), so use `request_id()`, not `type()`, to distinguish
on-demand replies from auto-fired vitals. Full field documentation is in
[Data Types → Insight](../../docs/data-types.md#insight).

The first auto-fired insight arrives about 15 seconds after the session starts;
allow that much valid measurement before an on-demand request can be grounded in
the user's physiology.

## Complete examples

Each example below is a minimal, self-contained program covering the full flow —
SDK init, metric config, a thread-safe `SetOnInsight` sink, an on-demand
`RequestInsight`, and `analysis()`/`error()` handling. They are condensed for the
docs; the linked sample apps are the full, buildable versions.

### Linux — CLI

A console app: type a prompt, press Enter, and the response prints when it
arrives. The insight callback runs on a **background thread**, so shared state is
mutex-guarded.

```cpp
// insights_cli.cc — minimal SmartSpectra LLM Insights example (Linux).
#include <cstdint>
#include <iostream>
#include <mutex>
#include <string>

#include <smartspectra/messages/insights.pb.h>
#include <smartspectra/smartspectra.h>
#include <smartspectra/smartspectra_config.h>

namespace spectra = presage::smartspectra;

int main(int argc, char** argv) {
  if (argc < 2) {
    std::cerr << "usage: insights_cli <api_key>\n";
    return 1;
  }

  // 1-2. Init + metrics: breathing (defaults) + cardio must both be active.
  spectra::SmartSpectraConfig config;
  config.api_key = argv[1];
  config.AddMetrics(spectra::SmartSpectraConfig::DefaultSupportedMetrics());
  config.AddMetrics(spectra::SmartSpectraConfig::CardioMetrics());
  spectra::SmartSpectra smart_spectra(std::move(config));

  // 3. Receive responses. The callback runs on a background thread; guard shared
  //    state. Correlate on-demand replies via request_id().
  std::mutex insight_mutex;
  smart_spectra.SetOnInsight([&](const spectra::Insight& insight) {
    std::lock_guard<std::mutex> lock(insight_mutex);
    if (insight.has_analysis()) {
      std::cout << "\n[insight #" << insight.request_id() << "] "
                << insight.analysis() << "\n> " << std::flush;
    } else if (insight.has_error()) {
      std::cerr << "\n[insight error] " << insight.error() << '\n';
    }
  });
  smart_spectra.SetOnError([](const spectra::SmartSpectraError& err) {
    std::cerr << err.FullMessage() << '\n';
  });

  if (const auto err = smart_spectra.UseCamera().Build(); !err.ok()) {
    std::cerr << err.FullMessage() << '\n';
    return 1;
  }
  if (const auto err = smart_spectra.Start(); !err.ok()) {
    std::cerr << err.FullMessage() << '\n';
    return 1;
  }

  // 4-5. Type a prompt + Enter to request an insight; replies print above.
  std::cout << "Type a prompt and press Enter (empty line quits).\n> " << std::flush;
  std::string prompt;
  while (std::getline(std::cin, prompt) && !prompt.empty()) {
    int32_t request_id = 0;
    if (const auto err = smart_spectra.RequestInsight(prompt, &request_id); !err.ok()) {
      std::cerr << err.FullMessage() << '\n';
    }
  }

  (void)smart_spectra.Stop();
  return 0;
}
```

Full runnable sample:
[`cpp/samples/insights_example`](https://github.com/Presage-Security/SmartSpectra/tree/main/cpp/samples/insights_example).

### Windows — WinUI3 / C++WinRT

`SetOnInsight` fires on a background thread; XAML must be touched only on the UI
thread. Capture the UI `DispatcherQueue` up front and marshal the update with
`TryEnqueue`. Members (declared in `MainWindow.xaml.h`):

```cpp
std::unique_ptr<presage::smartspectra::SmartSpectra> m_spectra;
winrt::Microsoft::UI::Dispatching::DispatcherQueue m_ui_queue{ nullptr };
std::thread m_start_thread;
```

```cpp
// MainWindow.xaml.cpp (excerpt) — WinUI 3 / C++WinRT.
namespace spectra = presage::smartspectra;

MainWindow::MainWindow() {
  InitializeComponent();

  // Capture the UI-thread dispatcher so callbacks can marshal back to it.
  m_ui_queue = DispatcherQueue::GetForCurrentThread();

  // 1-2. Init + metrics: breathing (defaults) + cardio.
  spectra::SmartSpectraConfig cfg;
  cfg.api_key = ApiKey();  // supply your key
  cfg.AddMetrics(spectra::SmartSpectraConfig::DefaultSupportedMetrics());
  cfg.AddMetrics(spectra::SmartSpectraConfig::CardioMetrics());
  m_spectra = std::make_unique<spectra::SmartSpectra>(std::move(cfg));
  (void)m_spectra->UseCamera().Build();

  // 3. Receive responses. Hop to the UI thread with TryEnqueue before touching
  //    XAML. Hold a weak window ref so teardown can release it.
  auto weak = get_weak();
  m_spectra->SetOnInsight([weak](spectra::Insight const& insight) {
    winrt::hstring text;
    if (insight.has_analysis()) text = winrt::to_hstring(insight.analysis());
    else if (insight.has_error()) text = L"Error: " + winrt::to_hstring(insight.error());
    else return;
    if (auto self = weak.get()) {
      self->m_ui_queue.TryEnqueue([weak, text] {
        if (auto self = weak.get()) self->InsightText().Text(text);
      });
    }
  });

  // Start() blocks on authentication and model loading, so keep it off the UI
  // thread — the window would not paint until it returned.
  m_start_thread = std::thread([this] { (void)m_spectra->Start(); });
}

MainWindow::~MainWindow() {
  if (m_start_thread.joinable()) m_start_thread.join();
  (void)m_spectra->Stop();
}

// 4-5. Button handler — request an insight; the reply arrives via SetOnInsight.
void MainWindow::OnInsightClick(IInspectable const&, RoutedEventArgs const&) {
  int32_t request_id = 0;
  if (auto err = m_spectra->RequestInsight(
          "Summarize my current vital signs and flag anything unusual.",
          &request_id);
      !err.ok()) {
    InsightText().Text(L"Error: " + winrt::to_hstring(err.FullMessage()));
  }
}
```

Full runnable sample:
[`cpp/samples/winui3_example`](https://github.com/Presage-Security/SmartSpectra/tree/main/cpp/samples/winui3_example).

### macOS — SwiftUI

A SwiftUI app consumes the C++ SDK through an Objective-C++ bridge. The bridge
registers the insight sink, marshals each response onto the main queue, and
forwards it to a delegate; the SwiftUI model publishes it.

Bridge interface (`SmartSpectraRunner.h`):

```objc
@protocol SmartSpectraRunnerDelegate <NSObject>
// ...existing callbacks...
- (void)smartSpectraRunnerDidUpdateInsight:(NSString *)analysis
                                 requestId:(int32_t)requestId;
- (void)smartSpectraRunnerDidFailInsight:(NSString *)message;
@end

@interface SmartSpectraRunner : NSObject
// ...existing start/stop...
- (int32_t)requestInsight:(NSString *)prompt;  // returns request id, or -1 on failure
@end
```

Bridge implementation (`SmartSpectraRunner.mm`, `#include <smartspectra/messages/insights.pb.h>`):

```objc
namespace ss = presage::smartspectra;

// 1-2. In config setup: breathing (defaults) + cardio.
config.requested_metrics = ss::SmartSpectraConfig::DefaultSupportedMetrics();
config.AddMetrics(ss::SmartSpectraConfig::CardioMetrics());

// 3. Insight sink — fires on a background thread, so marshal to the main queue
//    before forwarding to the delegate (which drives SwiftUI state).
spectra->SetOnInsight([weakSelf](const ss::Insight& insight) {
  if (insight.has_analysis()) {
    NSString *analysis = [NSString stringWithUTF8String:insight.analysis().c_str()];
    int32_t request_id = insight.request_id();
    dispatch_async(dispatch_get_main_queue(), ^{
      SmartSpectraRunner *runner = weakSelf;
      [runner.delegate smartSpectraRunnerDidUpdateInsight:analysis requestId:request_id];
    });
  } else if (insight.has_error()) {
    NSString *message = [NSString stringWithUTF8String:insight.error().c_str()];
    dispatch_async(dispatch_get_main_queue(), ^{
      SmartSpectraRunner *runner = weakSelf;
      [runner.delegate smartSpectraRunnerDidFailInsight:message];
    });
  }
});

// 4. Request an insight (spectra_ is the runner's std::unique_ptr<ss::SmartSpectra>).
- (int32_t)requestInsight:(NSString *)prompt {
  std::lock_guard<std::mutex> lock(mutex_);
  if (!spectra_) return -1;
  int32_t request_id = -1;
  if (auto err = spectra_->RequestInsight(std::string(prompt.UTF8String), &request_id);
      !err.ok()) {
    return -1;
  }
  return request_id;
}
```

SwiftUI model (`AppModel.swift`, an `ObservableObject` conforming to `SmartSpectraRunnerDelegate`):

```swift
@Published var insight = "Ask AI to analyze your vitals."

func requestInsight() {
  let requestId = runner.requestInsight(
    "Summarize my current vital signs and flag anything unusual.")
  insight = requestId < 0 ? "Insight request failed."
                          : "Analyzing… (request #\(requestId))"
}

// 5. Delegate callbacks — already marshaled to the main queue by the bridge.
func smartSpectraRunnerDidUpdateInsight(_ analysis: String, requestId: Int32) {
  insight = analysis
}

func smartSpectraRunnerDidFailInsight(_ message: String) {
  insight = "Error: \(message)"
}
```

View (`ContentView.swift`, with `@ObservedObject var model: AppModel`):

```swift
Button("Ask AI") { model.requestInsight() }
  .disabled(!model.isRunning)
Text(model.insight)
  .textSelection(.enabled)
```

Full sample app (metrics/preview; extend with the insight wiring above):
[`cpp/samples/macos_swiftui_example`](https://github.com/Presage-Security/SmartSpectra/tree/main/cpp/samples/macos_swiftui_example).

## See also

- [LLM Insights overview](../../docs/llm-insights/index.md)
- [C++ API reference](api-reference.md)
- [Data Types](../../docs/data-types.md)
