#pragma once

#include <QString>
#include <any>

class DataProvider {
public:
    virtual ~DataProvider() = default;
    virtual std::any getData(const QString &dataId) const = 0;
};
