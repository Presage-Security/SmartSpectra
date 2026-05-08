cmake_minimum_required(VERSION 3.24)

if(NOT CMAKE_OSX_DEPLOYMENT_TARGET)
    set(CMAKE_OSX_DEPLOYMENT_TARGET "14.0" CACHE STRING
        "Minimum macOS version required by the SmartSpectra SDK.")
endif()

project(smartspectra_swift_ui LANGUAGES CXX OBJCXX Swift)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(SMARTSPECTRA_SDK_ROOT "" CACHE PATH "SmartSpectra SDK install prefix.")
set(SMARTSPECTRA_DEVELOPMENT_TEAM "" CACHE STRING "Apple Developer Team ID used by Xcode signing.")
set(SMARTSPECTRA_BUNDLE_IDENTIFIER "com.example.smartspectra-swift-ui" CACHE STRING "macOS app bundle identifier.")
set(SMARTSPECTRA_HOMEBREW_PREFIX "/opt/homebrew" CACHE PATH "Homebrew prefix used for OpenCV, Vulkan, and MoltenVK runtime libraries.")
option(SMARTSPECTRA_REQUIRE_SIGNING "Require Apple Development signing at configure time." ON)

if(NOT APPLE)
    message(FATAL_ERROR "This example builds a macOS SwiftUI app and currently requires Apple platforms.")
endif()

if(NOT SMARTSPECTRA_SDK_ROOT)
    foreach(_prefix IN LISTS CMAKE_PREFIX_PATH)
        if(EXISTS "${_prefix}/lib/cmake/SmartSpectra/SmartSpectraConfig.cmake")
            set(SMARTSPECTRA_SDK_ROOT "${_prefix}" CACHE PATH "SmartSpectra SDK install prefix." FORCE)
            break()
        endif()
    endforeach()
endif()

if(NOT SMARTSPECTRA_SDK_ROOT)
    message(FATAL_ERROR
        "SMARTSPECTRA_SDK_ROOT is required. Set it to the SDK install prefix, "
        "for example /path/to/smartspectra/install.")
endif()

if(SMARTSPECTRA_SDK_ROOT)
    list(PREPEND CMAKE_PREFIX_PATH "${SMARTSPECTRA_SDK_ROOT}")
    set(PHYSIOLOGY_EDGE_GRAPH_DIRECTORY
        "${SMARTSPECTRA_SDK_ROOT}/share/smartspectra/graph"
        CACHE PATH "Directory of the Physiology computation graphs." FORCE)
    set(PHYSIOLOGY_EDGE_MODEL_DIRECTORY
        "${SMARTSPECTRA_SDK_ROOT}/share/smartspectra/graph/models"
        CACHE PATH "Directory of the Physiology inference models." FORCE)
endif()

if(EXISTS "${SMARTSPECTRA_HOMEBREW_PREFIX}")
    list(PREPEND CMAKE_PREFIX_PATH "${SMARTSPECTRA_HOMEBREW_PREFIX}")
endif()

find_package(SmartSpectra CONFIG REQUIRED)
find_package(OpenCV REQUIRED COMPONENTS core imgproc)

foreach(_required_file IN ITEMS
    "${SMARTSPECTRA_SDK_ROOT}/lib/libsmartspectra.dylib"
    "${PHYSIOLOGY_EDGE_GRAPH_DIRECTORY}/metrics_cpu_continuous_rest.binarypb"
    "${PHYSIOLOGY_EDGE_MODEL_DIRECTORY}/face_landmark_with_attention.tflite"
    "${PHYSIOLOGY_EDGE_MODEL_DIRECTORY}/pose_detection.tflite"
    "${PHYSIOLOGY_EDGE_MODEL_DIRECTORY}/pose_landmark_lite.tflite")
    if(NOT EXISTS "${_required_file}")
        message(WARNING "Expected SmartSpectra runtime file is missing: ${_required_file}")
    endif()
endforeach()

if(NOT SMARTSPECTRA_DEVELOPMENT_TEAM)
    if(SMARTSPECTRA_REQUIRE_SIGNING)
        message(FATAL_ERROR
            "SMARTSPECTRA_DEVELOPMENT_TEAM is required because SmartSpectra writes "
            "SDK state to Keychain. Set SMARTSPECTRA_DEVELOPMENT_TEAM or configure "
            "with -DSMARTSPECTRA_REQUIRE_SIGNING=OFF for project-generation-only use.")
    else()
        message(WARNING
            "SMARTSPECTRA_DEVELOPMENT_TEAM is empty. The app can be generated, but "
            "a working Keychain-enabled run needs an Apple Development signature.")
    endif()
endif()

if(APPLE)
    add_executable(smartspectra_swift_ui
        MACOSX_BUNDLE
        swift_ui/SmartSpectraSwiftApp.swift
        swift_ui/ContentView.swift
        swift_ui/AppModel.swift
        swift_ui/SmartSpectraRunner.h
        swift_ui/SmartSpectraRunner.mm
    )

    target_include_directories(smartspectra_swift_ui PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/swift_ui"
        "${SMARTSPECTRA_SDK_ROOT}/include/smartspectra/interface"
        ${OpenCV_INCLUDE_DIRS}
    )
    target_link_libraries(smartspectra_swift_ui PRIVATE
        SmartSpectra::SDK
        ${OpenCV_LIBS}
        "-framework AppKit"
    )

    set_target_properties(smartspectra_swift_ui PROPERTIES
        BUILD_RPATH "${SMARTSPECTRA_SDK_ROOT}/lib;${SMARTSPECTRA_HOMEBREW_PREFIX}/lib"
        MACOSX_BUNDLE_BUNDLE_NAME "SmartSpectraSwiftUI"
        MACOSX_BUNDLE_BUNDLE_VERSION "1.0"
        MACOSX_BUNDLE_COPYRIGHT ""
        MACOSX_BUNDLE_GUI_IDENTIFIER "${SMARTSPECTRA_BUNDLE_IDENTIFIER}"
        MACOSX_BUNDLE_INFO_PLIST "${CMAKE_CURRENT_SOURCE_DIR}/cmake/Info.plist.in"
        XCODE_ATTRIBUTE_DEVELOPMENT_TEAM "${SMARTSPECTRA_DEVELOPMENT_TEAM}"
        XCODE_ATTRIBUTE_CODE_SIGN_STYLE "Automatic"
        XCODE_ATTRIBUTE_CODE_SIGN_IDENTITY "Apple Development"
        XCODE_ATTRIBUTE_PRODUCT_BUNDLE_IDENTIFIER "${SMARTSPECTRA_BUNDLE_IDENTIFIER}"
        XCODE_ATTRIBUTE_CODE_SIGN_ENTITLEMENTS "${CMAKE_CURRENT_SOURCE_DIR}/smartspectra_swift_ui.entitlements"
        XCODE_ATTRIBUTE_MACOSX_DEPLOYMENT_TARGET "${CMAKE_OSX_DEPLOYMENT_TARGET}"
        XCODE_ATTRIBUTE_SWIFT_OBJC_BRIDGING_HEADER "${CMAKE_CURRENT_SOURCE_DIR}/swift_ui/SmartSpectraSwiftBridge.h"
        XCODE_ATTRIBUTE_SWIFT_VERSION "5.0"
        XCODE_ATTRIBUTE_CLANG_ENABLE_MODULES "YES"
        XCODE_ATTRIBUTE_CLANG_ENABLE_OBJC_ARC "YES"
    )
endif()
