cmake_minimum_required(VERSION 3.25)
project(qtermwidget)

find_package(
    Qt${QT_MAJOR}
    COMPONENTS Core Widgets
    REQUIRED
)

set(GHOSTTY_ZIG_BUILD_FLAGS
    "-Dsimd=false;-Demit-xcframework=false"
    CACHE STRING "" FORCE
)
add_subdirectory(../thirdparty/ghostty "${CMAKE_CURRENT_BINARY_DIR}/ghostty")

add_library(${PROJECT_NAME} include/qtermwidget/qtermwidget.h src/qtermwidget.cpp src/qtermwidget_p.h)
target_include_directories(${PROJECT_NAME} PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_compile_definitions(${PROJECT_NAME} PRIVATE QT_NO_KEYWORDS)

target_link_libraries(
    ${PROJECT_NAME}
    PUBLIC Qt${QT_MAJOR}::Core Qt${QT_MAJOR}::Widgets
    PRIVATE ghostty-vt-static
)
