#!/usr/bin/env bash
set -euo pipefail

FIX=0
PRINT_XCODE_SETTINGS=0
SDK_ROOT="${SMARTSPECTRA_SDK_ROOT:-}"

usage() {
  cat >&2 <<EOF
Usage: $0 [--fix] [--print-xcode-settings] [--sdk-root "$(brew --prefix)"]
       SMARTSPECTRA_SDK_ROOT="$(brew --prefix)" $0 [--fix] [--print-xcode-settings]
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --fix)
      FIX=1
      shift
      ;;
    --sdk-root)
      SDK_ROOT="${2:-}"
      if [[ -z "$SDK_ROOT" ]]; then
        echo "--sdk-root requires a path" >&2
        exit 2
      fi
      shift 2
      ;;
    --print-xcode-settings)
      PRINT_XCODE_SETTINGS=1
      shift
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

if [[ -z "$SDK_ROOT" ]]; then
  if command -v brew >/dev/null 2>&1; then
    SDK_ROOT="$(brew --prefix)"
  else
    SDK_ROOT="/opt/homebrew"
  fi
fi

if [[ -z "${HOMEBREW_PREFIX:-}" ]] && command -v brew >/dev/null 2>&1; then
  HOMEBREW_PREFIX="$(brew --prefix)"
fi
HOMEBREW_PREFIX="${HOMEBREW_PREFIX:-/opt/homebrew}"
EXPECTED_GRAPH="$HOMEBREW_PREFIX/share/smartspectra/graph"
SDK_GRAPH="$SDK_ROOT/share/smartspectra/graph"
ICD_JSON="$HOMEBREW_PREFIX/etc/vulkan/icd.d/MoltenVK_icd.json"

info() { printf '[info] %s\n' "$*"; }
warn() { printf '[warn] %s\n' "$*" >&2; }
fail() { printf '[fail] %s\n' "$*" >&2; exit 1; }

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "Missing required command: $1"
  fi
}

brew_install_if_missing() {
  local formula="$1"
  if brew list --versions "$formula" >/dev/null 2>&1; then
    info "Homebrew package present: $formula"
    return
  fi

  if [[ "$FIX" -eq 1 ]]; then
    info "Installing Homebrew package: $formula"
    brew install "$formula"
  else
    warn "Missing Homebrew package: $formula (run $0 --fix or brew install $formula)"
  fi
}

need_cmd sw_vers
need_cmd xcodebuild

PRODUCT_VERSION="$(sw_vers -productVersion)"
info "macOS: $PRODUCT_VERSION"

if [[ ! -d "$SDK_ROOT" ]]; then
  fail "SmartSpectra SDK root not found: $SDK_ROOT"
fi

if [[ ! -f "$SDK_ROOT/lib/libsmartspectra.dylib" ]]; then
  fail "libsmartspectra.dylib not found under SDK root: $SDK_ROOT"
fi
if [[ ! -f "$SDK_ROOT/include/smartspectra/smartspectra.h" ]]; then
  fail "smartspectra.h not found under SDK root: $SDK_ROOT"
fi
if [[ ! -d "$SDK_ROOT/include/smartspectra/interface" ]]; then
  fail "SmartSpectra interface headers not found under SDK root: $SDK_ROOT"
fi
info "SmartSpectra SDK root: $SDK_ROOT"

if [[ "$PRINT_XCODE_SETTINGS" -eq 1 ]]; then
  cat <<EOF

Xcode Build Settings:
SMARTSPECTRA_SDK_ROOT = $SDK_ROOT
HOMEBREW_PREFIX = $HOMEBREW_PREFIX

Xcode Signing & Capabilities:
Team = <your Apple Development team>
Bundle Identifier = <your unique bundle identifier>

Command-line build:
SMARTSPECTRA_DEVELOPMENT_TEAM=<team-id> SMARTSPECTRA_SDK_ROOT="$SDK_ROOT" task cpp:macos-swiftui-example
EOF
fi

for file in \
  "$SDK_GRAPH/metrics_cpu_continuous_rest.binarypb" \
  "$SDK_GRAPH/models/face_landmark_with_attention.tflite" \
  "$SDK_GRAPH/models/pose_detection.tflite" \
  "$SDK_GRAPH/models/pose_landmark_lite.tflite"; do
  [[ -f "$file" ]] || fail "Required SDK file is missing: $file"
done
info "SmartSpectra graph and model assets are present"

if [[ -e "$EXPECTED_GRAPH" ]]; then
  if [[ -d "$EXPECTED_GRAPH" ]] &&
     [[ "$(cd "$EXPECTED_GRAPH" && pwd -P)" == "$(cd "$SDK_GRAPH" && pwd -P)" ]]; then
    info "SmartSpectra graph path is linked: $EXPECTED_GRAPH"
  else
    warn "SmartSpectra graph path exists but does not point at $SDK_GRAPH: $EXPECTED_GRAPH"
  fi
elif [[ -L "$EXPECTED_GRAPH" ]]; then
  warn "SmartSpectra graph path is a broken symlink: $EXPECTED_GRAPH"
elif [[ "$FIX" -eq 1 ]]; then
  info "Linking SDK graph assets into expected SmartSpectra path"
  mkdir -p "$(dirname "$EXPECTED_GRAPH")"
  ln -s "$SDK_GRAPH" "$EXPECTED_GRAPH"
else
  warn "Missing expected SmartSpectra graph path: $EXPECTED_GRAPH (run $0 --fix)"
fi

if command -v brew >/dev/null 2>&1; then
  brew_install_if_missing opencv
  brew_install_if_missing vulkan-loader
  brew_install_if_missing molten-vk
else
  warn "Homebrew not found; install opencv, vulkan-loader, and molten-vk another way"
fi

if [[ -f "$ICD_JSON" ]]; then
  info "MoltenVK ICD manifest: $ICD_JSON"
else
  warn "MoltenVK ICD manifest not found: $ICD_JSON"
fi

IDENTITIES="$(security find-identity -v -p codesigning 2>/dev/null || true)"
if grep -q 'valid identities found' <<<"$IDENTITIES" && ! grep -q ' 0 valid identities found' <<<"$IDENTITIES"; then
  info "Code-signing identity is available"
else
  warn "No valid command-line code-signing identity found. Xcode may still provision during build."
fi

info "Requirement check complete"
