#!/usr/bin/env bash
#
# data_server build script.
# - Default: builds ONLY for the current host (GOOS/GOARCH detected via `go env`).
# - Optional: also builds musl (fully static) Linux binaries via --musl.
# - CGO is always enabled (gorm.io/driver/sqlite requires mattn/go-sqlite3 via CGO).
# - Regenerates gorm.io/gen query code BEFORE every build.
#
# Usage:
#   ./build.sh                     # regenerate query + native build -> ./dist/
#   ./build.sh --musl              # native build + linux musl(static) build for host arch
#   ./build.sh --musl-only         # skip native build, only musl(static) build
#   ./build.sh --musl --musl-arch all   # also cross-build linux/amd64 + linux/arm64 musl
#   ./build.sh -out ./artifacts   # custom output directory
#   ./build.sh -config ./cfg.yaml # DB config passed to code-generator
#   ./build.sh -n                 # dry-run: print commands only
#   ./build.sh -v                 # verbose (adds -x to go build)
#   ./build.sh -h                 # help
#
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

OUT_DIR="$SCRIPT_DIR/dist"
CONFIG_FILE=""
VERBOSE=0
DRY_RUN=0
WITH_MUSL=0
MUSL_ONLY=0
MUSL_ARCHS=""

usage() {
  cat <<'EOF'
Usage: ./build.sh [options]

Builds data_server for the CURRENT host platform (native build) by default.
Uses CGO unconditionally because gorm.io/driver/sqlite is backed by
mattn/go-sqlite3, which requires a local C compiler.

Optionally also builds musl-based, fully static Linux binaries (run on
Alpine / any distro, no glibc dependency).

The script ALWAYS regenerates query package via `go run ./gen` first.
Code-generation step runs with CGO_ENABLED=0 (pure-Go sqlite driver,
no gcc needed for gen).

Options:
  -out <dir>       Output directory (default: ./dist)
  -config <file>   Pass this file to code-generator via --gen-config
  --musl           Also build musl (static) Linux binary -> xcimoc-data-server-linux-<arch>-musl
  --musl-only      Only build the musl binary (skip the native build)
  --musl-arch <a>  musl target arch: amd64 | arm64 | all (default: host arch)
  -n | -dry-run    Print commands, do not execute
  -v               Verbose (passes -x to go build)
  -h | --help      Show this help

Prerequisites:
  * Go >= 1.20
  * A local C compiler (gcc / clang) in PATH for the native build:
      - Debian/Ubuntu:  sudo apt-get install gcc
      - macOS:          xcode-select --install
      - WSL:            sudo apt-get install gcc
      - Windows MSYS2:  pacman -S --needed mingw-w64-ucrt-x86_64-gcc
  * For --musl, a musl-capable C compiler, one of:
      - musl-gcc           (Debian/Ubuntu: sudo apt-get install musl-tools)
      - x86_64-linux-musl-gcc / aarch64-linux-musl-gcc (musl.cc cross toolchains)
      - zig (any platform, used as `zig cc -target <triple>-linux-musl`):
          Debian/Ubuntu: sudo snap install zig --classic --beta
          macOS:         brew install zig
          Windows:       scoop install zig   (then use build.ps1 -Musl)
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -out)        OUT_DIR="$2"; shift 2 ;;
    -config)     CONFIG_FILE="$2"; shift 2 ;;
    --musl|-musl)
                 WITH_MUSL=1; shift ;;
    --musl-only)
                 WITH_MUSL=1; MUSL_ONLY=1; shift ;;
    --musl-arch)
                 if [[ $# -lt 2 ]]; then
                   echo "ERROR: --musl-arch requires a value (amd64 | arm64 | all)" >&2; exit 1
                 fi
                 MUSL_ARCHS="$2"; WITH_MUSL=1; shift 2 ;;
    -n|-dry-run) DRY_RUN=1; shift ;;
    -v)          VERBOSE=1; shift ;;
    -h|--help)   usage; exit 0 ;;
    *)           echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
done

# ---- helpers ---------------------------------------------------------
run() {
  if [[ $DRY_RUN -eq 1 ]]; then
    echo "RUN: $*"
  else
    echo "RUN: $*"
    "$@"
  fi
}

require_cmd() {
  local cmd="$1"; local hint="$2"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "ERROR: missing required command: $cmd" >&2
    [[ -n "${hint:-}" ]] && echo "       hint: $hint" >&2
    exit 2
  fi
}

# Resolve a musl-capable C compiler for linux/<arch>.
# Prints the CC value on success (may contain arguments, e.g. "zig cc -target ...").
resolve_musl_cc() {
  local arch="$1" name triple
  local names=()
  case "$arch" in
    amd64) names=(musl-gcc x86_64-linux-musl-gcc) ;;
    arm64) names=(aarch64-linux-musl-gcc) ;;
  esac
  for name in "${names[@]}"; do
    if command -v "$name" >/dev/null 2>&1; then
      printf '%s' "$name"; return 0
    fi
  done
  if command -v zig >/dev/null 2>&1; then
    triple="x86_64"
    [[ "$arch" == "arm64" ]] && triple="aarch64"
    printf 'zig cc -target %s-linux-musl' "$triple"
    return 0
  fi
  return 1
}

# Normalize --musl-arch into a space-separated list of GOARCH values.
normalize_musl_archs() {
  local raw="$1" a out=""
  for a in $raw; do
    case "$a" in
      amd64|x86_64)  out="$out amd64" ;;
      arm64|aarch64) out="$out arm64" ;;
      *) echo "ERROR: unsupported --musl-arch: $a (use amd64, arm64 or all)" >&2; exit 1 ;;
    esac
  done
  printf '%s' "$out"
}

# ---- main ------------------------------------------------------------
require_cmd go "Install Go >=1.20: https://go.dev/dl/"
if [[ $MUSL_ONLY -eq 0 ]]; then
  require_cmd gcc "Install a host C compiler (gcc/clang) for CGO."
fi

mkdir -p "$OUT_DIR"

BUILD_FLAGS=(-ldflags "-s -w")
if [[ $VERBOSE -eq 1 ]]; then BUILD_FLAGS+=(-x); fi

# musl 版本：外部链接器强制静态链接，并用 netgo/osusergo 去掉 glibc/NSS 依赖，
# 这样产物在 Alpine 或任意发行版上都能直接跑（零动态库依赖）。
MUSL_BUILD_FLAGS=(-ldflags "-s -w -extldflags=-static" -tags "netgo osusergo")
if [[ $VERBOSE -eq 1 ]]; then MUSL_BUILD_FLAGS+=(-x); fi

host_os="$(go env GOOS)"
host_arch="$(go env GOARCH)"
ext=""
[[ "$host_os" == "windows" ]] && ext=".exe"
OUT_FILE="$OUT_DIR/xcimoc-data-server-${host_os}-${host_arch}${ext}"

# musl 目标架构：未指定时跟随宿主架构
MUSL_ARCH_LIST=""
if [[ $WITH_MUSL -eq 1 ]]; then
  if [[ -z "$MUSL_ARCHS" ]]; then
    MUSL_ARCHS="$host_arch"
  elif [[ "$MUSL_ARCHS" == "all" ]]; then
    MUSL_ARCHS="amd64 arm64"
  fi
  MUSL_ARCH_LIST="$(normalize_musl_archs "$MUSL_ARCHS")"
fi

# ---- Step 0: regenerate query package (每次编译都重新生成) -------------
# 说明：
#   - CGO_ENABLED=0：gen 内部用 github.com/glebarez/sqlite（纯 Go，modernc），
#     不需要 gcc，也避免了生成阶段和 CGO 驱动冲突。
#   - --gen-config 是 gen/gen.go 自己解析的参数；之所以不叫 --config，
#     是因为 config.Load() 内部会声明 --config flag，两者冲突时 gen 自定义
#     参数解析会先把 --gen-config 摘出来再把剩余参数交给 config.Load。
# ---------------------------------------------------------------------
GEN_ARGS=(run ./gen)
if [[ -n "${CONFIG_FILE:-}" ]]; then
  GEN_ARGS+=("--gen-config=${CONFIG_FILE}")
fi
echo "==> Regenerate gorm.io/gen query package (CGO=0, pure-Go sqlite driver)"
if [[ -z "${CONFIG_FILE:-}" ]]; then
  echo "    hint: if you need a specific DB config, pass -config ./config.yaml"
fi
CGO_ENABLED=0 run go "${GEN_ARGS[@]}"

if [[ $MUSL_ONLY -eq 0 ]]; then
  echo "==> Build native ($host_os/$host_arch)"
  CGO_ENABLED=1 run go build "${BUILD_FLAGS[@]}" -o "$OUT_FILE" .
fi

# ---- Step 2: musl（静态）构建 -----------------------------------------
if [[ $WITH_MUSL -eq 1 ]]; then
  for arch in $MUSL_ARCH_LIST; do
    if ! musl_cc="$(resolve_musl_cc "$arch")"; then
      echo "ERROR: no musl-capable C compiler found for linux/$arch" >&2
      echo "       Install one of: musl-gcc (apt-get install musl-tools)," >&2
      echo "       x86_64-linux-musl-gcc / aarch64-linux-musl-gcc (musl.cc)," >&2
      echo "       or zig (Snap/Brew; used as 'zig cc -target <triple>-linux-musl')." >&2
      exit 2
    fi
    musl_out="$OUT_DIR/xcimoc-data-server-linux-${arch}-musl"
    echo "==> Build musl static (linux/$arch) with CC=$musl_cc"
    # 用 env 前缀而不是内联赋值，这样 -n(dry-run) 打印出的命令行也带上完整环境变量
    run env CGO_ENABLED=1 "CC=$musl_cc" GOOS=linux GOARCH="$arch" \
      go build "${MUSL_BUILD_FLAGS[@]}" -o "$musl_out" .
    if [[ $DRY_RUN -eq 0 ]] && command -v file >/dev/null 2>&1; then
      file "$musl_out" || true
    fi
  done
fi

if [[ $DRY_RUN -eq 0 ]]; then
  # 输出目录与脚本目录相同时（例如 -out .），源与目标是同一文件，
  # 跳过复制，避免 cp 报 "are the same file"。
  if [[ "$SCRIPT_DIR/config.example.yaml" -ef "$OUT_DIR/config.example.yaml" ]]; then
    echo "config.example.yaml already in output dir, skip copy"
  else
    cp -f "$SCRIPT_DIR/config.example.yaml" "$OUT_DIR/"
  fi
  echo ""
  echo "Build succeeded. Binaries in: $OUT_DIR"
  ls -lh "$OUT_DIR"
fi
