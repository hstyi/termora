#include "qsshsftplocalfile_p.h"

#include <QFile>
#include <QSaveFile>
#include <QTemporaryFile>

QSshSftpLocalFile::QSshSftpLocalFile(asio::thread_pool &pool, QString path, bool upload, bool overwrite)
    : executor(asio::make_strand(pool)),
      path(std::move(path)),
      upload(upload),
      overwrite(overwrite) {}

asio::awaitable<QSshSftpLocalFile::Result> QSshSftpLocalFile::run(std::shared_ptr<QSshSftpLocalFile> self,
                                                                  Operation operation, QByteArray data) {
    if (operation == Close) {
        self->file.reset();
        co_return Result();
    }
    if (self->cancelled.load(std::memory_order_relaxed)) {
        co_return Result{.error = QStringLiteral("Local file operation cancelled")};
    }
    switch (operation) {
        case Prepare:
            co_return self->prepare();
        case Read:
            co_return self->read();
        case Write:
            co_return self->write(data);
        case Commit:
            co_return self->commit();
        case Close:
            break;
    }
    co_return Result();
}

void QSshSftpLocalFile::stop() {
    cancelled.store(true, std::memory_order_relaxed);
    asio::post(executor, [self = shared_from_this()] { self->file.reset(); });
}

QSshSftpLocalFile::Result QSshSftpLocalFile::prepare() {
    if (upload) {
        file = std::make_unique<QFile>(path);
    } else if (overwrite) {
        file = std::make_unique<QSaveFile>(path);
    } else {
        if (QFile::exists(path)) {
            return {.error = QStringLiteral("Download destination already exists")};
        }
        file = std::make_unique<QTemporaryFile>(path + QStringLiteral(".qssh-XXXXXX"));
    }
    // 普通文件不处理 Qt 事件；移除创建线程归属后，仅由 strand 串行访问。
    file->moveToThread(nullptr);
    if (!file->open(upload ? QIODevice::ReadOnly : QIODevice::WriteOnly)) {
        return {.error = file->errorString()};
    }
    return {.total = upload ? file->size() : -1};
}

QSshSftpLocalFile::Result QSshSftpLocalFile::read() {
    QByteArray data(65536, Qt::Uninitialized);
    const auto count = file->read(data.data(), data.size());
    if (count < 0) {
        return {.error = file->errorString()};
    }
    data.resize(static_cast<int>(count));
    return {.data = std::move(data), .eof = count == 0};
}

QSshSftpLocalFile::Result QSshSftpLocalFile::write(const QByteArray &data) {
    qsizetype written = 0;
    while (written < data.size()) {
        const auto count = file->write(data.constData() + written, data.size() - written);
        if (count <= 0) {
            return {.error = file->errorString()};
        }
        written += count;
    }
    return {};
}

QSshSftpLocalFile::Result QSshSftpLocalFile::commit() {
    if (overwrite) {
        if (!static_cast<QSaveFile *>(file.get())->commit()) {
            return {.error = file->errorString()};
        }
    } else {
        auto *temporary = static_cast<QTemporaryFile *>(file.get());
        if (!temporary->flush()) {
            return {.error = temporary->errorString()};
        }
        temporary->close();
        if (!temporary->rename(path)) {
            return {.error = temporary->errorString()};
        }
        temporary->setAutoRemove(false);
    }
    file.reset();
    return {};
}
