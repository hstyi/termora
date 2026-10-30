find_package(
    Qt${QT_MAJOR}
    COMPONENTS Core Gui Widgets Svg Concurrent Network
    REQUIRED
)
find_package(SQLiteCpp CONFIG REQUIRED)
find_package(spdlog CONFIG REQUIRED)
find_package(KF${QT_MAJOR}SyntaxHighlighting CONFIG REQUIRED)
find_package(efsw CONFIG REQUIRED)

add_executable(${PROJECT_NAME} main.cpp)

target_link_libraries(
    ${PROJECT_NAME}
    PRIVATE Qt${QT_MAJOR}::Core
            Qt${QT_MAJOR}::Gui
            Qt${QT_MAJOR}::Widgets
            Qt${QT_MAJOR}::Svg
            Qt${QT_MAJOR}::Concurrent
            Qt${QT_MAJOR}::Network
            qtermwidget
            qssh
            qvfs
            qpty
)

if(WIN32)
    include(InstallRequiredSystemLibraries)
    get_target_property(QPTY_RUNTIME_FILES qpty QPTY_RUNTIME_FILES)
    add_custom_command(
        TARGET ${PROJECT_NAME}
        POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different ${QPTY_RUNTIME_FILES} ${CMAKE_INSTALL_SYSTEM_RUNTIME_LIBS}
                "$<TARGET_FILE_DIR:${PROJECT_NAME}>"
        COMMAND_EXPAND_LISTS VERBATIM
    )
endif()
