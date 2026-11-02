#pragma once

#include <QString>

template <typename T> class DataKey {
public:
    using ValueType = T;

    explicit DataKey(QString name) : m_name(std::move(name)) {}

    const QString &name() const noexcept {
        return m_name;
    }

private:
    QString m_name;
};
