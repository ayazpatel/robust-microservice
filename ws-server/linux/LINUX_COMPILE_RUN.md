## 🐧 **LINUX_COMPILE_RUN.md**

````markdown
# 🐧 Linux Build & Run Guide — uWebSockets Server

This document explains how to **build, compile, and run** the uWebSockets-based server on **Ubuntu 20.04 / 22.04 / 24.04** using **CMake** and a **local vcpkg** toolchain.

---

## 📦 1. Install Dependencies

Run these commands first:

```bash
sudo apt update
sudo apt install -y build-essential cmake git pkg-config curl unzip tar
````

---

## 🧰 2. Project Structure

```
ws-server/
├─ vcpkg/
├─ vcpkg.json
├─ linux/
│  ├─ CMakeLists.txt
│  ├─ src/
│  │  └─ server.cpp
│  └─ scripts/
│     └─ build.sh
└─ windows/
```

---

## ⚙️ 3. Local vcpkg Setup

If you haven’t cloned vcpkg yet:

```bash
git clone https://github.com/microsoft/vcpkg ../../vcpkg
../../vcpkg/bootstrap-vcpkg.sh
```

---

## 🧾 4. vcpkg Manifest

File: `ws-server/vcpkg.json`

```json
{
  "name": "ws-server",
  "version-string": "0.1.0",
  "builtin-baseline": "74e6536215718009aae747d86d84b78376bf9e09",
  "dependencies": [
    { "name": "uwebsockets", "features": ["zlib", "libdeflate", "ssl"] }
  ]
}
```

---

## ⚙️ 5. Linux CMakeLists.txt

File: `ws-server/linux/CMakeLists.txt`

```cmake
cmake_minimum_required(VERSION 3.16)
project(ws_server_linux LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(unofficial-uwebsockets CONFIG REQUIRED)
find_package(libuv CONFIG QUIET)

add_executable(ws_server src/server.cpp)
target_link_libraries(ws_server PRIVATE unofficial::uwebsockets::uwebsockets)

if(TARGET libuv::uv_a)
  target_link_libraries(ws_server PRIVATE libuv::uv_a)
elseif(TARGET libuv::uv)
  target_link_libraries(ws_server PRIVATE libuv::uv)
endif()
```

---

## 🏗️ 6. Build Script

File: `linux/scripts/build.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail
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
```

Make it executable:

```bash
chmod +x linux/scripts/build.sh
```

---

## 🚀 7. Build & Run

Run these commands:

```bash
cd ws-server/linux
./scripts/build.sh
./build/ws_server
```

Output example:

```
Listening on :9001
```

---

## 🔍 8. Test

* HTTP Health Check → `curl http://localhost:9001/healthz`
* WebSocket → Connect to `ws://localhost:9001`

---

## 🔒 9. Optional — Enable TLS

Use SSL mode in your code:

```cpp
uWS::SSLApp({
  .key_file_name = "key.pem",
  .cert_file_name = "cert.pem"
})
```

---

## 🧾 10. Run as a Service (optional)

File: `/etc/systemd/system/ws-server.service`

```ini
[Unit]
Description=uWebSockets Server
After=network.target

[Service]
ExecStart=/home/ubuntu/ws-server/linux/build/ws_server
Restart=always
User=ubuntu
WorkingDirectory=/home/ubuntu/ws-server/linux
StandardOutput=append:/var/log/ws-server.log
StandardError=append:/var/log/ws-server.log

[Install]
WantedBy=multi-user.target
```

Enable it:

```bash
sudo systemctl daemon-reload
sudo systemctl enable ws-server
sudo systemctl start ws-server
```

---

✅ **You’re done!**
You now have a fully working uWebSockets server compiled and running on Linux.