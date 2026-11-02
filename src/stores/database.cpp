#include "database.h"

#include "qguiapplication.h"

#include <QApplication>
#include <spdlog/spdlog.h>

namespace {
    constexpr int kDatabaseVersion_1 = 1;
    constexpr int kDatabaseVersion = kDatabaseVersion_1;

    constexpr auto kDatabaseVersion_1_SQL = R"(
        CREATE TABLE "tb_host" (
          "id" TEXT,
          "name" TEXT,
          "protocol" BLOB,
          "host" TEXT,
          "port" INTEGER,
          "username" TEXT,
          "remark" TEXT,
          "authentication" TEXT,
          "proxy" TEXT,
          "options" TEXT,
          "sort" INTEGER,
          "parentId" TEXT,
          "ownerId" TEXT,
          "creatorId" TEXT,
          "version" INTEGER,
          "createdAt" INTEGER,
          "updatedAt" INTEGER,
          PRIMARY KEY ("id")
        );
)";
} // namespace

Database::Database(const QString &filepath) {
    m_database = std::make_unique<SQLite::Database>(filepath.toStdString(),
                                                    SQLite::OPEN_READWRITE | SQLite::OPEN_CREATE);
    m_database->exec("PRAGMA journal_mode = WAL;");
    m_database->exec("PRAGMA synchronous = NORMAL;");
    m_database->exec("PRAGMA foreign_keys = ON;");
    m_database->exec("PRAGMA busy_timeout = 5000;");

    const int version = std::max(m_database->execAndGet("PRAGMA user_version;").getInt(), 0);
    migrate(version, kDatabaseVersion);

    m_worker.moveToThread(&m_thread);
    m_thread.start();
}

Database *Database::instance() {
    Q_ASSERT(qApp->thread() == QThread::currentThread());
    static Database *m_database = nullptr;
    if (m_database == nullptr) {
        m_database = new Database("test");
        QObject::connect(qApp, &QApplication::destroyed, qApp, [] { delete m_database; });
    }
    return m_database;
}

Database::~Database() {
    m_thread.quit();
    m_thread.wait();
}

void Database::transaction(const std::function<void(SQLite::Database &)> &operation) {
    std::exception_ptr exception;
    QMetaObject::invokeMethod(
        &m_worker,
        [this, operation, &exception] {
            try {
                SQLite::Transaction transaction(*m_database);
                operation(*m_database);
                transaction.commit();
            } catch (...) {
                exception = std::current_exception();
            }
        },
        Qt::BlockingQueuedConnection);

    if (exception) {
        std::rethrow_exception(exception);
    }
}

void Database::migrate(const int old_version, int new_version) const {
    if (new_version == old_version) {
        return;
    }

    if (old_version < kDatabaseVersion_1) {
        m_database->exec(kDatabaseVersion_1_SQL);
        if (spdlog::get_level() <= spdlog::level::debug) {
            spdlog::debug("exec sql:\n{}", kDatabaseVersion_1_SQL);
        }
    }

    m_database->exec("PRAGMA user_version = " + std::to_string(new_version) + ";");

    if (spdlog::get_level() <= spdlog::level::debug) {
        spdlog::debug("set sqlite user_version = {}", new_version);
    }
}
