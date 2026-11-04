#include <QPointer>
#include <QTimer>
#include <chrono>
#include <map>
#include <optional>
#include <qvfs/private/qvirtualbackend_p.h>
#include <qvfs/qvirtualtransferjob.h>

namespace {
    std::mutex budgetMutex;
    qint64 bufferLimit = 256 * 1024 * 1024, buffersUsed = 0;

    qint64 nowMs() {
        return std::chrono::duration_cast<std::chrono::milliseconds>(
                   std::chrono::steady_clock::now().time_since_epoch())
            .count();
    }
} // namespace

namespace qvfs {
    /** 后台复制协调器；内容状态只在 SerialQueue 访问，取消/时限为独立原子量。 */
    struct Transfer : std::enable_shared_from_this<Transfer> {
        struct Chunk {
            quint64 offset;
            quint32 capacity;
            QByteArray data;
        }; ///< 预算覆盖读到最终写入确认。

        std::shared_ptr<SerialQueue> queue = std::make_shared<SerialQueue>(); ///< 与 GUI 无关的协调队列。
        std::shared_ptr<Backend> source, target;                              ///< 路径后端快照。
        std::shared_ptr<Handle> input, output;                                ///< 打开的后台句柄。
        ControlPtr control = std::make_shared<Control>();                     ///< 用户取消和错误后的停发令牌。
        std::shared_ptr<void> lease;                                          ///< 全局预算租约，所有在途块结束后释放。
        QVirtualTransferOptions options;                                      ///< 配置快照。
        std::function<void(qint64, qint64, QVirtualTransferJob::Phase)> progress; ///< 合并 Qt 快照的通知器。
        Callback finished;                                        ///< 最终结果通知器，不持有前端 QObject。
        std::atomic<qint64> lastProgress = nowMs();               ///< 最近实际进展时间。
        std::atomic_bool timedOut = false;                        ///< GUI 空闲计时器设置，只停止补发。
        Result error;                                             ///< 首个错误，保留后续回收结果的优先级。
        qint64 total = -1, completed = 0, reserved = 0;           ///< 总量、目标确认量和保留预算。
        quint64 next = 0, writeOffset = 0;                        ///< 下次预读及顺序目标写入偏移。
        int active = 0;                                           ///< 读、就绪或写阶段的块总数。
        bool writing = false, closing = false, sizeKnown = false; ///< 写屏障、回收及初始大小有效性。
        std::map<quint64, std::shared_ptr<Chunk>> ready;          ///< 顺序目标的乱序缓存，也占 reserved。

        /** 将 result 移交本任务队列后执行 next，后端线程不访问协调状态。 */
        template <class F> Callback then(F next) {
            return [self = shared_from_this(), next = std::move(next)](Result result) mutable {
                self->queue->post([self, next = std::move(next), result = std::move(result)]() mutable {
                    next(*self, std::move(result));
                });
            };
        }

        void touch() {
            lastProgress = nowMs();
        }

        void fail(Result result) {
            if (timedOut && result.error == QVirtualJob::OperationCancelled) {
                result = failure(QVirtualJob::Timeout, QStringLiteral("Transfer idle timeout"));
            }
            if (error.error == QVirtualJob::NoError) {
                error = std::move(result);
            }
            control->cancelled = true;
        }

        void begin() {
            if (control->cancelled) {
                finishResult(failure(QVirtualJob::OperationCancelled, QStringLiteral("Transfer cancelled")));
                return;
            }
            source->verifyCopyTarget(target, control, then([](Transfer &s, Result result) {
                                         if (result.error != QVirtualJob::NoError) {
                                             s.finishResult(std::move(result));
                                             return;
                                         }
                                         s.openSource();
                                     }));
        }

        void openSource() {
            source->open({}, control, then([](Transfer &s, Result result) {
                             if (result.error != QVirtualJob::NoError) {
                                 s.finishResult(std::move(result));
                                 return;
                             }
                             s.input = std::move(result.handle);
                             s.total = result.size;
                             s.sizeKnown = result.size >= 0;
                             s.touch();
                             if (s.control->cancelled) {
                                 s.pump();
                                 return;
                             }
                             QVirtualOpenOptions o;
                             o.access = QVirtualOpenOptions::WriteOnly;
                             o.creation = s.options.overwrite ? QVirtualOpenOptions::Replace
                                                              : QVirtualOpenOptions::CreateNew;
                             o.atomicReplace = s.options.atomicReplace;
                             s.target->open(o, s.control, s.then([](Transfer &s, Result result) {
                                 if (result.error != QVirtualJob::NoError) {
                                     s.fail(std::move(result));
                                 } else {
                                     s.output = std::move(result.handle);
                                     s.touch();
                                 }
                                 s.progress(s.completed, s.total, QVirtualTransferJob::Transferring);
                                 s.pump();
                             }));
                         }));
        }

        void read(const std::shared_ptr<Chunk> &chunk) {
            const auto offset = chunk->offset + quint64(chunk->data.size());
            input->read(
                offset, chunk->capacity - chunk->data.size(), control, then([chunk](Transfer &s, Result result) {
                    if (result.error != QVirtualJob::NoError) {
                        if (!s.control->cancelled || s.error.error == QVirtualJob::NoError) {
                            s.fail(std::move(result));
                        }
                        s.release(chunk);
                        s.pump();
                        return;
                    }
                    if (s.control->cancelled) {
                        s.release(chunk);
                        s.pump();
                        return;
                    }
                    if (result.data.size() > int(chunk->capacity) - chunk->data.size() ||
                        (result.data.isEmpty() && !result.eof)) {
                        s.fail(failure(QVirtualJob::IoError, QStringLiteral("Invalid or empty read without EOF")));
                        s.release(chunk);
                        s.pump();
                        return;
                    }
                    chunk->data.append(result.data);
                    s.touch();
                    if (result.eof && chunk->data.size() < int(chunk->capacity)) {
                        const auto end = chunk->offset + quint64(chunk->data.size());
                        if (s.sizeKnown && end < quint64(s.total)) {
                            s.fail(
                                failure(QVirtualJob::IoError, QStringLiteral("Source ended before its initial size")));
                            s.release(chunk);
                            s.pump();
                            return;
                        }
                        if (!s.sizeKnown) {
                            s.total = s.total < 0 ? static_cast<qint64>(end) : qMin(s.total, static_cast<qint64>(end));
                        }
                    }
                    if (!result.eof && chunk->data.size() < int(chunk->capacity)) {
                        s.read(chunk);
                        return;
                    }
                    if (chunk->data.isEmpty()) {
                        s.release(chunk);
                    } else {
                        s.ready.emplace(chunk->offset, chunk);
                    }
                    s.pump();
                }));
        }

        void release(const std::shared_ptr<Chunk> &chunk) {
            --active;
            reserved -= chunk->capacity;
        }

        void write(const std::shared_ptr<Chunk> &chunk) {
            if (!output->randomWrite()) {
                writing = true;
            }
            output->write(chunk->offset, chunk->data, control, then([chunk](Transfer &s, Result result) {
                              s.writing = false;
                              if (result.error != QVirtualJob::NoError) {
                                  s.fail(std::move(result));
                              } else {
                                  s.completed += chunk->data.size();
                                  s.writeOffset += chunk->data.size();
                                  s.touch();
                                  s.progress(s.completed, s.total, QVirtualTransferJob::Transferring);
                              }
                              s.release(chunk);
                              s.pump();
                          }));
        }

        void pump() {
            if (closing) {
                return;
            }
            if (control->cancelled) {
                for (const auto &[offset, chunk] : ready) {
                    release(chunk);
                }
                ready.clear();
            } else if (output) {
                while (!ready.empty() && (output->randomWrite() || !writing)) {
                    auto entry = output->randomWrite() ? ready.begin() : ready.find(writeOffset);
                    if (entry == ready.end()) {
                        break;
                    }
                    auto chunk = std::move(entry->second);
                    ready.erase(entry);
                    write(chunk);
                }
                while (reserved + 65536 <= options.bufferSize && (total < 0 || next < quint64(total))) {
                    const auto count = total < 0 ? 65536u : quint32(qMin<quint64>(65536, quint64(total) - next));
                    auto chunk = std::make_shared<Chunk>(Chunk{next, count, {}});
                    ++active;
                    reserved += count;
                    next += count;
                    read(chunk);
                }
            }
            if (active || (!control->cancelled && output && (total < 0 || next < quint64(total)))) {
                return;
            }
            closing = true;
            const bool commit = output && !control->cancelled && error.error == QVirtualJob::NoError;
            if (commit) {
                progress(completed, total, QVirtualTransferJob::Committing);
            }
            if (!commit && error.error == QVirtualJob::NoError) {
                error = failure(timedOut ? QVirtualJob::Timeout : QVirtualJob::OperationCancelled,
                                timedOut ? QStringLiteral("Transfer idle timeout")
                                         : QStringLiteral("Transfer cancelled"));
            }
            const auto closeInput = [](Transfer &s, Result result) {
                if (result.error != QVirtualJob::NoError && s.error.error == QVirtualJob::NoError) {
                    s.error = std::move(result);
                }
                s.output.reset();
                if (!s.input) {
                    s.finishResult(s.error);
                    return;
                }
                s.input->close(false, s.then([](Transfer &s, Result result) {
                    if (result.error != QVirtualJob::NoError && s.error.error == QVirtualJob::NoError) {
                        s.error = std::move(result);
                    }
                    s.input.reset();
                    s.finishResult(s.error);
                }));
            };
            if (output) {
                output->close(commit, then(closeInput));
            } else {
                closeInput(*this, {});
            }
        }

        void finishResult(Result result) {
            lease.reset();
            finished(std::move(result));
        }
    };
} // namespace qvfs

QVirtualTransferJob::QVirtualTransferJob(QObject *parent) : QVirtualJob(parent) {}

QVirtualTransferJob::~QVirtualTransferJob() {
    if (operation) {
        operation->control->cancelled = true;
    }
}

qint64 QVirtualTransferJob::bytesTransferred() const {
    return completed;
}

qint64 QVirtualTransferJob::totalBytes() const {
    return total;
}

QVirtualTransferJob::Phase QVirtualTransferJob::phase() const {
    return currentPhase;
}

void QVirtualTransferJob::requestCancel() {
    if (operation) {
        operation->control->cancelled = true;
    } else {
        QVirtualJob::requestCancel();
    }
}

bool QVirtualTransferJob::setGlobalBufferLimit(qint64 limit) {
    if (limit < 65536) {
        return false;
    }
    std::lock_guard lock(budgetMutex);
    bufferLimit = limit;
    return true;
}

qint64 QVirtualTransferJob::globalBufferLimit() {
    std::lock_guard lock(budgetMutex);
    return bufferLimit;
}

std::shared_ptr<void> QVirtualTransferJob::reserveBuffer(const QVirtualTransferOptions &options) {
    std::lock_guard lock(budgetMutex);
    if (options.bufferSize < 65536 || options.bufferSize > 64 * 1024 * 1024 ||
        options.bufferSize > bufferLimit - buffersUsed) {
        return {};
    }
    buffersUsed += options.bufferSize;
    return std::shared_ptr<void>(new char, [size = options.bufferSize](void *p) {
        delete static_cast<char *>(p);
        std::lock_guard lock(budgetMutex);
        buffersUsed -= size;
    });
}

void QVirtualTransferJob::report(qint64 count, qint64 size, Phase phase) {
    if (isFinished()) {
        return;
    }
    completed = count;
    total = size;
    QPointer<QVirtualTransferJob> guard(this);
    if (currentPhase != phase) {
        currentPhase = phase;
        Q_EMIT phaseChanged(phase);
    }
    if (guard) {
        Q_EMIT progress(count, size);
    }
}

void QVirtualTransferJob::start(std::shared_ptr<qvfs::Backend> source, std::shared_ptr<qvfs::Backend> target,
                                QVirtualTransferOptions options) {
    using namespace qvfs;
    const auto ctx = context();
    operation = std::make_shared<Transfer>();
    operation->source = std::move(source);
    operation->target = std::move(target);
    operation->options = options;

    struct Snapshot {
        std::mutex mutex;
        qint64 count = 0, size = -1;
        Phase phase = Preparing;
        bool queued = false;
    };

    auto snapshot = std::make_shared<Snapshot>();
    operation->progress = [ctx, snapshot](qint64 count, qint64 size, Phase phase) {
        std::lock_guard lock(snapshot->mutex);
        snapshot->count = count;
        snapshot->size = size;
        snapshot->phase = phase;
        if (snapshot->queued) {
            return;
        }
        snapshot->queued = true;
        deliver(ctx, [snapshot](QVirtualJob *base) {
            qint64 count, size;
            Phase phase;
            {
                std::lock_guard lock(snapshot->mutex);
                snapshot->queued = false;
                count = snapshot->count;
                size = snapshot->size;
                phase = snapshot->phase;
            }
            static_cast<QVirtualTransferJob *>(base)->report(count, size, phase);
        });
    };
    operation->finished = [ctx](Result result) {
        deliver(ctx, [result = std::move(result)](QVirtualJob *base) {
            static_cast<QVirtualTransferJob *>(base)->finish(result.error, result.message, result.nativeError);
        });
    };
    if (options.bufferSize < 65536 || options.bufferSize > 64 * 1024 * 1024 || options.idleTimeoutMs <= 0 ||
        (options.atomicReplace && !options.overwrite)) {
        operation->finished(failure(InvalidArgument, QStringLiteral("Invalid transfer options")));
        return;
    }
    operation->lease = reserveBuffer(options);
    if (!operation->lease) {
        operation->finished(failure(ResourceLimit, QStringLiteral("Global transfer buffer budget exhausted")));
        return;
    }
    auto *timer = new QTimer(this);
    timer->setInterval(qMin(1000, qMax(1, options.idleTimeoutMs / 2)));
    connect(timer, &QTimer::timeout, this, [this, timeout = options.idleTimeoutMs, timer] {
        if (isFinished()) {
            timer->stop();
            return;
        }
        if (nowMs() - operation->lastProgress.load() >= timeout) {
            operation->timedOut = true;
            operation->control->cancelled = true;
        }
    });
    timer->start();
    deliver(ctx, [](QVirtualJob *base) {
        auto op = static_cast<QVirtualTransferJob *>(base)->operation;
        op->queue->post([op] { op->begin(); });
    });
}
