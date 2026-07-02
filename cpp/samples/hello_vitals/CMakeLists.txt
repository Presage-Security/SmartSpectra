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
