param(
  [string]$Preset = "ninja-vcpkg-debug",
  [string]$EnvFile = "$PSScriptRoot\..\.env",
  [switch]$Fresh
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Info($msg) { Write-Host "[run] $msg" -ForegroundColor Cyan }
function Write-Warn($msg) { Write-Host "[run] $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "[run] $msg" -ForegroundColor Red }

$root = (Resolve-Path (Join-Path $PSScriptRoot ".."))
Push-Location $root
try {
  # Load .env if present
  if (Test-Path $EnvFile) {
    Write-Info "Loading env from $EnvFile"
    Get-Content -Raw $EnvFile | ForEach-Object {
      $_ -split "`n" | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#') -or $line.StartsWith(';')) { return }
        $eq = $line.IndexOf('=')
        if ($eq -lt 1) { return }
        $key = $line.Substring(0, $eq).Trim()
        $val = $line.Substring($eq + 1).Trim().Trim('"')
        # $env:$key = $val
        # $env:$key = $val
        Set-Item -Path "env:$key" -Value $val

        # Normalize relative key file paths to absolute (so it works regardless of CWD)
        if ($key -eq 'AUTH_PUBLIC_PEM_FILE' -and -not [System.IO.Path]::IsPathRooted($val)) {
          $candidate = Join-Path $root $val
          if (Test-Path -LiteralPath $candidate) {
            $env:AUTH_PUBLIC_PEM_FILE = (Resolve-Path -LiteralPath $candidate).Path
            Write-Info "AUTH_PUBLIC_PEM_FILE -> $($env:AUTH_PUBLIC_PEM_FILE)"
          } else {
            Write-Warn "AUTH_PUBLIC_PEM_FILE relative path not found: $val (looked at $candidate)"
          }
        }
      }
    }
  } else {
    Write-Warn "No .env found at $EnvFile (this is fine if you use system env vars)"
  }

  $buildDir = Join-Path $root "out/build/$Preset"
  $exe = Join-Path $buildDir "ws_server.exe"

  if ($Fresh -or -not (Test-Path $exe)) {
    if ($Fresh) { Write-Info "Configuring (fresh) preset $Preset" } else { Write-Info "Configuring preset $Preset" }
    $args = @('--preset', $Preset)
    if ($Fresh) { $args += '--fresh' }
    & cmake @args
    if ($LASTEXITCODE -ne 0) { throw "cmake configure failed ($LASTEXITCODE)" }

    Write-Info "Building preset $Preset"
    & cmake --build --preset $Preset
    if ($LASTEXITCODE -ne 0) { throw "cmake build failed ($LASTEXITCODE)" }
  }

  if (-not (Test-Path $exe)) {
    throw "Executable not found: $exe"
  }

  Write-Info ("Starting: {0} (PORT={1}, RABBIT_URL={2}, REDIS_URL={3})" -f $exe, $env:PORT, $env:RABBIT_URL, $env:REDIS_URL)
  & $exe
  if ($LASTEXITCODE -ne 0) { throw "ws_server exited with code $LASTEXITCODE" }
}
finally {
  Pop-Location
}
