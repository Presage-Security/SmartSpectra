cmake_minimum_required(VERSION 3.22.1)

if (CMAKE_SOURCE_DIR STREQUAL CMAKE_CURRENT_SOURCE_DIR)
    project(SmartSpectraHelloVitals CXX)
endif ()

if (DEFINED ENV{SMARTSPECTRA_SDK_PATH} AND NOT "$ENV{SMARTSPECTRA_SDK_PATH}" STREQUAL "")
    list(PREPEND CMAKE_PREFIX_PATH "$ENV{SMARTSPECTRA_SDK_PATH}")
endif ()

if (NOT TARGET SmartSpectra::SDK)
    find_package(SmartSpectra CONFIG REQUIRED)
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
            "Configure hello_vitals with find_package(SmartSpectra) through CMAKE_PREFIX_PATH "
            "or set SMARTSPECTRA_SDK_PATH before running CMake.")
    endif ()

    file(GLOB _smartspectra_runtime_files
        "${_smartspectra_sdk_dir}/bin/*.dll")

    if (NOT _smartspectra_runtime_files)
        message(FATAL_ERROR
            "No SmartSpectra runtime DLLs found in ${_smartspectra_sdk_dir}/bin.")
    endif ()

    string(REPLACE "\\" "/" _smartspectra_manifest_root "${_smartspectra_sdk_dir}/share/smartspectra")
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

add_executable(hello_vitals hello_vitals.cpp)
set_target_properties(hello_vitals PROPERTIES
    CXX_STANDARD 20
    CXX_STANDARD_REQUIRED ON
    CXX_EXTENSIONS OFF)

if (MSVC)
    set_property(DIRECTORY PROPERTY VS_STARTUP_PROJECT hello_vitals)
    set_target_properties(hello_vitals PROPERTIES
        VS_DEBUGGER_COMMAND "$<TARGET_FILE:hello_vitals>"
        VS_DEBUGGER_WORKING_DIRECTORY "$<TARGET_FILE_DIR:hello_vitals>")
endif ()

target_link_libraries(hello_vitals PRIVATE SmartSpectra::SDK)

smartspectra_stage_windows_runtime(hello_vitals)
