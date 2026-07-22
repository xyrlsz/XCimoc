@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ========================================
REM  XCimoc 漫画源测试 — Windows CMD 脚本
REM ========================================

set SOURCE=
set VERBOSE=
set OUTPUT_DIR=
set NO_OPEN=

:parse
if "%~1"=="" goto :run
if /i "%~1"=="--source" set "SOURCE=%~2" & shift & shift & goto :parse
if /i "%~1"=="-s"       set "SOURCE=%~2" & shift & shift & goto :parse
if /i "%~1"=="--verbose" set VERBOSE=--verbose & shift & goto :parse
if /i "%~1"=="-v"       set VERBOSE=--verbose & shift & goto :parse
if /i "%~1"=="--output-dir" set "OUTPUT_DIR=%~2" & shift & shift & goto :parse
if /i "%~1"=="-o"       set "OUTPUT_DIR=%~2" & shift & shift & goto :parse
if /i "%~1"=="--no-open" set NO_OPEN=1 & shift & goto :parse
shift & goto :parse

:run
echo ========================================
echo   XCimoc 漫画源测试
echo ========================================
echo.

REM 检查 Java
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo   [错误] 未找到 Java，请安装 JDK 17+
    pause
    exit /b 1
)

for /f "tokens=*" %%i in ('java -version 2^>^&1') do (
    echo   Java: %%i | findstr "version"
    if not errorlevel 1 goto :java_ok
)
:java_ok

REM 检查 gradlew
if not exist gradlew (
    echo   [错误] 未找到 gradlew，请确保在项目根目录运行
    pause
    exit /b 1
)

REM 构建参数
set GRADLE_ARGS=:sourcetester:run
set APP_ARGS=

if defined SOURCE set APP_ARGS=!APP_ARGS! --source !SOURCE!
if defined VERBOSE set APP_ARGS=!APP_ARGS! --verbose
REM 强制使用绝对路径输出报告
if not defined OUTPUT_DIR set "OUTPUT_DIR=%CD%\reports"
set APP_ARGS=!APP_ARGS! --output-dir !OUTPUT_DIR!

if defined APP_ARGS (
    set GRADLE_ARGS=!GRADLE_ARGS! --args="!APP_ARGS!"
)

echo   Gradle: .\gradlew !GRADLE_ARGS!
echo.

call .\gradlew %GRADLE_ARGS%

set EXIT_CODE=%ERRORLEVEL%

REM 同步到 docs/ 并打开报告
set "RPATH=%OUTPUT_DIR%\report.html"
if exist "%RPATH%" (
    if exist "%CD%\docs" (
        copy /y "%RPATH%" "%CD%\docs\report.html" >nul
        echo   [SYNC] 报告已同步到 docs\report.html
    )
    if "%NO_OPEN%"=="" (
        echo.
        echo   [OPEN] %RPATH%
        start "" "%RPATH%"
    )
)

echo.
if %EXIT_CODE% EQU 0 (
    echo   ✅ 测试完成
) else (
    echo   ⚠️  测试完成（部分源可能失败，详见报告）
)
echo.
pause
exit /b %EXIT_CODE%
