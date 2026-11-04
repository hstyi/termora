#pragma once

#include <QByteArray>
#include <QFileDevice>
#include <QString>
#include <asio.hpp>
#include <atomic>
#include <memory>

/** 路径传输的本地文件；独立于 Transfer 存活，全部文件操作在线程池的 strand 中串行执行。 */
class QSshSftpLocalFile : public std::enable_shared_from_this<QSshSftpLocalFile> {
public:
    enum Operation {
        Prepare,
        Read,
        Write,
        Commit,
        Close
    };

    struct Result {
        QByteArray data;
        QString error;
        qint64 total = -1;
        bool eof = false;
    };

    QSshSftpLocalFile(asio::thread_pool &pool, QString path, bool upload, bool overwrite);
    /** self 使任务不依赖 Transfer；调用者通过 co_spawn 将任务放入 executor。 */
    static asio::awaitable<Result> run(std::shared_ptr<QSshSftpLocalFile> self, Operation operation,
                                       QByteArray data = {});
    /** 取消排队操作，并在已经开始的文件操作之后关闭和清理文件。 */
    void stop();

    asio::strand<asio::thread_pool::executor_type> executor;

private:
    Result prepare();
    Result read();
    Result write(const QByteArray &data);
    Result commit();

    const QString path;
    const bool upload;
    const bool overwrite;
    std::atomic_bool cancelled = false;
    std::unique_ptr<QFileDevice> file;
};
