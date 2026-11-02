#pragma once

#include <QThread>
#include <SQLiteCpp/SQLiteCpp.h>
#include <functional>
#include <memory>

class Database {
public:
    ~Database();

private:
    explicit Database(const QString &filepath);

public:
    static Database *instance();

public:
    void transaction(const std::function<void(SQLite::Database &)> &operation);

private:
    void migrate(int old_version, int new_version) const;

private:
    QThread m_thread;
    QObject m_worker;
    std::unique_ptr<SQLite::Database> m_database;
};
