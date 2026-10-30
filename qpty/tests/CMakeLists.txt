find_package(GTest CONFIG REQUIRED)

# 使用专用子程序验证真实 PTY，不依赖用户的 shell 配置。
add_executable(qpty_test_helper pty_test_helper.cpp)
target_link_libraries(qpty_test_helper PRIVATE Qt${QT_MAJOR}::Core)

add_executable(qpty_tests qptyprocess_test.cpp)
target_link_libraries(qpty_tests PRIVATE qpty GTest::gtest)
target_compile_definitions(qpty_tests PRIVATE QPTY_HELPER_PATH="$<TARGET_FILE:qpty_test_helper>")
add_dependencies(qpty_tests qpty_test_helper)

if(WIN32)
    get_target_property(QPTY_RUNTIME_FILES qpty QPTY_RUNTIME_FILES)
    add_custom_command(
        TARGET qpty_tests
        POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different ${QPTY_RUNTIME_FILES} "$<TARGET_FILE_DIR:qpty_tests>"
        COMMAND_EXPAND_LISTS VERBATIM
    )
endif()

add_test(NAME qpty.integration COMMAND qpty_tests)
set_tests_properties(qpty.integration PROPERTIES TIMEOUT 120)
