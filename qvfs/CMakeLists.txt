cmake_minimum_required(VERSION 3.25)
project(qvfs)

find_package(
    Qt${QT_MAJOR}
    COMPONENTS Core
    REQUIRED
)

add_library(${PROJECT_NAME})

target_link_directories(${PROJECT_NAME} PRIVATE Qt${QT_MAJOR}::Core)
