cmake_minimum_required(VERSION 3.20)
project(MCPassthroughCompositor LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(RESHADE_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../.tools/reshade-src/include" CACHE PATH "ReShade add-on API include directory")

add_library(MCPassthrough SHARED src/compositor.cpp src/shared_memory.cpp)
target_include_directories(MCPassthrough PRIVATE "${RESHADE_INCLUDE_DIR}")
target_compile_definitions(MCPassthrough PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX UNICODE _UNICODE)
target_compile_options(MCPassthrough PRIVATE /W4 /permissive-)

set_target_properties(MCPassthrough PROPERTIES PREFIX "" SUFFIX ".addon64")
