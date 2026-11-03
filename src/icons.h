#pragma once

#include <QHash>
#include <QIcon>

namespace icons {

    inline QIcon get(const QString &name) {
        static QHash<QString, QIcon> cache;
        if (const auto it = cache.constFind(name); it != cache.cend()) {
            return it.value();
        }

        const QString path = QStringLiteral(":/icons/") + name;
        QIcon icon;
        icon.addFile(path + ".svg", {}, QIcon::Normal);
        icon.addFile(path + "_dark.svg", {}, QIcon::Selected);

        cache.insert(name, icon);

        return icon;
    }

    inline QIcon terminal() {
        return get(QStringLiteral("terminal"));
    }

    inline QIcon settings() {
        return get(QStringLiteral("settings"));
    }

    inline QIcon network() {
        return get(QStringLiteral("network"));
    }

    inline QIcon show_write_access() {
        return get(QStringLiteral("showWriteAccess"));
    }

    inline QIcon server() {
        return get(QStringLiteral("server"));
    }

    inline QIcon folder() {
        return get(QStringLiteral("folder"));
    }

    inline QIcon regex() {
        return get(QStringLiteral("regex"));
    }

    inline QIcon matchCase() {
        return get(QStringLiteral("matchCase"));
    }
} // namespace icons
