cmake_minimum_required(VERSION 3.25)
project(qssh)

find_package(
    Qt${QT_MAJOR}
    COMPONENTS Core
    REQUIRED
)
find_package(asio CONFIG REQUIRED)
find_package(libssh CONFIG REQUIRED)

add_library(
    ${PROJECT_NAME} include/qssh/qsshclient.h include/qssh/qsshsession.h include/qssh/qsshchannel.h src/qsshclient.cpp
)
target_include_directories(${PROJECT_NAME} PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_link_libraries(${PROJECT_NAME} PRIVATE Qt${QT_MAJOR}::Core asio::asio ssh)
