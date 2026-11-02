#pragma once

#include "database.h"
#include "entities.h"

#include <optional>
#include <vector>

class HostStore : public QObject {
    Q_OBJECT
private:
    explicit HostStore(Database *database, QObject *parent = nullptr);

public:
    static HostStore *instance();

public:
    std::vector<store::Host> hosts() const;
    void insert(const store::Host &host);
    void remove(const QString &id);
    void update(const store::Host &host);
    std::optional<store::Host> host(const QString &id) const;

Q_SIGNALS:
    void hostInserted(QString id);
    void hostRemoved(QString id);
    void hostChanged(QString id);

private:
    Database *m_database = nullptr;
};
