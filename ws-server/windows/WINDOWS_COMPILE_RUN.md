## 🪟 **WINDOWS_COMPILE_RUN.md**

```markdown
# 🪟 Windows Build & Run Guide — uWebSockets Server

This document explains how to **build, compile, and run** the uWebSockets-based server on **Windows 10 / 11 / Server** using **CMake** and a **local vcpkg** toolchain.

---

## 🧰 1. Prerequisites

Install these before you start:

- **Visual Studio 2022** → *Desktop development with C++* workload  
- **Git**
- **Developer PowerShell for VS 2022**

---

## 📂 2. Project Structure

````

ws-server/
├─ vcpkg/
├─ vcpkg.json
├─ windows/
│  ├─ CMakeLists.txt
│  ├─ src/
│  │  └─ server.cpp
│  └─ scripts/
│     └─ build.ps1
└─ linux/

````

---

## ⚙️ 3. Local vcpkg Setup

Run these commands:

```powershell
cd E:\TelegramStation\Sorya\Project03-02Upgraded\ws-server\windows
git clone https://github.com/microsoft/vcpkg ..\..\vcpkg
& ..\..\vcpkg\bootstrap-vcpkg.bat
````

---

## 📄 4. vcpkg Manifest

File: `ws-server/vcpkg.json`

```json
{
  "name": "ws-server-windows",
  "version-string": "0.1.0",
  "builtin-baseline": "74e6536215718009aae747d86d84b78376bf9e09",
  "dependencies": [
    { "name": "uwebsockets", "features": ["zlib", "libdeflate", "ssl"] }
  ]
}
```

---

## ⚙️ 5. CMakeLists.txt

File: `ws-server/windows/CMakeLists.txt`

```cmake
cmake_minimum_required(VERSION 3.20)
project(ws_server_windows LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(unofficial-uwebsockets CONFIG REQUIRED)
find_package(libuv CONFIG REQUIRED)

add_executable(ws_server src/server.cpp)
target_link_libraries(ws_server PRIVATE unofficial::uwebsockets::uwebsockets)

if(TARGET libuv::uv_a)
  target_link_libraries(ws_server PRIVATE libuv::uv_a)
elseif(TARGET libuv::uv)
  target_link_libraries(ws_server PRIVATE libuv::uv)
endif()
```

---

## 🧱 6. Build Script

File: `windows/scripts/build.ps1`

```powershell
$ErrorActionPreference = "Stop"
$VCPKG_ROOT = Join-Path $PSScriptRoot "..\..\..\vcpkg"
$TOOLCHAIN  = Join-Path $VCPKG_ROOT "scripts\buildsystems\vcpkg.cmake"

cmake -S . -B .\build `
  -G "Visual Studio 17 2022" -A x64 `
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" `
  -DVCPKG_FEATURE_FLAGS=manifests `
  -DVCPKG_MANIFEST_DIR=".."

cmake --build .\build --config Release
```

---

## 🚀 7. Build & Run

```powershell
cd ws-server\windows
.\scripts\build.ps1
.\build\Release\ws_server.exe
```

Output:

```
Listening on :9001
```

---

## 🧪 8. Test

* WebSocket: `ws://localhost:9001`
* HTTP Health Check: `http://localhost:9001/healthz`

---

## 🔒 9. TLS (Optional)

Enable secure WebSocket (wss://):

```cpp
uWS::SSLApp({
  .key_file_name = "key.pem",
  .cert_file_name = "cert.pem"
})
```

---

✅ **Done!**
You now have a fully functioning uWebSockets server compiled and running on Windows using local vcpkg and CMake.