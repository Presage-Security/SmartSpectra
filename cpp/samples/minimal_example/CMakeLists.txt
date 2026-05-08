# Copyright (C) 2024-2026 Presage Technologies, Inc.
#
# SPDX-License-Identifier: LicenseRef-Proprietary

set(EXECUTABLE_NAME minimal_example)

add_executable(${EXECUTABLE_NAME} main.cc)

target_link_libraries(${EXECUTABLE_NAME}
        SmartSpectra::SDK
)

if (INSTALL_SAMPLES)
    install(TARGETS ${EXECUTABLE_NAME}
            EXPORT ${PROJECT_NAME}Targets
    )
endif ()
