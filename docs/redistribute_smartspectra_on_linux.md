---
title: Redistribute on Linux
description: Bundle the SmartSpectra Linux C++ SDK tarball into your own .deb so end users install your app on stock Ubuntu without a Presage apt source.
---

# Redistribute SmartSpectra on Linux

Validated path for a developer to take the published SmartSpectra Linux C++
SDK release tarball, build their own application against it, and ship a
self-contained `.deb` that installs on a stock Ubuntu / Mint / Debian host
without configuring a Presage apt source.

This is the **only** Linux redistribution shape currently supported. Other
shapes are not currently validated.

## When to use this path

You're a developer who:

- Ships a desktop app to enterprise Linux end-users
- Wants the end-user install to be one `sudo apt install ./your-app.deb` step
- Cannot ask end-users to configure a Presage apt source
- Targets Debian-family distros — currently Ubuntu 22.04 / 24.04 (Mint 21 / 22),
  with Debian 11+ as a documented equivalent. arm64 redistribution remains a
  follow-up

If your end-users run a non-Debian-family distro (Fedora, RHEL, openSUSE, Arch),
you're in territory we don't currently validate.

## The example app

[`cpp/samples/debian-app-example/`](https://github.com/Presage-Security/SmartSpectra/tree/main/cpp/samples/debian-app-example)
on the public mirror is a working reference implementation. It demonstrates
every choice this page documents and is a suitable starting point to copy
into your own repository when integrating the SDK into a redistributable
`.deb`.

Build it standalone against an extracted SDK release tarball:

```bash
# 1. Extract the published SDK release tarball
#    Use the codename-qualified asset name:
#    - Ubuntu 22.04 / Mint 21 / Debian 11+: linux-jammy-amd64
#    - Ubuntu 24.04 / Mint 22: linux-noble-amd64
mkdir -p /tmp/sdk
tar -xzf smartspectra-sdk-<version>-linux-<codename>-amd64.tar.gz -C /tmp/sdk

# 2. Configure + build the example out-of-tree
cmake -S cpp/samples/debian-app-example -B /tmp/dae-build \
      -DCMAKE_PREFIX_PATH=/tmp/sdk \
      -DCMAKE_BUILD_TYPE=Release
cmake --build /tmp/dae-build -j$(nproc)

# 3. Produce the .deb (per-codename revision suffix)
cd /tmp/dae-build
cpack -G DEB
# Produces e.g. debian-app-example_0.1.0-noble1_amd64.deb on Ubuntu 24.04
# or debian-app-example_0.1.0-jammy1_amd64.deb on Ubuntu 22.04 — the
# revision suffix tracks the build-host codename so the same .deb stream
# never serves a mismatched stock-archive Depends set.

# 4. Install on a clean Debian-family container
sudo apt install -y ./debian-app-example_*.deb
```

Read [`cpp/samples/debian-app-example/CMakeLists.txt`](https://github.com/Presage-Security/SmartSpectra/blob/main/cpp/samples/debian-app-example/CMakeLists.txt) end-to-end —
it's the canonical source for the install layout, CPack DEB config, Depends
manifest reading, and maintainer-script wiring.

## Install layout

The `.deb` installs entirely under `/opt/debian-app-example/` (an app-private prefix
per [FHS 3.0 §3.12](https://refspecs.linuxfoundation.org/FHS_3.0/fhs/ch03s12.html)).
Replace `debian-app-example` with your own app name when you adapt the example.

```text
/opt/<app-name>/
├── bin/
│   └── <app-name>                          (the executable)
├── lib/
│   ├── libsmartspectra.so                  (bundled from SDK tarball)
│   ├── smartspectra_manifest.json          (written at install time)
│   └── smartspectra/
│       └── libopencv_*.so.*                (bundled private OpenCV runtime)
└── share/
    └── smartspectra/
        └── graph/
            ├── models/                     (bundled from SDK tarball)
            └── ...
```

Plus one file outside the app's prefix, written by `postinst`:

```text
# /etc/ld.so.conf.d/<app-name>.conf
/opt/<app-name>/lib
/opt/<app-name>/lib/smartspectra
```

`postrm` removes that fragment on `remove` / `purge`.

## RPATH and per-app ldconfig

Two mechanisms together resolve the bundled-library chain when the end-user
launches the binary.

**RPATH on the binary.** The example binary is linked with
`INSTALL_RPATH=$ORIGIN/../lib` and `INSTALL_RPATH_USE_LINK_PATH=FALSE` so the
resulting `DT_RUNPATH` is exactly `$ORIGIN/../lib`. From
`/opt/<app-name>/bin/<app>` this walks to `/opt/<app-name>/lib/`, where
`libsmartspectra.so` lives. The binary's `DT_RUNPATH` resolves only that one
direct dependency — under `--enable-new-dtags` (the linker default since
~2008) `DT_RUNPATH` does **not** propagate to transitive dependencies, so it
plays no role in finding the bundled OpenCV runtime that
`libsmartspectra.so` itself depends on.

**ldconfig fragment for the SDK's transitive chain.** The SDK's shared
library has no embedded `DT_RUNPATH` / `DT_RPATH`, so its `DT_NEEDED`
entries for the bundled OpenCV runtime can be resolved only via the system
loader cache. To populate the cache, `postinst` writes:

```text
# /etc/ld.so.conf.d/<app-name>.conf
/opt/<app-name>/lib
/opt/<app-name>/lib/smartspectra
```

and runs `ldconfig`. The system cache then maps the bundled OpenCV runtime
to `/opt/<app-name>/lib/smartspectra/`, so the SDK's `DT_NEEDED` chain
resolves at load time. The redistributed layout keeps the OpenCV runtime in
the same `lib/smartspectra/` subdir the SDK uses when installed via apt, so
the installed tree mirrors the SDK's own install shape.

`postrm` removes the ldconfig fragment on `remove` / `purge` so the cache no
longer maps libraries to the now-removed prefix.

## Model-path resolution

At runtime the SDK locates its own shared-library directory and reads
`smartspectra_manifest.json` from that directory. The manifest contains a
single absolute path:

```json
{
  "resource_root_dir": "/opt/<app-name>/share/smartspectra"
}
```

The SDK joins `resource_root_dir` with the relative model paths it ships
internally, so model lookups land under
`/opt/<app-name>/share/smartspectra/...` at runtime.

The example's CMake writes this manifest at install time (not build time) so
the recorded prefix matches the actual install location, not the build prefix.

## Depends: from SmartSpectraPackageManifest.json

The example does **not** hard-code its `Depends:` list. Drift between the
SDK's own apt-resolved dependencies and the example's list would silently
produce a `.deb` that installs but fails to load the SDK on a stock
container.

Instead, the example reads
`<sdk>/share/smartspectra/package/SmartSpectraPackageManifest.json` at
CMake-configure time and feeds its `debian_depends` field straight into
`CPACK_DEBIAN_PACKAGE_DEPENDS`. The manifest is per-codename, so each
codename produces its own Depends set from the same example source.

A `FATAL_ERROR` guard in the example's CMake refuses to package if the
manifest is missing or its `debian_depends` field is empty.

The end-user therefore needs no Presage apt source — every transitive
dependency resolves from the codename's stock archive at
`apt install ./<app>.deb` time on both **jammy** and **noble**. arm64
remains a follow-up.

## See also

- Example source: [`cpp/samples/debian-app-example/`](https://github.com/Presage-Security/SmartSpectra/tree/main/cpp/samples/debian-app-example)
