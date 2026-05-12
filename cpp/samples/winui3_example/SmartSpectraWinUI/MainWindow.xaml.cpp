// MainWindow.xaml.cpp
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

#include "pch.h"
#include "MainWindow.xaml.h"
#if __has_include("MainWindow.g.cpp")
#include "MainWindow.g.cpp"
#endif

#include <algorithm>
#include <robuffer.h>

#include <smartspectra/smartspectra_config.h>
#include <smartspectra/smartspectra_types.h>
#include <smartspectra/messages/insights.pb.h>

namespace spectra = presage::smartspectra;

using namespace winrt;
using namespace ::winrt::Microsoft::UI::Dispatching;
using namespace ::winrt::Microsoft::UI::Xaml;
using namespace ::winrt::Microsoft::UI::Xaml::Controls;
using namespace ::winrt::Microsoft::UI::Xaml::Media;
using namespace ::winrt::Microsoft::UI::Xaml::Media::Imaging;
using namespace ::winrt::Microsoft::UI::Xaml::Shapes;
using namespace ::winrt::Windows::Foundation;
using namespace ::winrt::Windows::UI;

// Read the API key from the SMARTSPECTRA_API_KEY environment variable.
// Do NOT hard-code a real key here — set the environment variable instead:
//   setx SMARTSPECTRA_API_KEY "your-key-here"
static std::string ApiKey() {
    const char* env = std::getenv("SMARTSPECTRA_API_KEY");
    return (env && *env) ? env : std::string{};
}

namespace winrt::SmartSpectraWinUI::implementation
{
    namespace {
        std::wstring Widen(std::string const& s) {
            if (s.empty()) return {};
            auto h = winrt::to_hstring(s);
            return std::wstring(h.c_str(), h.size());
        }

        std::wstring UserFacing(spectra::SmartSpectraError const& e) {
            switch (e.code) {
                case spectra::SmartSpectraErrorCode::kAuthenticationFailed:
                    return L"Authentication failed. Check your API key.";
                case spectra::SmartSpectraErrorCode::kCreditExhausted:
                    return L"Account credits exhausted.";
                case spectra::SmartSpectraErrorCode::kNetworkError:
                    return e.retryable ? L"Network issue — please try again."
                                       : L"Network error: " + Widen(e.message);
                case spectra::SmartSpectraErrorCode::kInputUnavailable:
                    return L"Camera is unavailable. Check permissions.";
                case spectra::SmartSpectraErrorCode::kConfigurationFailed:
                    return L"Configuration failed: " + Widen(e.message);
                case spectra::SmartSpectraErrorCode::kInvalidState:
                    return L"SDK is in an invalid state for this action.";
                default:
                    return Widen(e.message);
            }
        }

        // Map a protobuf ExpressionType to an emoji glyph + display name.
        std::pair<std::wstring, std::wstring> ExpressionLabel(spectra::ExpressionType t) {
            switch (t) {
                case spectra::HAPPY:    return {L"\U0001F60A", L"Happy"};
                case spectra::SAD:      return {L"\U0001F622", L"Sad"};
                case spectra::ANGRY:    return {L"\U0001F620", L"Angry"};
                case spectra::SURPRISE: return {L"\U0001F62E", L"Surprised"};
                case spectra::FEAR:     return {L"\U0001F628", L"Fearful"};
                case spectra::DISGUST:  return {L"\U0001F922", L"Disgusted"};
                case spectra::CONTEMPT: return {L"\U0001F612", L"Contempt"};
                case spectra::NEUTRAL:  return {L"\U0001F610", L"Neutral"};
                default:             return {L"", L""};
            }
        }

        // Get a writable byte pointer into the bitmap's pixel buffer.
        uint8_t* PixelBufferData(WriteableBitmap const& bmp) {
            auto buf = bmp.PixelBuffer();
            com_ptr<::Windows::Storage::Streams::IBufferByteAccess> access;
            check_hresult(reinterpret_cast<::IUnknown*>(winrt::get_abi(buf))
                              ->QueryInterface(__uuidof(::Windows::Storage::Streams::IBufferByteAccess),
                                               access.put_void()));
            uint8_t* p = nullptr;
            check_hresult(access->Buffer(&p));
            return p;
        }
    }

    MainWindow::MainWindow() {
        InitializeComponent();
        Title(L"SmartSpectra Sample (WinUI 3)");

        m_ui_queue = DispatcherQueue::GetForCurrentThread();

        spectra::SmartSpectraConfig cfg;
        cfg.api_key = ApiKey();
        cfg.AddMetrics(spectra::SmartSpectraConfig::DefaultSupportedMetrics());
        cfg.AddMetrics(spectra::SmartSpectraConfig::CardioMetrics());
        cfg.AddMetrics(spectra::SmartSpectraConfig::FaceMetrics());
        m_spectra = std::make_unique<spectra::SmartSpectra>(std::move(cfg));

        if (auto err = m_spectra->UseCamera().Build(); !err.ok()) {
            StatusText().Text(L"Camera error: " + UserFacing(err));
        }

        // SmartSpectra stores these callbacks, and the window owns SmartSpectra.
        // Keep only a weak window reference here so teardown can release both.
        auto weak = get_weak();

        m_spectra->SetOnVideoOutput([weak](spectra::FrameBuffer const& fb, int64_t) {
            // RGB→BGRA on the worker thread; UI thread copies into the bitmap.
            auto pixels = std::make_shared<std::vector<uint8_t>>(
                static_cast<size_t>(fb.width) * fb.height * 4);
            for (int y = 0; y < fb.height; ++y) {
                const uint8_t* src = fb.data + y * fb.stride_bytes;
                uint8_t* dst = pixels->data() + (size_t)y * fb.width * 4;
                for (int x = 0; x < fb.width; ++x) {
                    dst[0] = src[2]; dst[1] = src[1]; dst[2] = src[0]; dst[3] = 255;
                    src += 3; dst += 4;
                }
            }
            int w = fb.width, h = fb.height;
            if (auto self = weak.get()) {
                self->m_ui_queue.TryEnqueue([weak, pixels, w, h] {
                    if (auto self = weak.get()) {
                        if (self->m_preview_w != w || self->m_preview_h != h) {
                            self->RebuildPreview(w, h);
                        }
                        std::copy_n(pixels->data(), pixels->size(), PixelBufferData(self->m_preview));
                        self->m_preview.Invalidate();
                    }
                });
            }
        });

        m_spectra->SetOnMetrics([weak](spectra::Metrics const& m, int64_t) {
            std::optional<float> hr;
            std::optional<float> br;
            std::optional<double> hrv_rmssd;
            std::vector<float> breath;
            std::vector<float> bp;
            std::optional<spectra::ExpressionType> expr_type;
            std::optional<float> expr_conf;
            if (m.has_cardio() && m.cardio().pulse_rate_size() > 0) {
                hr = m.cardio().pulse_rate(m.cardio().pulse_rate_size() - 1).value();
            }
            if (m.has_cardio() && m.cardio().hrv_size() > 0) {
                // RMSSD is the standard short-window HRV metric (vagal tone proxy).
                hrv_rmssd = m.cardio().hrv(m.cardio().hrv_size() - 1).rmssd();
            }
            if (m.has_breathing() && m.breathing().rate_size() > 0) {
                br = m.breathing().rate(m.breathing().rate_size() - 1).value();
            }
            if (m.has_breathing()) {
                breath.reserve(m.breathing().upper_trace_size());
                for (auto const& s : m.breathing().upper_trace()) breath.push_back(s.value());
            }
            if (m.has_cardio()) {
                bp.reserve(m.cardio().arterial_pressure_trace_size());
                for (auto const& s : m.cardio().arterial_pressure_trace()) bp.push_back(s.value());
            }
            if (m.has_face() && m.face().expression_size() > 0) {
                auto const& latest = m.face().expression(m.face().expression_size() - 1);
                spectra::ExpressionScore const* best = nullptr;
                for (auto const& s : latest.scores()) {
                    if (!best || s.confidence() > best->confidence()) best = &s;
                }
                if (best && best->type() != spectra::UNSPECIFIED) {
                    expr_type = best->type();
                    expr_conf = best->confidence();
                }
            }
            if (auto self = weak.get()) {
                self->m_ui_queue.TryEnqueue([weak, hr, br, hrv_rmssd, expr_type, expr_conf,
                                             breath = std::move(breath), bp = std::move(bp)] {
                    if (auto self = weak.get()) {
                        if (hr) {
                            wchar_t buf[64];
                            swprintf_s(buf, L"%d bpm", (int)*hr);
                            self->HeartRateText().Text(buf);
                        }
                        if (hrv_rmssd) {
                            wchar_t buf[64];
                            swprintf_s(buf, L"HRV %d ms", (int)*hrv_rmssd);
                            self->HrvText().Text(buf);
                        }
                        if (br) {
                            wchar_t buf[96];
                            swprintf_s(buf, L"Breathing Rate — %d brpm", (int)*br);
                            self->BreathingRateText().Text(buf);
                        }
                        if (expr_type) {
                            auto [emoji, name] = ExpressionLabel(*expr_type);
                            self->ExpressionEmoji().Text(emoji);
                            wchar_t buf[64];
                            swprintf_s(buf, L"%ls  %d%%", name.c_str(),
                                       (int)(expr_conf ? *expr_conf : 0.f));
                            self->ExpressionText().Text(buf);
                        }
                        if (!breath.empty()) {
                            self->m_breath_trace.appendN(breath.data(), breath.size());
                            self->RenderGraph(self->BreathingCanvas(), self->m_breath_trace,
                                              Color{255, 79, 204, 197});
                        }
                        if (!bp.empty()) {
                            self->m_bp_trace.appendN(bp.data(), bp.size());
                            self->RenderGraph(self->BpCanvas(), self->m_bp_trace,
                                              Color{255, 166, 140, 250});
                        }
                    }
                });
            }
        });

        m_spectra->SetOnValidationStatusChanged([weak](spectra::ValidationStatus const& vs, int64_t) {
            std::wstring hint = Widen(vs.hint);
            if (auto self = weak.get()) {
                self->m_ui_queue.TryEnqueue([weak, hint] {
                    if (auto self = weak.get()) {
                        if (hint.empty()) {
                            self->ValidationPill().Visibility(Visibility::Collapsed);
                        } else {
                            self->ValidationText().Text(hint);
                            self->ValidationPill().Visibility(Visibility::Visible);
                        }
                    }
                });
            }
        });

        m_spectra->SetOnProcessingStatusChanged([weak](spectra::ProcessingStatus s) {
            if (auto self = weak.get()) {
                self->m_ui_queue.TryEnqueue([weak, s] {
                    if (auto self = weak.get()) {
                        switch (s) {
                            case spectra::ProcessingStatus::kIdle:
                                self->StatusText().Text(L"Idle");
                                self->ToggleButton().Content(box_value(L"Start"));
                                self->ToggleButton().IsEnabled(true);
                                self->m_running = false;
                                break;
                            case spectra::ProcessingStatus::kStarting:
                                self->StatusText().Text(L"Starting");
                                self->ToggleButton().IsEnabled(false);
                                break;
                            case spectra::ProcessingStatus::kRunning:
                                self->StatusText().Text(L"Running");
                                self->ToggleButton().Content(box_value(L"Stop"));
                                self->ToggleButton().IsEnabled(true);
                                self->m_running = true;
                                break;
                            case spectra::ProcessingStatus::kStopping:
                                self->StatusText().Text(L"Stopping");
                                self->ToggleButton().IsEnabled(false);
                                break;
                            case spectra::ProcessingStatus::kError:
                                self->StatusText().Text(L"Error");
                                self->ToggleButton().Content(box_value(L"Start"));
                                self->ToggleButton().IsEnabled(true);
                                self->m_running = false;
                                break;
                            default:
                                break;
                        }
                    }
                });
            }
        });

        m_spectra->SetOnInsight([weak](spectra::Insight const& insight) {
            std::wstring text;
            if (insight.has_analysis()) text = Widen(insight.analysis());
            else if (insight.has_error()) text = L"Error: " + Widen(insight.error());
            else return;
            if (auto self = weak.get()) {
                self->m_ui_queue.TryEnqueue([weak, text] {
                    if (auto self = weak.get()) {
                        self->InsightText().Text(text);
                        self->InsightButton().IsEnabled(true);
                    }
                });
            }
        });

        m_spectra->SetOnError([weak](spectra::SmartSpectraError const& err) {
            std::wstring msg = UserFacing(err);
            if (auto self = weak.get()) {
                self->m_ui_queue.TryEnqueue([weak, msg] {
                    if (auto self = weak.get()) {
                        self->StatusText().Text(L"Error: " + msg);
                    }
                });
            }
        });
    }

    MainWindow::~MainWindow() {
        std::thread lifecycle_thread;
        {
            std::lock_guard<std::mutex> lock(m_lifecycle_mutex);
            lifecycle_thread = std::move(m_lifecycle_thread);
        }
        // Let any async Start/Stop finish before the final shutdown Stop().
        if (lifecycle_thread.joinable()) lifecycle_thread.join();
        if (m_spectra) (void)m_spectra->Stop();
    }

    void MainWindow::OnToggleClick(IInspectable const&, RoutedEventArgs const&) {
        if (m_running) {
            RunLifecycleAsync([](spectra::SmartSpectra& spec) { (void)spec.Stop(); });
            return;
        }
        m_breath_trace.reset();
        m_bp_trace.reset();
        HeartRateText().Text(L"-- bpm");
        HrvText().Text(L"");
        BreathingRateText().Text(L"Breathing Rate");
        ExpressionEmoji().Text(L"");
        ExpressionText().Text(L"");
        InsightText().Text(L"Tap Ask AI to get an analysis of your vitals.");
        BreathingCanvas().Children().Clear();
        BpCanvas().Children().Clear();
        RunLifecycleAsync([](spectra::SmartSpectra& spec) { (void)spec.Start(); });
    }

    void MainWindow::OnInsightClick(IInspectable const&, RoutedEventArgs const&) {
        if (!m_running) return;
        InsightButton().IsEnabled(false);
        InsightText().Text(L"Analyzing…");
        int32_t request_id = -1;
        auto err = m_spectra->RequestInsight(
            "Summarize my current vital signs and flag anything unusual.",
            &request_id);
        if (!err.ok()) {
            InsightText().Text(L"Error: " + UserFacing(err));
            InsightButton().IsEnabled(true);
        }
    }

    void MainWindow::OnGraphCanvasSizeChanged(IInspectable const&,
                                              SizeChangedEventArgs const&) {
        RenderGraph(BreathingCanvas(), m_breath_trace, Color{255, 79, 204, 197});
        RenderGraph(BpCanvas(), m_bp_trace, Color{255, 166, 140, 250});
    }

    void MainWindow::RebuildPreview(int w, int h) {
        m_preview = WriteableBitmap(w, h);
        m_preview_w = w;
        m_preview_h = h;
        PreviewImage().Source(m_preview);
    }

    void MainWindow::RunLifecycleAsync(
        std::function<void(spectra::SmartSpectra&)> operation) {
        std::lock_guard<std::mutex> lock(m_lifecycle_mutex);
        if (m_lifecycle_thread.joinable()) m_lifecycle_thread.join();
        m_lifecycle_thread = std::thread(
            [spec = m_spectra.get(), operation = std::move(operation)]() mutable {
                if (spec) operation(*spec);
            });
    }

    void MainWindow::RenderGraph(Canvas const& canvas, LineGraph const& trace,
                                 Color const& line_color) {
        canvas.Children().Clear();
        if (trace.points.size() < 2) return;
        double cw = canvas.ActualWidth();
        double ch = canvas.ActualHeight();
        if (cw <= 4 || ch <= 4) return;

        const double inset = 4;
        double aw = cw - 2 * inset;
        double ah = ch - 2 * inset;

        float vmin = trace.points.front();
        float vmax = vmin;
        for (float v : trace.points) {
            if (v < vmin) vmin = v;
            if (v > vmax) vmax = v;
        }
        float range = vmax - vmin;

        // 25/50/75% grid lines.
        for (double frac : {0.25, 0.5, 0.75}) {
            Line grid;
            double y = inset + ah * (1.0 - frac);
            grid.X1(inset);
            grid.Y1(y);
            grid.X2(inset + aw);
            grid.Y2(y);
            grid.Stroke(SolidColorBrush(Color{15, 255, 255, 255}));
            grid.StrokeThickness(0.5);
            canvas.Children().Append(grid);
        }

        std::vector<Point> pts;
        pts.reserve(trace.points.size());
        size_t n = trace.points.size();
        size_t i = 0;
        for (float v : trace.points) {
            double x = inset + aw * (double)i / (double)(n - 1);
            double norm = range > 0 ? (v - vmin) / range : 0.5;
            double y = inset + ah - norm * ah;
            pts.push_back(Point{(float)x, (float)y});
            ++i;
        }

        // Glow stroke under the main stroke.
        Shapes::Polyline glow;
        for (auto const& p : pts) glow.Points().Append(p);
        glow.Stroke(SolidColorBrush(Color{
            80, line_color.R, line_color.G, line_color.B}));
        glow.StrokeThickness(6);
        glow.StrokeLineJoin(PenLineJoin::Round);
        glow.StrokeStartLineCap(PenLineCap::Round);
        glow.StrokeEndLineCap(PenLineCap::Round);
        canvas.Children().Append(glow);

        Shapes::Polyline main_line;
        for (auto const& p : pts) main_line.Points().Append(p);
        main_line.Stroke(SolidColorBrush(line_color));
        main_line.StrokeThickness(2);
        main_line.StrokeLineJoin(PenLineJoin::Round);
        canvas.Children().Append(main_line);
    }
}
