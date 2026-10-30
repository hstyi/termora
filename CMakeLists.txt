cmake_minimum_required(VERSION 3.25)
project(termora)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_AUTOUIC ON)
include(CTest)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin")
set(QMSETUP_FIND_QT_ORDER Qt5 Qt6)
set(QWINDOWKIT_BUILD_STATIC ON)
set(QWINDOWKIT_BUILD_EXAMPLES ON)
set(QWINDOWKIT_INSTALL OFF)

if(WIN32)
    add_compile_options(/utf-8)
elseif(APPLE)
    set(VCPKG_APPLOCAL_DEPS OFF)
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
        set(CMAKE_OSX_DEPLOYMENT_TARGET "11.0")
    elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64")
        set(CMAKE_OSX_DEPLOYMENT_TARGET "10.13")
    endif()
endif()

if(NOT DEFINED QT_MAJOR)
    set(QT_MAJOR 5)
endif()

if(QT_MAJOR EQUAL 6)
    set(BUILD_WITH_QT5 OFF)
    set(QT_VERSION_MAJOR 6)
    set(QMSETUP_FIND_QT_ORDER Qt6 Qt5)
else()
    set(BUILD_WITH_QT5 ON)
    set(QT_VERSION_MAJOR 5)
    set(QT_FEATURE_zstd ON)
    set(QMSETUP_FIND_QT_ORDER Qt5 Qt6)
endif()

find_program(CLANG_FORMAT clang-format)
find_program(CMAKE_FORMAT cmake-format)

find_package(
    Qt${QT_MAJOR}
    COMPONENTS Core Gui Widgets Svg Concurrent Network
    REQUIRED
)
find_package(SQLiteCpp CONFIG REQUIRED)
find_package(spdlog CONFIG REQUIRED)
find_package(KF${QT_MAJOR}SyntaxHighlighting CONFIG REQUIRED)
find_package(efsw CONFIG REQUIRED)

if(LINUX)
    find_package(
        Qt${QT_MAJOR}
        COMPONENTS DBus
        REQUIRED
    )
endif()

if(QT_MAJOR EQUAL 5)
    set(QT_FEATURE_zstd ON)
endif()

add_subdirectory(qvfs)
add_subdirectory(qssh)
add_subdirectory(qpty)
add_subdirectory(qtermwidget)
add_subdirectory(src)

if(CLANG_FORMAT AND CMAKE_FORMAT)
    file(
        GLOB_RECURSE
        FORMAT_SOURCES
        CONFIGURE_DEPENDS
        "${CMAKE_SOURCE_DIR}/*.cpp"
        "${CMAKE_SOURCE_DIR}/*.cc"
        "${CMAKE_SOURCE_DIR}/*.c"
        "${CMAKE_SOURCE_DIR}/*.h"
        "${CMAKE_SOURCE_DIR}/*.hpp"
    )

    file(GLOB_RECURSE FORMAT_CMAKE_SOURCES CONFIGURE_DEPENDS "${CMAKE_SOURCE_DIR}/CMakeLists.txt"
         "${CMAKE_SOURCE_DIR}/*.cmake"
    )

    list(FILTER FORMAT_SOURCES EXCLUDE REGEX "/(cmake-build-[^/]+|build|vcpkg_installed|thirdparty)/")

    list(FILTER FORMAT_CMAKE_SOURCES EXCLUDE REGEX "/(cmake-build-[^/]+|build|vcpkg_installed|thirdparty)/")

    add_custom_target(
        format
        COMMAND ${CLANG_FORMAT} --style=file --verbose -i ${FORMAT_SOURCES}
        COMMAND ${CMAKE_FORMAT} -i ${FORMAT_CMAKE_SOURCES}
        COMMENT "Formatting C/C++ and CMake files"
        COMMAND_EXPAND_LISTS USES_TERMINAL VERBATIM
    )
else()
    if(NOT CLANG_FORMAT)
        message(WARNING "clang-format not found")
    endif()

    if(NOT CMAKE_FORMAT)
        message(WARNING "cmake-format not found")
    endif()
endif()
