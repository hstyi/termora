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
    qsshsftpsession_test.cpp
    qsshsftppipeline_test.cpp
    qsshsftptransfer_test.cpp
    qsshsftppermissions_test.cpp
)
target_link_libraries(qssh_tests PRIVATE qssh GTest::gtest Qt${QT_MAJOR}::Network)
target_compile_definitions(
    qssh_tests PRIVATE QSSH_TEST_PYTHON="${QSSH_TEST_PYTHON}"
                       QSSH_TEST_SERVER="${CMAKE_CURRENT_SOURCE_DIR}/ssh_test_server.py"
)
add_test(NAME qssh.integration COMMAND qssh_tests)
set_tests_properties(qssh.integration PROPERTIES TIMEOUT 180)

# 显式运行的真实 SFTP 性能测试；不注册到 CTest，避免让常规测试承担大文件传输。
add_executable(qssh_sftp_benchmarks test_main.cpp qssh_test_support.cpp qssh_test_support.h qsshsftp_benchmark.cpp)
target_link_libraries(qssh_sftp_benchmarks PRIVATE qssh GTest::gtest Qt${QT_MAJOR}::Network ssh)
target_compile_definitions(
    qssh_sftp_benchmarks PRIVATE QSSH_TEST_PYTHON="${QSSH_TEST_PYTHON}"
                                 QSSH_TEST_SERVER="${CMAKE_CURRENT_SOURCE_DIR}/ssh_test_server.py"
)
