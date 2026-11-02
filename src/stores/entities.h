#pragma once

#include <QString>
#include <QStringList>
#include <vector>

namespace store {
    enum class AuthenticationType {
        No,
        Password,
        PublicKey,
        SSHAgent,
        KeyboardInteractive,
    };

    enum class ProxyType {
        No,
        HTTP,
        SOCKS5,
    };

    struct Authentication {
        AuthenticationType type;
        QString password;
    };

    struct Proxy {
        ProxyType type;
        QString host;
        qint32 port;
        AuthenticationType authenticationType;
        QString username;
        QString password;
    };

    enum class TunnelingType {
        Local,
        Remote,
        Dynamic,
    };

    struct Tunneling {
        QString name;
        TunnelingType type = TunnelingType::Local;
        QString sourceHost = QStringLiteral("127.0.0.1");
        qint32 sourcePort = 1080;
        QString destinationHost = QStringLiteral("127.0.0.1");
        qint32 destinationPort = 22;
    };

    struct LoginScript {
        /**
         * 期待字符
         */
        QString expect;

        /**
         * 发送的字符
         */
        QString send;

        bool regex = false;

        /**
         * true：匹配大小写
         */
        bool matchCase = false;
    };

    struct Options {
        std::vector<Tunneling> tunnelings;
        QStringList jumpHosts;
        std::vector<LoginScript> loginScripts;
        QString encoding = QStringLiteral("UTF-8");
        QString env;
        QString startupCommand;
        qint32 heartbeatInterval = 30;
        qint32 timeout = 60;
        QString backspace = QStringLiteral("Delete");
        QString altModifier = QStringLiteral("EightBit");
        /**
         * 应用的高亮集
         */
        QString keywordHighlightSetId;
        bool enableX11Forwarding = false;
        QString x11Forwarding = QStringLiteral("localhost:0");
        bool forwardAgent = false;
        QString sftpDefaultDirectory;
    };

    struct Host {
        QString id;
        QString name;
        QString protocol;
        QString host;
        qint32 port;
        QString username;
        QString remark;
        Authentication authentication;
        Proxy proxy;
        Options options;
        int sort;
        QString parentId;
        QString ownerId;
        QString creatorId;
        qint64 version;
        qint64 createdAt;
        qint64 updatedAt;
    };
} // namespace store
