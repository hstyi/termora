#pragma once

#include "datakey.h"
#include "dataprovider.h"

#include <QPointer>
#include <QWidget>
#include <optional>

class DataContext {
public:
    explicit DataContext(QObject *source = nullptr) : m_source(source) {}

    template <typename T> std::optional<T> getData(const DataKey<T> &key) const {
        for (const auto *widget = m_source.data(); widget != nullptr; widget = widget->parent()) {
            const auto *provider = dynamic_cast<const DataProvider *>(widget);
            if (!provider) {
                continue;
            }
            if (auto value = provider->getData(key.name()); value.has_value()) {
                return std::any_cast<T>(value);
            }
        }
        return std::nullopt;
    }

public:
    static DataContext getDataContext(QWidget *widget) {
        return DataContext(widget);
    }

private:
    QPointer<QObject> m_source;
};
