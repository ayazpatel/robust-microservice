cmake_minimum_required(VERSION 3.16)
project(ws_server_linux LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(unofficial-uwebsockets CONFIG REQUIRED)
find_package(libuv CONFIG QUIET)

add_executable(ws_server src/server.cpp)

# Correct uWebSockets target
target_link_libraries(ws_server PRIVATE unofficial::uwebsockets::uwebsockets)

# Optional libuv linking
if(TARGET libuv::uv_a)
  target_link_libraries(ws_server PRIVATE libuv::uv_a)
elseif(TARGET libuv::uv)
  target_link_libraries(ws_server PRIVATE libuv::uv)
endif()
