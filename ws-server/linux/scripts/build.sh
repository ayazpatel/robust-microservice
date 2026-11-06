#!/usr/bin/env bash
set -euo pipefail

# Go to script folder
cd "$(dirname "$0")"

VCPKG_ROOT="$(realpath ../../vcpkg)"
TOOLCHAIN="$VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake"

echo "Using vcpkg from: $VCPKG_ROOT"

cmake -S .. -B ../build \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
  -DVCPKG_FEATURE_FLAGS=manifests \
  -DVCPKG_MANIFEST_DIR=".."

cmake --build ../build -j$(nproc)
