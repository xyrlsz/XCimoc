<#
.SYNOPSIS
  data_server build script.
  - Default: builds ONLY for the current host (GOOS/GOARCH detected via `go env`).
  - With -Musl: additionally cross-builds fully static musl Linux binaries.
  - CGO is always enabled (gorm.io/driver/sqlite requires mattn/go-sqlite3 via CGO).
  - Regenerates gorm.io/gen query code BEFORE every build.

.DESCRIPTION
  Usage examples:
    .\build.ps1                       # regenerate query + native build -> .\dist\
    .\build.ps1 -Musl                 # native build + linux/amd64 musl(static) build
    .\build.ps1 -Musl -MuslArch all   # native build + linux/amd64 + linux/arm64 musl
    .\build.ps1 -MuslOnly             # only musl(static) build, skip native build
    .\build.ps1 -OutDir .\artifacts   # custom output directory
    .\build.ps1 -Config .\config.yaml # pick DB config used by code-generator
    .\build.ps1 -DryRun               # print commands, do not execute
    .\build.ps1 -BuildVerbose         # verbose (passes -x to go build)
    Get-Help .\build.ps1 -Full        # full help

  Musl builds need a musl-capable C compiler. On Windows the easiest option is
  Zig (scoop install zig / winget install zig.zig), which is used as
  `zig cc -target <triple>-linux-musl`. musl-gcc / *-linux-musl-gcc also work
  when present (e.g. inside WSL or MSYS2).
#>

[CmdletBinding()]
param(
  [string]$OutDir,
  [string]$Config,
  [switch]$DryRun,
  [Alias('VerboseBuild')]
  [switch]$BuildVerbose,
  [switch]$Musl,
  [switch]$MuslOnly,
  [string]$MuslArch = 'amd64'
)

$ErrorActionPreference = 'Stop'

# -----------------------------------------------------------------------------
# Defaults
# -----------------------------------------------------------------------------
if ([string]::IsNullOrEmpty($OutDir)) {
  $OutDir = Join-Path $PSScriptRoot 'dist'
}

# -----------------------------------------------------------------------------
# Helpers
# -----------------------------------------------------------------------------
function Write-Step {
  param([string]$Msg)
  Write-Host ('==> ' + $Msg) -ForegroundColor Cyan
}

function Write-Hint {
  param([string]$Msg)
  Write-Host ('    hint: ' + $Msg) -ForegroundColor DarkGray
}

<#
.SYNOPSIS
  Invokes an external command with a temporary environment override.
  Uses splatting so an arg like "-ldflags=-s -w" is passed as a SINGLE string.
#>
function Invoke-Native {
  param(
    [Parameter(Mandatory=$true)][string]$FilePath,
    [string[]]$Arguments,
    [hashtable]$Env
  )

  if (-not $Arguments) { $Arguments = @() }
  if (-not $Env)       { $Env = @{} }

  # DryRun: pretty-print what would run, then return.
  if ($DryRun) {
    $envList = @()
    foreach ($k in $Env.Keys) {
      $envList += ('$env:' + $k + "='" + $Env[$k] + "'")
    }
    $argList = @()
    foreach ($a in $Arguments) {
      if ($a -match '\s') { $argList += ("'" + $a + "'") }
      else                { $argList += $a }
    }
    $envStr = ''
    if ($envList.Count -gt 0) { $envStr = '(' + ($envList -join '; ') + ')' }
    $argStr = ($argList -join ' ').TrimEnd()
    $line = ('RUN ' + $envStr + ': ' + $FilePath + ' ' + $argStr).TrimEnd()
    Write-Host $line
    return 0
  }

  $printArg = [bool]$BuildVerbose -or ($VerbosePreference -eq 'Continue')
  if ($printArg) {
    $argList = @()
    foreach ($a in $Arguments) {
      if ($a -match '\s') { $argList += ("'" + $a + "'") }
      else                { $argList += $a }
    }
    $argStr = ($argList -join ' ').TrimEnd()
    Write-Host ('RUN: ' + $FilePath + ' ' + $argStr).TrimEnd()
  }

  $saved = @{}
  foreach ($k in $Env.Keys) {
    $saved[$k] = [Environment]::GetEnvironmentVariable($k, 'Process')
    [Environment]::SetEnvironmentVariable($k, $Env[$k], 'Process')
  }
  try {
    # 用 Out-Host 把命令输出直接写到控制台（用户可见），
    # 避免外部命令的 stdout 混入 PowerShell 函数返回值 —— 否则
    # 像 `go run ./gen` 这种"成功时也打印日志"的命令会让 $e 变成
    # 输出文本数组而非退出码，导致后续 `$e -ne 0` 误判失败。
    if ($Arguments.Count -eq 0) { & $FilePath | Out-Host }
    else                        { & $FilePath @Arguments | Out-Host }
    $exitCode = $LASTEXITCODE
  }
  finally {
    foreach ($k in $saved.Keys) {
      [Environment]::SetEnvironmentVariable($k, $saved[$k], 'Process')
    }
  }
  return $exitCode
}

function Require-Command {
  param(
    [string]$Cmd,
    [string]$Hint
  )
  $found = Get-Command $Cmd -ErrorAction SilentlyContinue
  if (-not $found) {
    Write-Host ('ERROR: missing required command: ' + $Cmd) -ForegroundColor Red
    if (-not [string]::IsNullOrEmpty($Hint)) { Write-Hint $Hint }
    exit 2
  }
  return $found.Source
}

<#
.SYNOPSIS
  Resolves a musl-capable C compiler for linux/<Arch>.
  Returns the CC value (may contain arguments, e.g. "zig cc -target ...")
  or $null when nothing usable is installed.
#>
function Resolve-MuslCc {
  param([string]$Arch)

  $names = @()
  switch ($Arch) {
    'amd64' { $names = @('musl-gcc', 'x86_64-linux-musl-gcc') }
    'arm64' { $names = @('aarch64-linux-musl-gcc') }
  }
  foreach ($n in $names) {
    $cmd = Get-Command $n -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
  }

  $zig = Get-Command 'zig' -ErrorAction SilentlyContinue
  if ($zig) {
    if ($Arch -eq 'arm64') { return 'zig cc -target aarch64-linux-musl' }
    return 'zig cc -target x86_64-linux-musl'
  }
  return $null
}

<#
.SYNOPSIS
  Normalizes -MuslArch into a GOARCH list (amd64 / arm64).
#>
function Resolve-MuslArchs {
  param([string]$Value)

  if ([string]::IsNullOrWhiteSpace($Value)) { $Value = 'amd64' }
  $result = @()
  foreach ($part in ($Value -split ',')) {
    $a = $part.Trim().ToLowerInvariant()
    if ($a -eq '') { continue }
    if ($a -eq 'all') { $result += @('amd64', 'arm64'); continue }
    switch ($a) {
      'amd64' { $result += 'amd64' }
      'x86_64' { $result += 'amd64' }
      'arm64' { $result += 'arm64' }
      'aarch64' { $result += 'arm64' }
      default { throw ('unsupported -MuslArch: ' + $part + ' (use amd64, arm64 or all)') }
    }
  }
  if ($result.Count -eq 0) { throw '-MuslArch resolved to an empty arch list' }
  return @($result | Select-Object -Unique)
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------
Set-Location -LiteralPath $PSScriptRoot
$null = Require-Command -Cmd 'go' -Hint 'Install Go 1.20+: https://go.dev/dl/ . Restart the shell so "go" is on PATH.'

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$hostGoos = & go env GOOS
if ($LASTEXITCODE -ne 0) { throw 'go env GOOS failed' }
$hostGoarch = & go env GOARCH
if ($LASTEXITCODE -ne 0) { throw 'go env GOARCH failed' }

# -----------------------------------------------------------------------------
# Step 0: Regenerate gorm.io/gen query code BEFORE every build.
# 代码生成阶段使用纯 Go 版 SQLite 驱动（glebarez/sqlite），无需 gcc，
# 所以这里 CGO_ENABLED=0；并且 gen 工具内部用 --gen-config 替代 --config，
# 避免和 config.Load() 的 flag 解析互相冲突。
# 注：先做代码生成，再检查 gcc——gcc 只在随后真正 CGO 编译时才需要。
# -----------------------------------------------------------------------------
$genArgs = @('run', './gen')
if (-not [string]::IsNullOrEmpty($Config)) {
  $genArgs += ('--gen-config=' + $Config)
}
Write-Step 'Regenerate gorm.io/gen query package (CGO=0, pure-Go sqlite driver)'
Write-Hint 'If you omit -Config, codegen uses a temporary SQLite file (no DB needed); pass -Config only to pre-check models against MySQL/PostgreSQL.'
$e = Invoke-Native -FilePath go -Arguments $genArgs -Env @{ CGO_ENABLED = '0' }
if ($e -ne 0) { throw ('code generation failed (exit=' + $e + ')') }

# gcc 只在真正 CGO 编译阶段需要；DryRun 或只生成代码的场景可以没有 gcc。
# -MuslOnly 时跳过原生构建，因此也不需要宿主 gcc。
if (-not $DryRun -and -not $MuslOnly) {
  $gccHint = @(
    'Install a local C compiler (MinGW GCC) for CGO.',
    '  MSYS2 UCRT64: winget install -e --id msys2.msys2',
    '    then in UCRT64 shell: pacman -S --needed mingw-w64-ucrt-x86_64-gcc go',
    '  OR Chocolatey: choco install -y mingw --prefer-binary',
    '  Finally add <MSYS2>\ucrt64\bin or <MinGW>\bin to PATH; verify with "gcc --version".'
  ) -join [Environment]::NewLine
  $null = Require-Command -Cmd 'gcc' -Hint $gccHint
}

$ext = ''
if ($hostGoos -eq 'windows') { $ext = '.exe' }
$binName = 'xcimoc-data-server-' + $hostGoos + '-' + $hostGoarch + $ext
$outFile = Join-Path $OutDir $binName

# Build the go argument array.
$allArgs = @('build', '-ldflags=-s -w')
if ($BuildVerbose -or ($VerbosePreference -eq 'Continue')) { $allArgs += '-x' }
$allArgs += '-o'
$allArgs += $outFile
$allArgs += '.'

if (-not $MuslOnly) {
  Write-Step ('Build native (' + $hostGoos + '/' + $hostGoarch + ')')
  $e = Invoke-Native -FilePath go -Arguments $allArgs -Env @{ CGO_ENABLED = '1' }
  if ($e -ne 0) { throw ('go build failed (exit=' + $e + ')') }
}

# -----------------------------------------------------------------------------
# Step 1: musl（静态）构建
# 说明：
#   - 目标固定为 linux/<arch>，CC 用 musl 工具链（musl-gcc / *-linux-musl-gcc）
#     或 zig cc -target <triple>-linux-musl。
#   - -extldflags=-static 让外部链接器产出完全静态的可执行文件；
#     netgo/osusergo 去掉 cgo 版的 DNS / 用户查询，避免依赖 NSS。
#   - 产物可直接跑在 Alpine 等 musl 发行版上，无需任何动态库。
# -----------------------------------------------------------------------------
if ($Musl -or $MuslOnly) {
  $muslArchs = Resolve-MuslArchs -Value $MuslArch
  foreach ($arch in $muslArchs) {
    $cc = Resolve-MuslCc -Arch $arch
    if ([string]::IsNullOrEmpty($cc)) {
      if ($DryRun) {
        $cc = 'musl-gcc'
        Write-Hint ('no musl C compiler detected for linux/' + $arch + '; dry-run uses placeholder CC')
      } else {
        $muslHint = @(
          ('No musl-capable C compiler found for linux/' + $arch + '.'),
          '  Recommended (works on Windows): scoop install zig   (or: winget install -e --id zig.zig)',
          '  Linux:  sudo apt-get install musl-tools            (provides musl-gcc)',
          '  Cross:  download musl.cc aarch64-linux-musl-cross / x86_64-linux-musl-cross toolchains',
          '  Then re-run: .\build.ps1 -Musl -MuslArch ' + $arch
        ) -join [Environment]::NewLine
        Write-Host ('ERROR: ' + $muslHint) -ForegroundColor Red
        exit 2
      }
    }

    $muslOut = Join-Path $OutDir ('xcimoc-data-server-linux-' + $arch + '-musl')
    $muslArgs = @('build', '-ldflags=-s -w -extldflags=-static', '-tags=netgo,osusergo')
    if ($BuildVerbose -or ($VerbosePreference -eq 'Continue')) { $muslArgs += '-x' }
    $muslArgs += @('-o', $muslOut, '.')

    Write-Step ('Build musl static (linux/' + $arch + ') with CC=' + $cc)
    $e = Invoke-Native -FilePath go -Arguments $muslArgs -Env @{
      CGO_ENABLED = '1'
      GOOS        = 'linux'
      GOARCH      = $arch
      CC          = $cc
    }
    if ($e -ne 0) { throw ('musl go build failed for linux/' + $arch + ' (exit=' + $e + ')') }
  }
}

# -----------------------------------------------------------------------------
# Finalize
# -----------------------------------------------------------------------------
if (-not $DryRun) {
  $cfgSrc = Join-Path $PSScriptRoot 'config.example.yaml'
  $cfgDst = Join-Path $OutDir     'config.example.yaml'
  # 输出目录与脚本目录相同时（例如 -out ./），源与目标是同一文件，
  # 跳过复制，避免 Copy-Item 报“无法使用项其自身覆盖该项”。
  # 注意：不要用 [System.IO.Path]::GetFullPath 比较——PowerShell 5.1 里
  # Set-Location 不更新 .NET 的 CurrentDirectory，相对路径会被解析错。
  # 用 Get-Item 跟随 PowerShell 的当前目录解析最可靠。
  $srcDirFull = (Get-Item -LiteralPath $PSScriptRoot).FullName
  $outDirFull = (Get-Item -LiteralPath $OutDir).FullName
  if ($srcDirFull -eq $outDirFull) {
    Write-Host 'config.example.yaml already in output dir, skip copy'
  } else {
    Copy-Item -Force -LiteralPath $cfgSrc -Destination $cfgDst
  }
  Write-Host ''
  Write-Host ('Build succeeded. Binaries in: ' + $OutDir) -ForegroundColor Green
  $sizeExpr = @{ Label='SizeKB'; Expression={ [math]::Round($_.Length / 1KB, 1) } }
  Get-ChildItem -LiteralPath $OutDir -File |
    Sort-Object Name |
    Format-Table Name, $sizeExpr -AutoSize
}
