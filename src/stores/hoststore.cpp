#include "hoststore.h"

#include <QApplication>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <utility>

namespace {
    QJsonObject optionsToJson(const store::Options &options) {
        QJsonArray tunnelings;
        for (const auto &tunneling : options.tunnelings) {
            QString type = QStringLiteral("Local");
            if (tunneling.type == store::TunnelingType::Remote) {
                type = QStringLiteral("Remote");
            } else if (tunneling.type == store::TunnelingType::Dynamic) {
                type = QStringLiteral("Dynamic");
            }
            tunnelings.append(QJsonObject{
                {"name", tunneling.name},
                {"type", type},
                {"sourceHost", tunneling.sourceHost},
                {"sourcePort", tunneling.sourcePort},
                {"destinationHost", tunneling.destinationHost},
                {"destinationPort", tunneling.destinationPort},
            });
        }
        QJsonArray scripts;
        for (const auto &script : options.loginScripts) {
            scripts.append(QJsonObject{
                {"expect", script.expect},
                {"send", script.send},
                {"regex", script.regex},
                {"matchCase", script.matchCase},
            });
        }
        return {
            {"tunnelings", tunnelings},
            {"jumpHosts", QJsonArray::fromStringList(options.jumpHosts)},
            {"loginScripts", scripts},
            {"encoding", options.encoding},
            {"env", options.env},
            {"startupCommand", options.startupCommand},
            {"heartbeatInterval", options.heartbeatInterval},
            {"timeout", options.timeout},
            {"backspace", options.backspace},
            {"altModifier", options.altModifier},
            {"keywordHighlightSetId", options.keywordHighlightSetId},
            {"enableX11Forwarding", options.enableX11Forwarding},
            {"x11Forwarding", options.x11Forwarding},
            {"forwardAgent", options.forwardAgent},
            {"sftpDefaultDirectory", options.sftpDefaultDirectory},
        };
    }

    store::Options optionsFromJson(const QJsonObject &json) {
        store::Options options;
        for (const auto &value : json["tunnelings"].toArray()) {
            if (!value.isObject()) {
                continue;
            }
            const auto object = value.toObject();
            store::Tunneling tunneling;
            tunneling.name = object["name"].toString();
            if (object["type"].toString() == "Remote") {
                tunneling.type = store::TunnelingType::Remote;
            } else if (object["type"].toString() == "Dynamic") {
                tunneling.type = store::TunnelingType::Dynamic;
            }
            tunneling.sourceHost = object["sourceHost"].toString(tunneling.sourceHost);
            tunneling.sourcePort = object["sourcePort"].toInt(tunneling.sourcePort);
            tunneling.destinationHost = object["destinationHost"].toString(tunneling.destinationHost);
            tunneling.destinationPort = object["destinationPort"].toInt(tunneling.destinationPort);
            options.tunnelings.push_back(std::move(tunneling));
        }
        for (const auto &value : json["jumpHosts"].toArray()) {
            if (value.isString()) {
                options.jumpHosts.push_back(value.toString());
            }
        }
        for (const auto &value : json["loginScripts"].toArray()) {
            if (!value.isObject()) {
                continue;
            }
            const auto object = value.toObject();
            options.loginScripts.push_back({object["expect"].toString(), object["send"].toString(),
                                            object["regex"].toBool(), object["matchCase"].toBool()});
        }
        options.encoding = json["encoding"].toString(options.encoding);
        options.env = json["env"].toString();
        options.startupCommand = json["startupCommand"].toString();
        options.heartbeatInterval = json["heartbeatInterval"].toInt(options.heartbeatInterval);
        options.timeout = json["timeout"].toInt(options.timeout);
        options.backspace = json["backspace"].toString(options.backspace);
        options.altModifier = json["altModifier"].toString(options.altModifier);
        options.keywordHighlightSetId = json["keywordHighlightSetId"].toString(options.keywordHighlightSetId);
        options.enableX11Forwarding = json["enableX11Forwarding"].toBool();
        options.x11Forwarding = json["x11Forwarding"].toString(options.x11Forwarding);
        options.forwardAgent = json["forwardAgent"].toBool();
        options.sftpDefaultDirectory = json["sftpDefaultDirectory"].toString();
        return options;
    }

    store::AuthenticationType authenticationTypeFromName(const QString &type) {
        if (type == "Password") {
            return store::AuthenticationType::Password;
        }
        if (type == "PublicKey") {
            return store::AuthenticationType::PublicKey;
        }
        if (type == "SSHAgent") {
            return store::AuthenticationType::SSHAgent;
        }
        if (type == "KeyboardInteractive") {
            return store::AuthenticationType::KeyboardInteractive;
        }
        return store::AuthenticationType::No;
    }

    store::Host readHost(const SQLite::Statement &query) {
        store::Host host = {};
        host.id = QString::fromStdString(query.getColumn("id").getString());
        host.name = QString::fromStdString(query.getColumn("name").getString());
        host.protocol = QString::fromStdString(query.getColumn("protocol").getString());
        host.host = QString::fromStdString(query.getColumn("host").getString());
        host.port = query.getColumn("port").getInt();
        host.username = QString::fromStdString(query.getColumn("username").getString());
        host.remark = QString::fromStdString(query.getColumn("remark").getString());
        host.sort = query.getColumn("sort").getInt();
        host.parentId = QString::fromStdString(query.getColumn("parentId").getString());
        host.ownerId = QString::fromStdString(query.getColumn("ownerId").getString());
        host.creatorId = QString::fromStdString(query.getColumn("creatorId").getString());
        host.version = query.getColumn("version").getInt64();
        host.createdAt = query.getColumn("createdAt").getInt64();
        host.updatedAt = query.getColumn("updatedAt").getInt64();

        if (auto authentication = QJsonDocument::fromJson(
                QString::fromStdString(query.getColumn("authentication").getString()).toUtf8());
            authentication.isObject()) {
            host.authentication = {};
            host.authentication.type = authenticationTypeFromName(authentication["type"].toString());
            host.authentication.password = authentication["password"].toString();
        }

        if (auto proxy = QJsonDocument::fromJson(QString::fromStdString(query.getColumn("proxy").getString()).toUtf8());
            proxy.isObject()) {
            host.proxy = {};
            if (proxy["type"].toString() == "HTTP") {
                host.proxy.type = store::ProxyType::HTTP;
            } else if (proxy["type"].toString() == "SOCKS5") {
                host.proxy.type = store::ProxyType::SOCKS5;
            }
            host.proxy.host = proxy["host"].toString();
            host.proxy.port = proxy["port"].toInt();
            host.proxy.authenticationType = authenticationTypeFromName(proxy["authenticationType"].toString());
            host.proxy.username = proxy["username"].toString();
            host.proxy.password = proxy["password"].toString();
        }

        if (auto options = QJsonDocument::fromJson(
                QString::fromStdString(query.getColumn("options").getString()).toUtf8());
            options.isObject()) {
            host.options = optionsFromJson(options.object());
        }

        return host;
    }

    void bindHost(SQLite::Statement &query, const store::Host &host) {
        const auto authenticationTypeName = [](const store::AuthenticationType type) -> QString {
            switch (type) {
                case store::AuthenticationType::No:
                    return QStringLiteral("No");
                case store::AuthenticationType::Password:
                    return QStringLiteral("Password");
                case store::AuthenticationType::PublicKey:
                    return QStringLiteral("PublicKey");
                case store::AuthenticationType::SSHAgent:
                    return QStringLiteral("SSHAgent");
                case store::AuthenticationType::KeyboardInteractive:
                    return QStringLiteral("KeyboardInteractive");
            }
            return QStringLiteral("No");
        };

        QString proxyType = QStringLiteral("No");
        switch (host.proxy.type) {
            case store::ProxyType::No:
                break;
            case store::ProxyType::HTTP:
                proxyType = QStringLiteral("HTTP");
                break;
            case store::ProxyType::SOCKS5:
                proxyType = QStringLiteral("SOCKS5");
                break;
        }

        const QJsonObject authentication{
            {"type", authenticationTypeName(host.authentication.type)},
            {"password", host.authentication.password},
        };

        const QJsonObject proxy{
            {"type", proxyType},
            {"host", host.proxy.host},
            {"port", host.proxy.port},
            {"authenticationType", authenticationTypeName(host.proxy.authenticationType)},
            {"username", host.proxy.username},
            {"password", host.proxy.password},
        };

        query.bind(":id", host.id.toStdString());
        query.bind(":name", host.name.toStdString());
        query.bind(":protocol", host.protocol.toStdString());
        query.bind(":host", host.host.toStdString());
        query.bind(":port", host.port);
        query.bind(":username", host.username.toStdString());
        query.bind(":remark", host.remark.toStdString());
        query.bind(":authentication", QJsonDocument(authentication).toJson(QJsonDocument::Compact).toStdString());
        query.bind(":proxy", QJsonDocument(proxy).toJson(QJsonDocument::Compact).toStdString());
        query.bind(":options", QJsonDocument(optionsToJson(host.options)).toJson(QJsonDocument::Compact).toStdString());
        query.bind(":sort", host.sort);
        query.bind(":parentId", host.parentId.toStdString());
        query.bind(":ownerId", host.ownerId.toStdString());
        query.bind(":creatorId", host.creatorId.toStdString());
        query.bind(":version", host.version);
        query.bind(":createdAt", host.createdAt);
        query.bind(":updatedAt", host.updatedAt);
    }
} // namespace

HostStore::HostStore(Database *database, QObject *parent) : QObject(parent), m_database(database) {}

HostStore *HostStore::instance() {
    Q_ASSERT(qApp->thread() == QThread::currentThread());
    static HostStore *store = nullptr;
    if (store == nullptr) {
        store = new HostStore(Database::instance(), qApp);
    }
    return store;
}

std::vector<store::Host> HostStore::hosts() const {
    std::vector<store::Host> hosts;
    m_database->transaction([&hosts](const SQLite::Database &db) {
        SQLite::Statement query(db, "SELECT * FROM tb_host ORDER BY sort, id");
        while (query.executeStep()) {
            hosts.emplace_back(readHost(query));
        }
    });
    return hosts;
}

void HostStore::insert(const store::Host &host) {
    m_database->transaction([&host](const SQLite::Database &db) {
        SQLite::Statement query(db, R"(
            INSERT INTO tb_host (
                id, name, protocol, host, port, username, remark,
                authentication, proxy, options, sort, parentId, ownerId,
                creatorId, version, createdAt, updatedAt
            ) VALUES (
                :id, :name, :protocol, :host, :port, :username, :remark,
                :authentication, :proxy, :options, :sort, :parentId, :ownerId,
                :creatorId, :version, :createdAt, :updatedAt
            )
        )");
        bindHost(query, host);
        query.exec();
    });
    Q_EMIT hostInserted(host.id);
}

void HostStore::remove(const QString &id) {
    bool removed = false;
    m_database->transaction([&id, &removed](const SQLite::Database &db) {
        SQLite::Statement query(db, "DELETE FROM tb_host WHERE id = :id");
        query.bind(":id", id.toStdString());
        removed = query.exec() > 0;
    });
    if (removed) {
        Q_EMIT hostRemoved(id);
    }
}

void HostStore::update(const store::Host &host) {
    bool updated = false;
    m_database->transaction([&host, &updated](const SQLite::Database &db) {
        SQLite::Statement query(db, R"(
            UPDATE tb_host SET
                name = :name, protocol = :protocol, host = :host, port = :port,
                username = :username, remark = :remark, authentication = :authentication,
                proxy = :proxy, options = :options, sort = :sort, parentId = :parentId,
                ownerId = :ownerId, creatorId = :creatorId, version = :version,
                createdAt = :createdAt, updatedAt = :updatedAt
            WHERE id = :id
        )");
        bindHost(query, host);
        updated = query.exec() > 0;
    });
    if (updated) {
        Q_EMIT hostChanged(host.id);
    }
}

std::optional<store::Host> HostStore::host(const QString &id) const {
    std::optional<store::Host> host;
    m_database->transaction([&id, &host](const SQLite::Database &db) {
        SQLite::Statement query(db, "SELECT * FROM tb_host WHERE id = :id");
        query.bind(":id", id.toStdString());
        if (query.executeStep()) {
            host = readHost(query);
        }
    });
    return host;
}
