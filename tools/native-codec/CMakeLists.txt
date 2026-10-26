cmake_minimum_required(VERSION 3.20)
project(endfield_codec C)
set(CMAKE_MSVC_RUNTIME_LIBRARY MultiThreaded$<$<CONFIG:Debug>:Debug>)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(ZLIB_COMPAT ON CACHE BOOL "" FORCE)
set(ZLIB_ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(ZLIBNG_ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(WITH_GTEST OFF CACHE BOOL "" FORCE)
add_subdirectory(vendor/zlib-ng-2.2.4 zlib)
include(ExternalProject)
ExternalProject_Add(jpeg_codec
  SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/vendor/libjpeg-turbo-3.1.2
  BINARY_DIR ${CMAKE_CURRENT_BINARY_DIR}/jpeg
  CMAKE_ARGS -DCMAKE_BUILD_TYPE=Release -DENABLE_SHARED=OFF -DENABLE_STATIC=ON
    -DWITH_TURBOJPEG=ON -DWITH_TOOLS=OFF -DWITH_TESTS=OFF -DWITH_SIMD=ON
    -DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded
  BUILD_COMMAND ${CMAKE_COMMAND} --build . --target turbojpeg-static
  INSTALL_COMMAND ""
  BUILD_BYPRODUCTS ${CMAKE_CURRENT_BINARY_DIR}/jpeg/turbojpeg-static.lib)
add_library(turbojpeg-static STATIC IMPORTED)
set_target_properties(turbojpeg-static PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_BINARY_DIR}/jpeg/turbojpeg-static.lib)
add_library(endfield_codec SHARED codec.c vendor/libspng-0.7.4/spng/spng.c)
target_compile_definitions(endfield_codec PRIVATE SPNG_STATIC)
target_include_directories(endfield_codec PRIVATE vendor/libspng-0.7.4/spng vendor/libjpeg-turbo-3.1.2/src vendor/zlib-ng-2.2.4 ${CMAKE_CURRENT_BINARY_DIR}/zlib)
target_link_libraries(endfield_codec PRIVATE zlibstatic turbojpeg-static)
add_dependencies(endfield_codec jpeg_codec)
