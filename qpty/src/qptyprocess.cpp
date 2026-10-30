#include "qptyprocess_p.h"

#include <QDir>
#include <QPointer>
#include <algorithm>
#include <utility>

namespace {
    /** 限制尚未写入系统的输入量，避免终端阻塞时无限积压内存。 */
    constexpr qint64 KMaximumQueuedBytes = 4 * 1024 * 1024;
} // namespace

QPtyProcessPrivate::QPtyProcessPrivate(QPtyProcess *process) : q(process) {}

QPtyProcessPrivate::~QPtyProcessPrivate() = default;

void QPtyProcessPrivate::post(std::function<void()> task) {
    // 析构先 join 后台线程，确保 invokeMethod 调用期间 QObject 尚未销毁。
    QMetaObject::invokeMethod(q, std::move(task), Qt::QueuedConnection);
}

void QPtyProcessPrivate::reportError(QPtyProcess::ProcessError value, const QString &message) {
    error = value;
    errorText = message;
    Q_EMIT q->errorOccurred(value);
}

QPtyProcessBackend::QPtyProcessBackend(QPtyProcessPrivate *owner, QPtyProcessOptions options)
    : owner(owner),
      options(std::move(options)),
      requestedSize(this->options.size) {}

void QPtyProcessBackend::enqueue(const QByteArray &data) {
    {
        std::lock_guard<std::mutex> lock = std::lock_guard<std::mutex>(mutex);
        pendingInput.append(data);
    }
    inputReady.notify_one();
    wake();
}

void QPtyProcessBackend::resize(const QSize &size) {
    {
        std::lock_guard<std::mutex> lock = std::lock_guard<std::mutex>(mutex);
        requestedSize = size;
        resizePending = true;
    }
    wake();
}

void QPtyProcessBackend::stop(bool force) {
    {
        std::lock_guard<std::mutex> lock = std::lock_guard<std::mutex>(mutex);
        stopRequest = std::max(stopRequest, force ? 2 : 1);
    }
    inputReady.notify_one();
    wake();
}

void QPtyProcessBackend::started(qint64 pid, QPtyProcess::Backend backend) {
    owner->post([d = owner, pid, backend] {
        d->pid = pid;
        d->active = backend;
        d->state = QPtyProcess::Running;
        QPointer<QPtyProcess> guard = QPointer<QPtyProcess>(d->q);
        Q_EMIT d->q->stateChanged(QPtyProcess::Running);
        if (guard) {
            Q_EMIT d->q->started();
        }
    });
}

void QPtyProcessBackend::output(const QByteArray &data) {
    if (data.isEmpty()) {
        return;
    }
    owner->post([d = owner, data] {
        d->received.append(data);
        Q_EMIT d->q->readyRead();
    });
}

void QPtyProcessBackend::written(qint64 count) {
    owner->post([d = owner, count] {
        d->queuedBytes -= count;
        Q_EMIT d->q->bytesWritten(count);
    });
}

void QPtyProcessBackend::reportError(QPtyProcess::ProcessError error, const QString &message) {
    owner->post([d = owner, error, message] { d->reportError(error, message); });
}

void QPtyProcessBackend::failed(const QString &message, QPtyProcess::ProcessError error) {
    owner->post([d = owner, message, error] {
        d->backend.reset();
        d->state = QPtyProcess::Stopped;
        d->error = error;
        d->errorText = message;
        QPointer<QPtyProcess> guard = QPointer<QPtyProcess>(d->q);
        Q_EMIT d->q->stateChanged(QPtyProcess::Stopped);
        if (guard) {
            Q_EMIT guard->errorOccurred(error);
        }
    });
}

void QPtyProcessBackend::finished(int code, QPtyProcess::ExitStatus status) {
    owner->post([d = owner, code, status] {
        // 平台后端必须先排空输出再投递此通知，finished 槽中可以读取完整尾部输出。
        d->backend.reset();
        d->pid = 0;
        d->queuedBytes = 0;
        d->exitCode = code;
        d->exitStatus = status;
        d->state = QPtyProcess::Stopped;
        QPointer<QPtyProcess> guard = QPointer<QPtyProcess>(d->q);
        Q_EMIT d->q->stateChanged(QPtyProcess::Stopped);
        if (guard) {
            Q_EMIT guard->finished(code, status);
        }
    });
}

QPtyProcess::QPtyProcess(QObject *parent) : QObject(parent), d_ptr(new QPtyProcessPrivate(this)) {
    qRegisterMetaType<QPtyProcess::ProcessState>();
    qRegisterMetaType<QPtyProcess::ProcessError>();
    qRegisterMetaType<QPtyProcess::ExitStatus>();
    qRegisterMetaType<QPtyProcess::Backend>();
}

QPtyProcess::~QPtyProcess() = default;

void QPtyProcess::setProgram(const QString &program) {
    Q_D(QPtyProcess);
    d->program = program;
}

QString QPtyProcess::program() const {
    Q_D(const QPtyProcess);
    return d->program;
}

void QPtyProcess::setArguments(const QStringList &arguments) {
    Q_D(QPtyProcess);
    d->arguments = arguments;
}

QStringList QPtyProcess::arguments() const {
    Q_D(const QPtyProcess);
    return d->arguments;
}

void QPtyProcess::setWorkingDirectory(const QString &directory) {
    Q_D(QPtyProcess);
    d->directory = directory;
}

QString QPtyProcess::workingDirectory() const {
    Q_D(const QPtyProcess);
    return d->directory;
}

void QPtyProcess::setProcessEnvironment(const QProcessEnvironment &environment) {
    Q_D(QPtyProcess);
    d->environment = environment;
    d->customEnvironment = true;
}

QProcessEnvironment QPtyProcess::processEnvironment() const {
    Q_D(const QPtyProcess);
    return d->customEnvironment ? d->environment : QProcessEnvironment::systemEnvironment();
}

void QPtyProcess::setBackend(Backend backend) {
    Q_D(QPtyProcess);
    d->preference = backend;
}

QPtyProcess::Backend QPtyProcess::backend() const {
    Q_D(const QPtyProcess);
    return d->preference;
}

QPtyProcess::Backend QPtyProcess::activeBackend() const {
    Q_D(const QPtyProcess);
    return d->active;
}

QPtyProcess::ProcessState QPtyProcess::state() const {
    Q_D(const QPtyProcess);
    return d->state;
}

qint64 QPtyProcess::processId() const {
    Q_D(const QPtyProcess);
    return d->pid;
}

int QPtyProcess::exitCode() const {
    Q_D(const QPtyProcess);
    return d->exitCode;
}

QPtyProcess::ExitStatus QPtyProcess::exitStatus() const {
    Q_D(const QPtyProcess);
    return d->exitStatus;
}

QPtyProcess::ProcessError QPtyProcess::error() const {
    Q_D(const QPtyProcess);
    return d->error;
}

QString QPtyProcess::errorString() const {
    Q_D(const QPtyProcess);
    return d->errorText;
}

QSize QPtyProcess::terminalSize() const {
    Q_D(const QPtyProcess);
    return d->size;
}

qint64 QPtyProcess::bytesAvailable() const {
    Q_D(const QPtyProcess);
    return d->received.size();
}

qint64 QPtyProcess::bytesToWrite() const {
    Q_D(const QPtyProcess);
    return d->queuedBytes;
}

QByteArray QPtyProcess::readAll() {
    Q_D(QPtyProcess);
    return std::exchange(d->received, {});
}

void QPtyProcess::start() {
    Q_D(QPtyProcess);
    if (d->state != NotRunning) {
        return;
    }
    d->error = NoError;
    d->errorText.clear();
    QPtyProcessOptions options = {d->program,
                                  d->arguments,
                                  QDir(d->directory.isEmpty() ? QDir::currentPath() : d->directory).absolutePath(),
                                  processEnvironment(),
                                  d->size,
                                  d->preference};
    // TERM 只在缺省时补充，尊重调用方明确选择的终端能力。
    if (!options.environment.contains(QStringLiteral("TERM"))) {
        options.environment.insert(QStringLiteral("TERM"), QStringLiteral("xterm-256color"));
    }
    QString invalid;
    if (options.program.isEmpty() || options.program.contains(QChar::Null) || options.directory.contains(QChar::Null)) {
        invalid = QStringLiteral("Program and working directory must not contain NUL; program must not be empty");
    }
    for (const auto &arg : options.arguments) {
        if (arg.contains(QChar::Null)) {
            invalid = QStringLiteral("Arguments must not contain NUL");
        }
    }
    for (const auto &entry : options.environment.toStringList()) {
        if (entry.contains(QChar::Null)) {
            invalid = QStringLiteral("Environment must not contain NUL");
        }
    }
    d->state = Starting;
    QPointer<QPtyProcess> guard = QPointer<QPtyProcess>(this);
    // 在发出 Starting 前建立后端，使 stateChanged 槽中的 kill() 也能被记录。
    if (invalid.isEmpty()) {
        d->backend = createPtyBackend(d, std::move(options));
    }
    Q_EMIT stateChanged(Starting);
    if (!guard) {
        return;
    }
    if (!invalid.isEmpty()) {
        d->post([d, invalid] {
            d->state = Stopped;
            QPointer<QPtyProcess> alive = QPointer<QPtyProcess>(d->q);
            Q_EMIT d->q->stateChanged(Stopped);
            if (alive) {
                d->reportError(FailedToStart, invalid);
            }
        });
        return;
    }
    try {
        d->backend->launch();
    } catch (const std::exception &e) {
        d->backend.reset();
        d->state = Stopped;
        Q_EMIT stateChanged(Stopped);
        if (guard) {
            d->reportError(FailedToStart, QString::fromLocal8Bit(e.what()));
        }
    }
}

qint64 QPtyProcess::write(const QByteArray &data) {
    Q_D(QPtyProcess);
    if (d->state != Running || !d->backend) {
        d->reportError(WriteError, QStringLiteral("PTY process is not running"));
        return -1;
    }
    if (data.size() > KMaximumQueuedBytes - d->queuedBytes) {
        d->reportError(WriteError, QStringLiteral("PTY input queue exceeds 4 MiB"));
        return -1;
    }
    d->queuedBytes += data.size();
    d->backend->enqueue(data);
    return data.size();
}

bool QPtyProcess::setTerminalSize(const QSize &size) {
    Q_D(QPtyProcess);
    if (size.width() < 1 || size.height() < 1 || size.width() > 32767 || size.height() > 32767) {
        d->reportError(ResizeError, QStringLiteral("PTY columns and rows must be in 1..32767"));
        return false;
    }
    d->size = size;
    if (d->backend) {
        d->backend->resize(size);
    }
    return true;
}

void QPtyProcess::terminate() {
    Q_D(QPtyProcess);
    if (d->backend) {
        d->backend->stop(false);
    } else if (d->state == NotRunning) {
        d->state = Stopped;
        Q_EMIT stateChanged(Stopped);
    }
}

void QPtyProcess::kill() {
    Q_D(QPtyProcess);
    if (d->backend) {
        d->backend->stop(true);
    } else if (d->state == NotRunning) {
        d->state = Stopped;
        Q_EMIT stateChanged(Stopped);
    }
}
