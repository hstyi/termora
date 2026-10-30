cmake_minimum_required(VERSION 3.25)
project(qssh)

find_package(
    Qt${QT_MAJOR}
    COMPONENTS Core
    REQUIRED
)
find_package(asio CONFIG REQUIRED)
find_package(libssh CONFIG REQUIRED)
find_package(Threads REQUIRED)

add_library(
    ${PROJECT_NAME}
    include/qssh/qsshclient.h
    include/qssh/qsshsession.h
    include/qssh/qsshchannel.h
    include/qssh/qsshexecchannel.h
    include/qssh/qsshshellchannel.h
    include/qssh/qsshtunnel.h
    src/qsshclient.cpp
    src/qsshchannel.cpp
    src/qsshexecchannel.cpp
    src/qsshsession.cpp
    src/qsshshellchannel.cpp
    src/qsshclient_p.h
    src/qsshsession_p.h
    src/qsshchannel_p.h
    src/qsshsession_proxy.cpp
    src/qsshsession_forwarding.cpp
    src/qsshtunnel.cpp
    src/qsshtunnel_p.h
    src/qsshforwardstream.cpp
    src/qsshforwardstream_p.h
)
target_include_directories(${PROJECT_NAME} PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_link_libraries(
    ${PROJECT_NAME}
    PUBLIC Qt${QT_MAJOR}::Core
    PRIVATE asio::asio ssh Threads::Threads
)

if(WIN32)
    target_compile_definitions(${PROJECT_NAME} PRIVATE _WIN32_WINNT=0x0601 NOMINMAX)
    target_link_libraries(${PROJECT_NAME} PRIVATE ws2_32)
endif()

if(BUILD_TESTING)
    add_subdirectory(tests)
endif()
