# debian-app-example

A standalone SmartSpectra C++ SDK redistribution example. Demonstrates the
**validated Linux redistribution path**: take the published SmartSpectra Linux
SDK release tarball, build your own app against it via `find_package(SmartSpectra)`,
and ship a `.deb` that installs on stock Ubuntu without a Presage apt source.

See [Redistribute SmartSpectra on Linux](../../../docs/redistribute_smartspectra_on_linux.md)
for the full guide; the rest of this file is a quick reference for the example itself.

The `.deb` produced by this example:

- Installs entirely under `/opt/debian-app-example/{bin,lib,share}`
- Privately bundles `libsmartspectra.so` under `/opt/debian-app-example/lib/`.
- Sets `RPATH=$ORIGIN/../lib` on the binary so it finds the bundled lib
- Writes `/etc/ld.so.conf.d/debian-app-example.conf` from `postinst` so the
  loader finds `libsmartspectra.so` under `/opt/debian-app-example/lib`, which
  is not on the default search path (the SDK strips `DT_RUNPATH` from
  libsmartspectra.so, so this loader-cache entry is load-bearing)
- Declares its `Depends:` from the SDK's own
  `share/smartspectra/package/SmartSpectraPackageManifest.json` so the
  apt-resolved tail (FFmpeg / TLS on Noble, OpenGL / Vulkan baseline) stays
  in sync with what the SDK was built against
- Does **not** depend on `libsmartspectra-dev` — the end-user needs no
  Presage apt source

## Build, package, install (Debian-family host with the SDK extracted)

```bash
# 1. Extract a SmartSpectra Linux SDK release tarball
#    Use the codename-qualified asset name:
#    - Ubuntu 22.04 / Mint 21 / Debian 11+: linux-jammy-amd64
#    - Ubuntu 24.04 / Mint 22: linux-noble-amd64
mkdir -p /tmp/sdk
tar -xzf smartspectra-sdk-<version>-linux-<codename>-amd64.tar.gz -C /tmp/sdk

# 2. Build the example against it (out-of-tree)
cmake -S . -B /tmp/build \
      -DCMAKE_PREFIX_PATH=/tmp/sdk \
      -DCMAKE_BUILD_TYPE=Release
cmake --build /tmp/build -j$(nproc)

# 3. Produce the .deb
cd /tmp/build
cpack -G DEB
# Produces debian-app-example_<version>-<codename>1_amd64.deb

# 4. Install on the host (or ship to an end-user)
sudo apt install -y ./debian-app-example_*.deb
```

## Run

```bash
debian-app-example \
    --api_key=YOUR_KEY \
    --input_video_path=/path/to/vitals.mp4 \
    --output_json=/tmp/metrics.json
```

The binary writes a JSON document containing the final SmartSpectra metrics
(including `pulse.rate` and `breathing.rate`) to `--output_json`.

### No-argument launch

```bash
debian-app-example </dev/null
# rc=1 (controlled exit): "Usage: ..." / missing required argument.
```

A useful smoke test after installing the `.deb`: if the dynamic loader
resolves the bundled `libsmartspectra.so` via the postinst-registered
ldconfig entry, the binary launches and prints its usage banner before
exiting with rc=1. An rc of `127` instead means the loader could not
resolve `libsmartspectra.so` — most likely the ldconfig fragment was not
picked up; re-running `sudo ldconfig` after install resolves that.

## Files installed by the `.deb`

| Path | Origin |
| --- | --- |
| `/opt/debian-app-example/bin/debian-app-example` | This example |
| `/opt/debian-app-example/lib/libsmartspectra.so` | Bundled from SDK tarball |
| `/opt/debian-app-example/lib/smartspectra_manifest.json` | Generated at install |
| `/opt/debian-app-example/share/smartspectra/...` (models) | Bundled from SDK tarball |
| `/etc/ld.so.conf.d/debian-app-example.conf` | Written by `postinst` |

`postrm` removes the `ld.so.conf.d` fragment and re-runs `ldconfig` on
`remove` / `purge`.

## Distro coverage

The SDK ships per-codename tarballs (`linux-jammy-*`, `linux-noble-*`). This
example emits per-codename `.deb` revision suffixes (`jammy1`, `noble1`), so
a `.deb` built against a jammy SDK installs on Ubuntu 22.04 / Mint 21 /
Debian 11+, and a `.deb` built against a noble SDK installs on Ubuntu 24.04 /
Mint 22. Cross-codename install (jammy `.deb` on noble host or vice versa) is
not supported — the bundled `libsmartspectra.so` is per-codename and the
Depends versions differ.

## License

See [LICENSE](LICENSE).
