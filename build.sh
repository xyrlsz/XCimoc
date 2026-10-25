#!/usr/bin/env bash
#
# XCimoc APK 一键构建脚本
#
# 用法：
#   ./build.sh            # 默认 release
#   ./build.sh debug      # 编译 debug
#
set -euo pipefail

cd "$(dirname "$0")"

BUILD_TYPE="${1:-release}"

case "$BUILD_TYPE" in
    release|debug) ;;
    *) echo "用法: ./build.sh [release|debug]" >&2; exit 1 ;;
esac

# 任务名首字母大写：release -> assembleRelease / debug -> assembleDebug
TASK="assemble$(echo "${BUILD_TYPE:0:1}" | tr '[:lower:]' '[:upper:]')${BUILD_TYPE:1}"

echo ""
echo "=============================================="
echo " 编译 APK (:app:$TASK)"
echo "=============================================="
./gradlew ":app:$TASK"

echo ""
echo "=============================================="
echo " 构建完成 ✅  APK 输出目录："
echo "   full（含超分）: app/build/outputs/apk/full/${BUILD_TYPE}/"
echo "   lite（无超分）: app/build/outputs/apk/lite/${BUILD_TYPE}/"
echo "=============================================="
