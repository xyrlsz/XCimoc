<#
.SYNOPSIS
    XCimoc Comic Source Tester - PowerShell Script
.DESCRIPTION
    Build and run the Java test module, generate HTML/JSON reports.
.PARAMETER Source
    Source type IDs to test, comma-separated (default: all)
.PARAMETER Verbose
    Show verbose output
.PARAMETER OutputDir
    Report output directory (default: ./reports)
.PARAMETER NoOpen
    Do not auto-open the report after completion
.EXAMPLE
    .\run-test.ps1
    .\run-test.ps1 -Source 0,5,26
    .\run-test.ps1 -Verbose
    .\run-test.ps1 -OutputDir .\my-reports -NoOpen
#>

param(
    [string]$Source = "",
    [switch]$Verbose = $false,
    [string]$OutputDir = "",
    [switch]$NoOpen = $false
)

$ErrorActionPreference = "Continue"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

# ---------- Build arguments ----------
$gradleArgs = @(":sourcetester:run")

$appArgs = @()
if ($Source) {
    $appArgs += "--source"
    $appArgs += $Source
}
if ($Verbose) {
    $appArgs += "--verbose"
}
if ($OutputDir) {
    $appArgs += "--output-dir"
    $appArgs += $OutputDir
}

# 强制使用绝对路径输出报告，确保报告写到项目根目录的 reports/
$reportDirName = if ($OutputDir) { $OutputDir } else { "reports" }
$reportAbsolute = Join-Path $ProjectRoot $reportDirName
$appArgs += "--output-dir"
$appArgs += $reportAbsolute

if ($appArgs.Count -gt 0) {
    $joined = $appArgs -join " "
    $gradleArgs += "--args=`"$joined`""
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  XCimoc Comic Source Tester" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ---------- Check Java ----------
$javaTest = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaTest) {
    Write-Host "  [ERR] Java not found. Please install JDK 17+." -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] Java detected: $($javaTest.Source)" -ForegroundColor Green

# ---------- Check Gradle ----------
if (-not (Test-Path "gradlew")) {
    Write-Host "  [ERR] gradlew not found. Run this script from the project root." -ForegroundColor Red
    exit 1
}

# ---------- Run tests ----------
Write-Host "  [RUN] .\gradlew $gradleArgs" -ForegroundColor Gray
Write-Host ""
.\gradlew @gradleArgs 2>&1 | Out-Host
$exitCode = $LASTEXITCODE

# ---------- Locate report ----------
$reportPath = Join-Path $reportAbsolute "report.html"

if (Test-Path $reportPath) {
    # 同步到 docs/（GitHub Pages 使用）
    $docsDir = Join-Path $ProjectRoot "docs"
    if (Test-Path $docsDir) {
        Copy-Item $reportPath (Join-Path $docsDir "report.html") -Force
        Write-Host "  [SYNC] 报告已同步到 docs/report.html" -ForegroundColor Gray
    }
}

# ---------- Open report ----------
if ((Test-Path $reportPath) -and -not $NoOpen) {
    Write-Host ""
    Write-Host "  [OPEN] $reportPath" -ForegroundColor Green
    Start-Process $reportPath
}

Write-Host ""
if ($exitCode -eq 0) {
    Write-Host "  [DONE] Tests completed successfully." -ForegroundColor Green
} else {
    Write-Host "  [DONE] Tests completed (some sources may have failed, see report)." -ForegroundColor Yellow
}
Write-Host ""
exit $exitCode
