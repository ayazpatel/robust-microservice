cmake_minimum_required(VERSION 3.20)
project(ws_server_windows LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)   # also fix for C++20 requirement (see below)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(unofficial-uwebsockets CONFIG REQUIRED)
find_package(libuv CONFIG REQUIRED)
find_package(jwt-cpp CONFIG REQUIRED)
find_package(OpenSSL REQUIRED)
find_package(nlohmann_json CONFIG REQUIRED)
find_package(rabbitmq-c CONFIG REQUIRED)
find_package(redis++ CONFIG REQUIRED)
find_package(Threads REQUIRED)

add_executable(ws_server src/server.cpp)

# ✅ Correct target name:
target_link_libraries(ws_server PRIVATE
  unofficial::uwebsockets::uwebsockets
  jwt-cpp::jwt-cpp
  OpenSSL::Crypto
  nlohmann_json::nlohmann_json
  rabbitmq::rabbitmq
  redis++::redis++
  Threads::Threads
)

# Link libuv if available
if(TARGET libuv::uv_a)
  target_link_libraries(ws_server PRIVATE libuv::uv_a)
elseif(TARGET libuv::uv)
  target_link_libraries(ws_server PRIVATE libuv::uv)
endif()
