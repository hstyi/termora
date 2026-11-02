find_package(
    Qt${QT_MAJOR}
    COMPONENTS Core Gui Widgets Svg Concurrent Network
    REQUIRED
)
find_package(SQLiteCpp CONFIG REQUIRED)
find_package(spdlog CONFIG REQUIRED)
find_package(KF${QT_MAJOR}SyntaxHighlighting CONFIG REQUIRED)
find_package(efsw CONFIG REQUIRED)
find_package(spdlog CONFIG REQUIRED)

add_subdirectory(../thirdparty/qwindowkit "${CMAKE_CURRENT_BINARY_DIR}/qwindowkit")

add_executable(
    ${PROJECT_NAME}
    resources/app.qrc
    main.cpp
    applicationstyle.h
    applicationstyle.cpp
    windowsmenu.cpp
    windowsmenu.h
    tabwidget.h
    tabwidget.cpp
    tabwidgetmodel.h
    tabwidgetmodel.cpp
    dialog.h
    dialog.cpp
    newhostdialog.h
    newhostdialog.cpp
    mainwindow.h
    mainwindow.cpp
    treeview.h
    treeview.cpp
    macscrollstyle.h
    macscrollstyle.cpp
    itemviewpadding.h
    hosttreeview.cpp
    hosttreeview.h
    hosttreemodel.h
    hosttreemodel.cpp
    menu.h
    menu.cpp
    stores/hoststore.h
    stores/hoststore.cpp
    stores/database.cpp
    stores/database.h
    stores/entities.h
    actions/actionbutton.cpp
    actions/actionbutton.h
    actions/actionevent.cpp
    actions/actionevent.h
    actions/actionmenu.cpp
    actions/actionmenu.h
    actions/actionsystem.cpp
    actions/actionsystem.h
    actions/actiontoolbar.cpp
    actions/actiontoolbar.h
    actions/actionupdatescheduler.cpp
    actions/actionupdatescheduler.h
    actions/anaction.cpp
    actions/anaction.h
    actions/datacontext.cpp
    actions/datacontext.h
    actions/datakey.h
    actions/datakeys.h
    actions/dataprovider.h
    actions/presentation.h
    actions/executes/hosttreeaction.h
    actions/executes/hosttreeaction.cpp
    actions/executes/newhostaction.h
    actions/executes/newhostaction.cpp
)

target_link_libraries(
    ${PROJECT_NAME}
    PRIVATE Qt${QT_MAJOR}::Core
            Qt${QT_MAJOR}::Gui
            Qt${QT_MAJOR}::Widgets
            Qt${QT_MAJOR}::Svg
            Qt${QT_MAJOR}::Concurrent
            Qt${QT_MAJOR}::Network
            SQLiteCpp
            QWKCore
            QWKWidgets
            WidgetFrame
            spdlog::spdlog_header_only
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
