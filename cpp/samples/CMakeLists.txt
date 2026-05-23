# Copyright (C) 2024-2026 Presage Technologies, Inc.
#
# SPDX-License-Identifier: LicenseRef-Proprietary

# SmartSpectra C++ samples aggregate.
#
# Each `add_subdirectory(...)` line below is a portable CLI sample binary
# built and shipped with the public C++ SDK. App-style samples
# (`macos_swiftui_example`, `winui3_example`, `debian-app-example`) live
# alongside this tree but are built outside this aggregate by their own
# platform tooling. `debian-app-example` in particular is built standalone
# against an extracted SmartSpectra Linux SDK release tarball — see
# `smartspectra/cpp/samples/debian-app-example/README.md`.
cmake_minimum_required(VERSION 3.22.1)
project(SmartSpectraExamples CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if (NOT TARGET SmartSpectra::SDK)
    find_package(SmartSpectra REQUIRED)
endif ()

function(smartspectra_stage_windows_runtime target_name)
    if (NOT WIN32)
        return ()
    endif ()

    if (DEFINED SmartSpectra_DIR AND NOT SmartSpectra_DIR STREQUAL "")
        get_filename_component(_smartspectra_sdk_dir "${SmartSpectra_DIR}/../../.." ABSOLUTE)
    elseif (DEFINED SMARTSPECTRA_GRAPH_DIRECTORY AND NOT SMARTSPECTRA_GRAPH_DIRECTORY STREQUAL "")
        get_filename_component(_smartspectra_resource_dir "${SMARTSPECTRA_GRAPH_DIRECTORY}" DIRECTORY)
        get_filename_component(_smartspectra_share_dir "${_smartspectra_resource_dir}" DIRECTORY)
        get_filename_component(_smartspectra_sdk_dir "${_smartspectra_share_dir}" DIRECTORY)
    else ()
        message(FATAL_ERROR
            "Cannot resolve the SmartSpectra SDK directory for ${target_name}. "
            "Configure samples with find_package(SmartSpectra) through CMAKE_PREFIX_PATH.")
    endif ()

    file(GLOB _smartspectra_runtime_files
        "${_smartspectra_sdk_dir}/bin/*.dll")

    if (NOT _smartspectra_runtime_files)
        message(FATAL_ERROR
            "No SmartSpectra runtime DLLs found in ${_smartspectra_sdk_dir}/bin.")
    endif ()

    string(REPLACE "\\" "\\\\" _smartspectra_manifest_root "${_smartspectra_sdk_dir}/share/smartspectra")
    string(REPLACE "\"" "\\\"" _smartspectra_manifest_root "${_smartspectra_manifest_root}")
    file(GENERATE
        OUTPUT "$<TARGET_FILE_DIR:${target_name}>/smartspectra_manifest.json"
        CONTENT "{\n  \"resource_root_dir\": \"${_smartspectra_manifest_root}\"\n}\n")

    add_custom_command(TARGET ${target_name} POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
                ${_smartspectra_runtime_files}
                "$<TARGET_FILE_DIR:${target_name}>"
        VERBATIM)
endfunction ()

add_subdirectory(dense_facemesh_example)
add_subdirectory(minimal_example)
add_subdirectory(gstreamer_example)
add_subdirectory(full_example)
add_subdirectory(insights_example)
add_subdirectory(smart_spectra_example)
