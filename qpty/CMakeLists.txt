cmake_minimum_required(VERSION 3.25)
project(qpty)

find_package(
    Qt${QT_MAJOR}
    COMPONENTS Core
    REQUIRED
)
find_package(Threads REQUIRED)

add_library(${PROJECT_NAME} include/qpty/qptyprocess.h src/qptyprocess.cpp src/qptyprocess_p.h)
target_include_directories(${PROJECT_NAME} PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_link_libraries(
    ${PROJECT_NAME}
    PUBLIC Qt${QT_MAJOR}::Core
    PRIVATE Threads::Threads
)

if(WIN32)
    # vcpkg 的 winpty 端口没有 CMake config，需要直接查找头文件和库。
    find_path(WINPTY_INCLUDE_DIR winpty.h REQUIRED)
    get_filename_component(WINPTY_PREFIX "${WINPTY_INCLUDE_DIR}" DIRECTORY)
    find_library(
        WINPTY_LIBRARY_RELEASE
        NAMES winpty
        PATHS "${WINPTY_PREFIX}/lib"
        NO_DEFAULT_PATH REQUIRED
    )
    find_library(
        WINPTY_LIBRARY_DEBUG
        NAMES winpty
        PATHS "${WINPTY_PREFIX}/debug/lib"
        NO_DEFAULT_PATH
    )
    find_program(
        WINPTY_AGENT_EXECUTABLE
        NAMES winpty-agent.exe
        HINTS "${WINPTY_PREFIX}/tools/winpty" REQUIRED
    )
    find_file(
        WINPTY_DLL_RELEASE
        NAMES winpty.dll
        PATHS "${WINPTY_PREFIX}/bin"
        NO_DEFAULT_PATH
    )
    find_file(
        WINPTY_DLL_DEBUG
        NAMES winpty.dll
        PATHS "${WINPTY_PREFIX}/debug/bin"
        NO_DEFAULT_PATH
    )

    target_sources(${PROJECT_NAME} PRIVATE src/qptyprocess_win.cpp)
    target_include_directories(${PROJECT_NAME} PRIVATE "${WINPTY_INCLUDE_DIR}")
    target_compile_definitions(${PROJECT_NAME} PRIVATE _WIN32_WINNT=0x0601 WINVER=0x0601 NOMINMAX UNICODE _UNICODE)
    if(WINPTY_LIBRARY_DEBUG)
        target_link_libraries(
            ${PROJECT_NAME} PRIVATE optimized "${WINPTY_LIBRARY_RELEASE}" debug "${WINPTY_LIBRARY_DEBUG}"
        )
    else()
        target_link_libraries(${PROJECT_NAME} PRIVATE "${WINPTY_LIBRARY_RELEASE}")
    endif()

    # 运行文件由宿主和测试目标复制到各自输出目录；静态 winpty 也需要 agent。
    set(QPTY_RUNTIME_FILES "${WINPTY_AGENT_EXECUTABLE}")
    if(WINPTY_DLL_RELEASE)
        if(WINPTY_DLL_DEBUG)
            list(APPEND QPTY_RUNTIME_FILES "$<IF:$<CONFIG:Debug>,${WINPTY_DLL_DEBUG},${WINPTY_DLL_RELEASE}>")
        else()
            list(APPEND QPTY_RUNTIME_FILES "${WINPTY_DLL_RELEASE}")
        endif()
    else()
        target_compile_definitions(${PROJECT_NAME} PRIVATE BUILD_STATIC)
        target_link_libraries(${PROJECT_NAME} PRIVATE advapi32 user32)
    endif()
    set_property(TARGET ${PROJECT_NAME} PROPERTY QPTY_RUNTIME_FILES "${QPTY_RUNTIME_FILES}")
elseif(UNIX)
    target_sources(${PROJECT_NAME} PRIVATE src/qptyprocess_unix.cpp)
    find_library(QPTY_UTIL_LIBRARY util)
    if(QPTY_UTIL_LIBRARY)
        target_link_libraries(${PROJECT_NAME} PRIVATE ${QPTY_UTIL_LIBRARY})
    endif()
endif()

if(BUILD_TESTING)
    add_subdirectory(tests)
endif()
