# Copyright (C) 2024-2026 Presage Technologies, Inc.
#
# SPDX-License-Identifier: LicenseRef-Proprietary

set(LIBRARY_NAME FullExampleGui)
# Sample-support helper only; keep this target local to the source tree.

add_library(${LIBRARY_NAME} STATIC)

target_sources(${LIBRARY_NAME}
    PRIVATE
        opencv_trace_plotter.cpp
        confidence_thresholding.cpp
        opencv_value_indicator.cpp
        opencv_metrics_group.cpp
        opencv_hud.cpp
        opencv_element_fits.cpp
        opencv_label.cpp
        opencv_trace_plotter.hpp
        confidence_thresholding.hpp
        opencv_value_indicator.hpp
        opencv_metrics_group.hpp
        opencv_hud.hpp
        opencv_element_fits.hpp
        opencv_label.hpp
)

target_include_directories(${LIBRARY_NAME}
        PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}>
        $<BUILD_INTERFACE:${PROJECT_SOURCE_DIR}>
        $<INSTALL_INTERFACE:${CMAKE_INSTALL_INCLUDEDIR}>
)

target_include_directories(${LIBRARY_NAME}
        SYSTEM PUBLIC
        ${OpenCV_INCLUDE_DIRS}
)

target_link_libraries(${LIBRARY_NAME} PUBLIC ${OpenCV_LIBS})
