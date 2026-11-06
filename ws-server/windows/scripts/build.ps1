# windows\scripts\build.ps1
$ErrorActionPreference = "Stop"

$VCPKG_ROOT = Join-Path $PSScriptRoot "..\..\..\vcpkg"
$TOOLCHAIN  = Join-Path $VCPKG_ROOT "scripts\buildsystems\vcpkg.cmake"

cmake -S . -B .\build `
  -G "Visual Studio 17 2022" -A x64 `
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" `
  -DVCPKG_FEATURE_FLAGS=manifests `
  -DVCPKG_MANIFEST_DIR=".."      # because manifest is now at ws-server\

cmake --build .\build --config Release
