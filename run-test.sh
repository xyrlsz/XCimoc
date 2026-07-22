#!/usr/bin/env bash
# ========================================
#  XCimoc 漫画源测试 — Bash/Linux 脚本
# ========================================
set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[0;90m'
NC='\033[0m'

# 默认值
SOURCE=""
VERBOSE=""
OUTPUT_DIR=""
NO_OPEN=false

# 解析参数
while [[ $# -gt 0 ]]; do
    case "$1" in
        --source|-s)       SOURCE="$2";       shift 2 ;;
        --verbose|-v)      VERBOSE="--verbose"; shift ;;
        --output-dir|-o)   OUTPUT_DIR="$2";   shift 2 ;;
        --no-open)         NO_OPEN=true;       shift ;;
        *)                 echo "未知参数: $1"; exit 1 ;;
    esac
done

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  XCimoc 漫画源测试${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

# 检测 Java
if command -v java &>/dev/null; then
    JAVA_VER=$(java -version 2>&1 | head -1)
    echo -e "  ${GREEN}Java: $JAVA_VER${NC}"
else
    echo -e "  ${RED}[错误] 未找到 Java，请安装 JDK 17+${NC}"
    exit 1
fi

# 检测 gradlew
if [[ ! -f "gradlew" ]]; then
    echo -e "  ${RED}[错误] 未找到 gradlew，请确保在项目根目录运行${NC}"
    exit 1
fi

chmod +x gradlew

# 构建参数 — 强制使用绝对路径输出报告
GRADLE_ARGS=":sourcetester:run"
APP_ARGS=""

[[ -n "$SOURCE" ]]     && APP_ARGS="$APP_ARGS --source $SOURCE"
[[ -n "$VERBOSE" ]]    && APP_ARGS="$APP_ARGS --verbose"
[[ -z "$OUTPUT_DIR" ]] && OUTPUT_DIR="$PROJECT_ROOT/reports"
APP_ARGS="$APP_ARGS --output-dir $OUTPUT_DIR"

if [[ -n "$APP_ARGS" ]]; then
    GRADLE_ARGS="$GRADLE_ARGS --args=\"$APP_ARGS\""
fi

echo -e "  ${GRAY}Gradle: ./gradlew $GRADLE_ARGS${NC}"
echo ""

# 运行测试
set +e
eval ./gradlew $GRADLE_ARGS
EXIT_CODE=$?
set -e

# 同步报告到 docs/（GitHub Pages 使用）
REPORT_PATH="$OUTPUT_DIR/report.html"
if [[ -f "$REPORT_PATH" ]]; then
    DOCS_DIR="$PROJECT_ROOT/docs"
    if [[ -d "$DOCS_DIR" ]]; then
        cp "$REPORT_PATH" "$DOCS_DIR/report.html"
        echo -e "  ${GRAY}[SYNC] 报告已同步到 docs/report.html${NC}"
    fi
fi

# 打开报告
if [[ -f "$REPORT_PATH" && "$NO_OPEN" == false ]]; then
    echo ""
    echo -e "  ${GREEN}[OPEN] $REPORT_PATH${NC}"
    case "$(uname -s)" in
        Linux*)   xdg-open "$REPORT_PATH" 2>/dev/null || true ;;
        Darwin*)  open "$REPORT_PATH" 2>/dev/null || true ;;
    esac
fi

echo ""
if [[ $EXIT_CODE -eq 0 ]]; then
    echo -e "  ${GREEN}✅ 测试完成${NC}"
else
    echo -e "  ${YELLOW}⚠️  测试完成（部分源可能失败，详见报告）${NC}"
fi
echo ""

exit $EXIT_CODE
