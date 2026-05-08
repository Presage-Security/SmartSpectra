// App.xaml.cpp
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

#include "pch.h"
#include "App.xaml.h"
#include "MainWindow.xaml.h"

using namespace winrt;
using namespace ::winrt::Microsoft::UI::Xaml;

namespace winrt::SmartSpectraWinUI::implementation
{
    App::App() {
        // XAML objects should not call InitializeComponent during construction.
        // See https://github.com/microsoft/cppwinrt/tree/master/nuget#initializecomponent
    }

    void App::OnLaunched(LaunchActivatedEventArgs const&) {
        m_window = make<MainWindow>();
        m_window.Activate();
    }
}
