find_package(GTest CONFIG REQUIRED)
find_package(
    Qt${QT_MAJOR}
    COMPONENTS Network
    REQUIRED
)
find_package(
    Python3
    COMPONENTS Interpreter
    QUIET
)

# 真实协议测试使用隔离的 Paramiko 服务端，不读取用户密钥或连接外部 SSH 主机。
set(QSSH_TEST_PYTHON
    "${Python3_EXECUTABLE}"
    CACHE FILEPATH "Python interpreter with Paramiko for SSH integration tests"
)

add_executable(
    qssh_tests
    test_main.cpp
    qssh_test_support.h
    qssh_test_support.cpp
    qsshclient_test.cpp
    qsshsession_test.cpp
    qsshauthentication_test.cpp
    qsshchannel_test.cpp
    qsshproxy_test.cpp
    qsshtunnel_test.cpp
    qsshforwarding_test.cpp
)
target_link_libraries(qssh_tests PRIVATE qssh GTest::gtest Qt${QT_MAJOR}::Network)
target_compile_definitions(
    qssh_tests PRIVATE QSSH_TEST_PYTHON="${QSSH_TEST_PYTHON}"
                       QSSH_TEST_SERVER="${CMAKE_CURRENT_SOURCE_DIR}/ssh_test_server.py"
)
add_test(NAME qssh.integration COMMAND qssh_tests)
set_tests_properties(qssh.integration PROPERTIES TIMEOUT 180)
