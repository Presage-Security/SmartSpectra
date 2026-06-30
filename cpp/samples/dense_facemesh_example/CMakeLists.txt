# Copyright (C) 2025-2026 Presage Technologies, Inc.
#
# SPDX-License-Identifier: LicenseRef-Proprietary

set(EXECUTABLE_NAME dense_facemesh)

find_package(OpenCV REQUIRED core imgcodecs imgproc highgui)

add_executable(${EXECUTABLE_NAME} main.cc)

target_link_libraries(${EXECUTABLE_NAME}
        SmartSpectra::SDK
        ${OpenCV_LIBS}
)

if (INSTALL_SAMPLES)
    install(TARGETS ${EXECUTABLE_NAME}
            EXPORT ${PROJECT_NAME}Targets
    )
endif ()
