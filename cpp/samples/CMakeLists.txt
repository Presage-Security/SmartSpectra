# Copyright (C) 2024-2026 Presage Technologies, Inc.
#
# SPDX-License-Identifier: LicenseRef-Proprietary

# SmartSpectra C++ samples aggregate.
#
# Each `add_subdirectory(...)` line below is a portable CLI sample binary
# built and shipped with the public C++ SDK. App-style samples
# (`macos_swiftui_example`, `winui3_example`) live alongside this tree
# but are built outside this aggregate by their own platform tooling.
cmake_minimum_required(VERSION 3.22.1)
project(SmartSpectraExamples CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if (NOT TARGET SmartSpectra::SDK)
    find_package(SmartSpectra REQUIRED)
endif ()

add_subdirectory(dense_facemesh_example)
add_subdirectory(minimal_example)
add_subdirectory(gstreamer_example)
add_subdirectory(full_example)
add_subdirectory(insights_example)
add_subdirectory(smart_spectra_example)
