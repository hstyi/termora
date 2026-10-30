#include <QCoreApplication>
#include <gtest/gtest.h>

/** 创建 Qt 事件循环后运行 gtest；测试本身通过 waitUntil 驱动异步通知。 */
int main(int argc, char **argv) {
    QCoreApplication application(argc, argv);
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
