# Copyright (C) 2026 Presage Technologies, Inc.
#
# SPDX-License-Identifier: LicenseRef-Proprietary

# debian-app-example: standalone SmartSpectra C++ SDK redistribution example.
#
# This CMakeLists is built OUT-OF-TREE against an extracted SmartSpectra Linux
# SDK release tarball (`smartspectra-sdk-<version>-linux-<arch>.tar.gz`). It
# consumes the SDK strictly through find_package(SmartSpectra) and is NOT
# added to smartspectra/cpp/samples/CMakeLists.txt's add_subdirectory(...)
# aggregate. CI exercises it via the verify_redistribution_example_linux_*
# job pair in smartspectra/cpp/.gitlab-ci.yml.
#
# Build (after extracting the SDK tarball to /tmp/sdk):
#   cmake -S . -B /tmp/build -DCMAKE_PREFIX_PATH=/tmp/sdk -DCMAKE_BUILD_TYPE=Release
#   cmake --build /tmp/build -j$(nproc)
#   cd /tmp/build && cpack -G DEB

cmake_minimum_required(VERSION 3.22.1)
project(debian-app-example VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(SmartSpectra REQUIRED)

add_executable(debian-app-example main.cc)
target_link_libraries(debian-app-example PRIVATE SmartSpectra::SDK)

# Resolve bundled runtime libs (libsmartspectra.so + Presage opencv) relative
# to the binary at install time: /opt/debian-app-example/bin/<exe> -> ../lib.
# INSTALL_RPATH_USE_LINK_PATH=FALSE prevents the link-time RPATH from polluting
# the final RUNPATH; the resulting DT_RUNPATH is exactly `$ORIGIN/../lib`.
set_target_properties(debian-app-example PROPERTIES
    INSTALL_RPATH "\$ORIGIN/../lib"
    INSTALL_RPATH_USE_LINK_PATH FALSE
    BUILD_WITH_INSTALL_RPATH FALSE
)

# ======================================================================
# CPack DEB install layout
# ======================================================================
#
# App-private install prefix per FHS 3.0 §3.12. All files land under
# /opt/debian-app-example/ except the postinst-written ldconfig fragment
# (group 3). Paths are absolute so the .deb's layout is unconditionally
# /opt/debian-app-example/... regardless of the build's CMAKE_INSTALL_PREFIX.

set(DAE_INSTALL_PREFIX "/opt/debian-app-example")
set(DAE_BIN_DIR        "${DAE_INSTALL_PREFIX}/bin")
set(DAE_LIB_DIR        "${DAE_INSTALL_PREFIX}/lib")
set(DAE_OPENCV_DIR     "${DAE_LIB_DIR}/smartspectra")
set(DAE_SHARE_DIR      "${DAE_INSTALL_PREFIX}/share/smartspectra")

install(TARGETS debian-app-example DESTINATION "${DAE_BIN_DIR}")

# Resolve bundled-lib + model source paths from SmartSpectraConfig's exported
# CACHE variables. SMARTSPECTRA_LIB_DIRECTORY and SMARTSPECTRA_GRAPH_DIRECTORY
# (cf. edge/cmake/templates/SmartSpectraConfig.cmake.in) are the stable
# contract — they persist in user scope after find_package() returns.
#
# Do NOT use PACKAGE_PREFIX_DIR here: CMake 4.x no longer leaks that variable
# to the caller after the config script finishes (under CMake 3.x it happened
# to persist, but that was never the documented contract). Do NOT walk
# ${SmartSpectra_DIR}/../../... arithmetic either — three levels deep is
# brittle against install-layout changes.
set(_dae_sdk_lib_dir "${SMARTSPECTRA_LIB_DIRECTORY}")

# Bundle libsmartspectra.so. The SDK ships it in <sdk>/lib/.
install(
    FILES "${_dae_sdk_lib_dir}/libsmartspectra.so"
    DESTINATION "${DAE_LIB_DIR}"
)

# Bundle the eight-module Presage OpenCV runtime, preserving the SDK's
# lib/smartspectra/ subdir layout. The SDK ships them at
# <sdk>/lib/smartspectra/libopencv_*.so.411; we install them to
# /opt/debian-app-example/lib/smartspectra/ so the redistributed layout
# mirrors what apt install of the SDK produces under /usr/local/lib/smartspectra/.
# Load-time resolution still works because libsmartspectra.so has no
# DT_RUNPATH (cc_shared_library strips $ORIGIN linkopts), so its
# DT_NEEDED libopencv_*.so.411 chain is resolved via the ldconfig cache
# populated by the postinst-registered .conf (which lists both
# /opt/debian-app-example/lib and /opt/debian-app-example/lib/smartspectra).
# The binary's RPATH=$ORIGIN/../lib was never load-bearing for OpenCV: under
# --enable-new-dtags (linker default) DT_RUNPATH does not propagate to
# transitive dependencies — it only resolves libsmartspectra.so itself.
set(_dae_opencv_modules
    calib3d core features2d flann imgcodecs imgproc video videoio
)
foreach(_mod ${_dae_opencv_modules})
    install(
        FILES "${_dae_sdk_lib_dir}/smartspectra/libopencv_${_mod}.so.411"
        DESTINATION "${DAE_OPENCV_DIR}"
    )
endforeach()

# Bundle the SDK's graph/ tree (models + computation graphs). Source layout:
# <sdk>/share/graph/{models,...}; bundled-into layout:
# /opt/debian-app-example/share/smartspectra/graph/{models,...}. The
# resource_root_dir written by the manifest (below) points at the parent of
# graph/, matching how SmartSpectra's resource resolver works (it prepends
# resource_root_dir to relative paths like "graph/models/face_detection.tflite").
get_filename_component(_dae_sdk_share_dir "${SMARTSPECTRA_GRAPH_DIRECTORY}" DIRECTORY)
install(
    DIRECTORY "${_dae_sdk_share_dir}/graph"
    DESTINATION "${DAE_SHARE_DIR}"
)

# Write smartspectra_manifest.json at install time. Lives next to
# libsmartspectra.so under /opt/debian-app-example/lib/ — the SDK's
# resource lookup reads the manifest from libsmartspectra.so's own
# directory. Written at install time (not build time) so the recorded
# prefix matches the .deb's actual install location, not the build prefix.
# DAE_SHARE_DIR is a Linux absolute path under /opt/, so no JSON-escaping
# of backslashes or double-quotes is required.
install(CODE "
    file(WRITE \"\$ENV{DESTDIR}${DAE_LIB_DIR}/smartspectra_manifest.json\"
         \"{\\n  \\\"resource_root_dir\\\": \\\"${DAE_SHARE_DIR}\\\"\\n}\\n\")
")

# ======================================================================
# CPack DEB generator
# ======================================================================

# Detect the Debian-family codename so the .deb's revision suffix matches
# the SDK's own per-codename mechanism (cf.
# edge/cmake/extensions/SmartSpectra_packaging.cmake:98-103).
# Inline detection here intentionally — including the SDK's distro_name
# helper would violate the out-of-tree constraint (D8 in the change folder's
# design.md).
if(NOT EXISTS "/etc/os-release")
    message(FATAL_ERROR
        "debian-app-example: cannot detect distro codename — /etc/os-release "
        "is missing. CPack DEB packaging is supported only on Debian-family "
        "hosts.")
endif()
file(READ "/etc/os-release" _dae_os_release)
string(REGEX MATCH "VERSION_CODENAME=([a-z]+)" _dae_codename_match "${_dae_os_release}")
if(NOT CMAKE_MATCH_1)
    message(FATAL_ERROR
        "debian-app-example: failed to parse VERSION_CODENAME from "
        "/etc/os-release — confirm the build host is Debian-family "
        "(Ubuntu / Debian / Mint).")
endif()
set(DAE_DISTRO_CODENAME "${CMAKE_MATCH_1}")

set(CPACK_GENERATOR "DEB")
set(CPACK_PACKAGE_NAME "debian-app-example")
set(CPACK_PACKAGE_VERSION "${PROJECT_VERSION}")
set(CPACK_DEBIAN_PACKAGE_RELEASE "${DAE_DISTRO_CODENAME}1")
set(CPACK_DEBIAN_PACKAGE_ARCHITECTURE "amd64")
set(CPACK_DEBIAN_FILE_NAME "DEB-DEFAULT")
set(CPACK_PACKAGING_INSTALL_PREFIX "/")
set(CPACK_SET_DESTDIR ON)
set(CPACK_DEBIAN_PACKAGE_MAINTAINER
    "Presage Technologies <support@presagetech.com>")
set(CPACK_DEBIAN_PACKAGE_DESCRIPTION
    "Standalone SmartSpectra C++ SDK redistribution example.\n\
 Demonstrates the validated Linux redistribution path: consume the\n\
 published SmartSpectra release tarball via find_package(SmartSpectra),\n\
 privately bundle the runtime, and ship a .deb that installs on stock\n\
 Ubuntu without a Presage apt source.")
set(CPACK_DEBIAN_PACKAGE_SECTION "misc")
set(CPACK_DEBIAN_PACKAGE_PRIORITY "optional")

# Depends: mirror the SDK's own per-codename Depends list by reading it from
# the SmartSpectraPackageManifest.json the SDK ships under
# <sdk>/share/smartspectra/package/. This keeps the example's apt-resolved
# transitive tail mechanically in sync with what the SDK was built against —
# a hard-coded literal would silently rot when the SDK adds / drops a dep.
# The manifest path lives one directory above SMARTSPECTRA_GRAPH_DIRECTORY
# (cf. edge/cmake/templates/SmartSpectraConfig.cmake.in:5, which exports
# SMARTSPECTRA_GRAPH_DIRECTORY = ${PACKAGE_PREFIX_DIR}/share/smartspectra/graph).
set(_dae_manifest_path
    "${_dae_sdk_share_dir}/package/SmartSpectraPackageManifest.json")
if(NOT EXISTS "${_dae_manifest_path}")
    message(FATAL_ERROR
        "debian-app-example: SmartSpectraPackageManifest.json not found at "
        "${_dae_manifest_path}. The example's Depends list is sourced from "
        "the SDK's own package manifest; a missing manifest means the SDK "
        "tarball is malformed or this is an older SDK that pre-dates the "
        "manifest-in-tarball contract. Update the SDK or escalate per R1 "
        "in the changeset's design.md.")
endif()
file(READ "${_dae_manifest_path}" _dae_manifest_json)
string(JSON _dae_debian_depends GET "${_dae_manifest_json}" debian_depends)
if(NOT _dae_debian_depends)
    message(FATAL_ERROR
        "debian-app-example: SmartSpectraPackageManifest.json at "
        "${_dae_manifest_path} reports an empty debian_depends field — "
        "this would produce a .deb that installs but fails to load "
        "libsmartspectra.so on a stock container. Refusing to package.")
endif()
set(CPACK_DEBIAN_PACKAGE_DEPENDS "${_dae_debian_depends}")

# Maintainer scripts: postinst writes /etc/ld.so.conf.d/debian-app-example.conf
# and runs ldconfig so libsmartspectra.so's DT_NEEDED libopencv_*.so.411 chain
# resolves at load time. postrm removes the conf and re-runs ldconfig.
configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/cmake/postinst.in"
    "${CMAKE_CURRENT_BINARY_DIR}/postinst"
    @ONLY
    FILE_PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE
                     GROUP_READ GROUP_EXECUTE
                     WORLD_READ WORLD_EXECUTE
)
configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/cmake/postrm.in"
    "${CMAKE_CURRENT_BINARY_DIR}/postrm"
    @ONLY
    FILE_PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE
                     GROUP_READ GROUP_EXECUTE
                     WORLD_READ WORLD_EXECUTE
)
set(CPACK_DEBIAN_PACKAGE_CONTROL_EXTRA
    "${CMAKE_CURRENT_BINARY_DIR}/postinst"
    "${CMAKE_CURRENT_BINARY_DIR}/postrm"
)

include(CPack)

# Group 4 (Depends-from-manifest) extends this file in the next commit.
