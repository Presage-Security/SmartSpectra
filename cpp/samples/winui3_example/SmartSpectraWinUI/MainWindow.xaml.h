// MainWindow.xaml.h
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

#pragma once
#include "MainWindow.g.h"

#include <smartspectra/smartspectra.h>

namespace winrt::SmartSpectraWinUI::implementation
{
    struct MainWindow : MainWindowT<MainWindow>
    {
        MainWindow();
        ~MainWindow();

        void OnToggleClick(::winrt::Windows::Foundation::IInspectable const&,
                           ::winrt::Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnInsightClick(::winrt::Windows::Foundation::IInspectable const&,
                            ::winrt::Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnGraphCanvasSizeChanged(::winrt::Windows::Foundation::IInspectable const&,
                                      ::winrt::Microsoft::UI::Xaml::SizeChangedEventArgs const&);

    private:
        struct LineGraph {
            std::deque<float> points;
            static constexpr size_t kMax = 200;
            void append(float v) {
                points.push_back(v);
                while (points.size() > kMax) points.pop_front();
            }
            void appendN(const float* d, size_t n) {
                for (size_t i = 0; i < n; ++i) append(d[i]);
            }
            void reset() { points.clear(); }
        };

        std::unique_ptr<presage::smartspectra::SmartSpectra> m_spectra;
        LineGraph m_breath_trace;
        LineGraph m_bp_trace;
        bool m_running = false;
        std::thread m_lifecycle_thread;
        std::mutex m_lifecycle_mutex;
        ::winrt::Microsoft::UI::Dispatching::DispatcherQueue m_ui_queue{ nullptr };

        ::winrt::Microsoft::UI::Xaml::Media::Imaging::WriteableBitmap m_preview{ nullptr };
        int m_preview_w = 0;
        int m_preview_h = 0;

        void RenderGraph(::winrt::Microsoft::UI::Xaml::Controls::Canvas const& canvas,
                         LineGraph const& trace,
                         ::winrt::Windows::UI::Color const& line_color);
        void RebuildPreview(int w, int h);
        void RunLifecycleAsync(
            std::function<void(presage::smartspectra::SmartSpectra&)> operation);
    };
}

namespace winrt::SmartSpectraWinUI::factory_implementation
{
    struct MainWindow : MainWindowT<MainWindow, implementation::MainWindow> {};
}
