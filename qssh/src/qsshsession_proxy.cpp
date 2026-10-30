#include "qsshsession_p.h"

#include <QUrl>

asio::awaitable<bool> QSshSessionPrivate::doProxyWrite(QByteArray data) {
    const auto token = alive;
    int offset = 0;
    while (offset < data.size()) {
        asio::error_code error;
        const auto count = co_await socket.async_write_some(
            asio::buffer(data.constData() + offset, data.size() - offset),
            asio::redirect_error(asio::use_awaitable, error));
        if (!*token || terminated) {
            co_return false;
        }
        if (error || !count) {
            fail(QSshSession::NetworkError, QStringLiteral("Proxy connection write failed"));
            co_return false;
        }
        offset += count;
    }
    co_return true;
}

asio::awaitable<QByteArray> QSshSessionPrivate::doProxyRead(int count) {
    const auto token = alive;
    QByteArray data(count, '\0');
    int offset = 0;
    while (offset < count) {
        asio::error_code error;
        const auto received = co_await socket.async_read_some(asio::buffer(data.data() + offset, count - offset),
                                                              asio::redirect_error(asio::use_awaitable, error));
        if (!*token || terminated) {
            co_return QByteArray();
        }
        if (error || !received) {
            fail(QSshSession::NetworkError, QStringLiteral("Proxy closed before negotiation completed"));
            co_return QByteArray();
        }
        offset += received;
    }
    co_return data;
}

asio::awaitable<void> QSshSessionPrivate::doProxyHandshake() {
    const auto token = alive;
    const auto proxy = options.proxy;
    const QByteArray host = QUrl::toAce(options.host);
    const quint16 port = options.port;
    if (proxy.type == QSshSession::HttpConnectProxy) {
        QByteArray authority = options.host.contains(':') ? "[" + options.host.toUtf8() + "]" : host;
        authority += ':' + QByteArray::number(port);
        QByteArray request = "CONNECT " + authority + " HTTP/1.1\r\nHost: " + authority + "\r\n";
        if (!proxy.username.isEmpty()) {
            request += "Proxy-Authorization: Basic " +
                       (proxy.username.toUtf8() + ':' + proxy.password.toUtf8()).toBase64() + "\r\n";
        }
        request += "\r\n";
        if (!co_await doProxyWrite(request)) {
            co_return;
        }
        QByteArray response;
        // 逐字节读到头结束，不能让 HTTP 解析器提前吞掉 SSH banner。
        while (response.size() < 16384 && !response.endsWith("\r\n\r\n")) {
            const auto byte = co_await doProxyRead(1);
            if (!*token || terminated) {
                co_return;
            }
            response += byte;
        }
        const auto status = response.left(response.indexOf("\r\n")).split(' ');
        if (!response.endsWith("\r\n\r\n") || status.size() < 2 || !status[0].startsWith("HTTP/1.") ||
            status[1] != "200") {
            fail(QSshSession::NetworkError, QStringLiteral("HTTP CONNECT proxy rejected the connection"));
        }
        co_return;
    }

    QByteArray request;
    if (proxy.type == QSshSession::Socks4Proxy) {
        request.append('\x04').append('\x01');
        request.append(char(port >> 8)).append(char(port));
        asio::error_code error;
        const auto address = asio::ip::make_address_v4(options.host.toStdString(), error);
        if (!error) {
            const auto bytes = address.to_bytes();
            request.append(reinterpret_cast<const char *>(bytes.data()), bytes.size());
        } else {
            request.append(QByteArray("\0\0\0\1", 4));
        }
        request += proxy.username.toUtf8() + '\0';
        if (error) {
            request += host + '\0'; // SOCKS4a 将 DNS 解析交给代理。
        }
        if (!co_await doProxyWrite(request)) {
            co_return;
        }
        const auto reply = co_await doProxyRead(8);
        if (!*token || terminated) {
            co_return;
        }
        if (reply.size() != 8 || quint8(reply[0]) != 0 || quint8(reply[1]) != 90) {
            fail(QSshSession::NetworkError, QStringLiteral("SOCKS4 proxy rejected the connection"));
        }
        co_return;
    }

    const bool authenticate = !proxy.username.isEmpty();
    if (!co_await doProxyWrite(authenticate ? QByteArray("\5\1\2", 3) : QByteArray("\5\1\0", 3))) {
        co_return;
    }
    auto reply = co_await doProxyRead(2);
    if (!*token || terminated) {
        co_return;
    }
    if (reply.size() != 2 || quint8(reply[0]) != 5 || quint8(reply[1]) != (authenticate ? 2 : 0)) {
        fail(QSshSession::NetworkError, QStringLiteral("SOCKS5 proxy has no acceptable authentication method"));
        co_return;
    }
    if (authenticate) {
        const auto username = proxy.username.toUtf8();
        const auto password = proxy.password.toUtf8();
        request = QByteArray(1, '\1') + char(username.size()) + username + char(password.size()) + password;
        if (!co_await doProxyWrite(request)) {
            co_return;
        }
        reply = co_await doProxyRead(2);
        if (!*token || terminated) {
            co_return;
        }
        if (reply.size() != 2 || quint8(reply[0]) != 1 || quint8(reply[1]) != 0) {
            fail(QSshSession::NetworkError, QStringLiteral("SOCKS5 proxy authentication failed"));
            co_return;
        }
    }
    request = QByteArray("\5\1\0", 3);
    asio::error_code error;
    const auto address = asio::ip::make_address(options.host.toStdString(), error);
    if (!error && address.is_v4()) {
        const auto bytes = address.to_v4().to_bytes();
        request += '\1';
        request.append(reinterpret_cast<const char *>(bytes.data()), bytes.size());
    } else if (!error && address.is_v6()) {
        const auto bytes = address.to_v6().to_bytes();
        request += '\4';
        request.append(reinterpret_cast<const char *>(bytes.data()), bytes.size());
    } else {
        if (host.isEmpty() || host.size() > 255) {
            fail(QSshSession::InvalidConfiguration, QStringLiteral("Invalid SOCKS5 destination hostname"));
            co_return;
        }
        request += '\3';
        request += char(host.size());
        request += host;
    }
    request.append(char(port >> 8)).append(char(port));
    if (!co_await doProxyWrite(request)) {
        co_return;
    }
    reply = co_await doProxyRead(4);
    if (!*token || terminated) {
        co_return;
    }
    if (reply.size() != 4 || quint8(reply[0]) != 5 || quint8(reply[1]) != 0 || quint8(reply[2]) != 0) {
        fail(QSshSession::NetworkError, QStringLiteral("SOCKS5 proxy rejected the connection"));
        co_return;
    }
    int length = quint8(reply[3]) == 1 ? 4 : quint8(reply[3]) == 4 ? 16 : -1;
    if (quint8(reply[3]) == 3) {
        reply = co_await doProxyRead(1);
        if (!*token || terminated) {
            co_return;
        }
        length = quint8(reply[0]);
    }
    if (length < 0) {
        fail(QSshSession::NetworkError, QStringLiteral("Invalid SOCKS5 proxy response"));
        co_return;
    }
    co_await doProxyRead(length + 2);
}
