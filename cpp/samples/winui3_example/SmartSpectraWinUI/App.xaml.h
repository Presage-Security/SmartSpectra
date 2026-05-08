// App.xaml.h
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

#pragma once
#include "App.xaml.g.h"

namespace winrt::SmartSpectraWinUI::implementation
{
    struct App : AppT<App>
    {
        App();
        void OnLaunched(::winrt::Microsoft::UI::Xaml::LaunchActivatedEventArgs const&);

    private:
        ::winrt::Microsoft::UI::Xaml::Window m_window{ nullptr };
    };
}
