#include "applicationstyle.h"
#include "mainwindow.h"
#include "windowsmenu.h"

#include <QApplication>
#include <QFile>
#include <QPalette>
#include <QStyleFactory>
#include <spdlog/spdlog.h>

int main(int argc, char *argv[]) {

#ifdef QT_DEBUG
    spdlog::set_level(spdlog::level::debug);
#else
    spdlog::set_level(spdlog::level::info);
#endif

#if QT_VERSION < QT_VERSION_CHECK(6, 0, 0)
    QApplication::setAttribute(Qt::AA_EnableHighDpiScaling);
    QApplication::setAttribute(Qt::AA_UseHighDpiPixmaps);
#endif

#if QT_VERSION >= QT_VERSION_CHECK(5, 14, 0)
    QApplication::setHighDpiScaleFactorRoundingPolicy(Qt::HighDpiScaleFactorRoundingPolicy::PassThrough);
#endif

    QApplication::setAttribute(Qt::AA_DontCreateNativeWidgetSiblings);

    QApplication app(argc, argv);
    QApplication::setStyle(new ApplicationStyle(QStyleFactory::create("Fusion")));

    app.installEventFilter(new WindowsMenu(&app));

    // Termora Light（FlatLaf Light）的配色，控件外观继续由 QSS 实现。
    auto palette = QApplication::palette();
    palette.setColor(QPalette::Window, QColor(0xF2F2F2));
    palette.setColor(QPalette::WindowText, Qt::black);
    palette.setColor(QPalette::Base, Qt::white);
    palette.setColor(QPalette::Text, Qt::black);
    palette.setColor(QPalette::Button, Qt::white);
    palette.setColor(QPalette::ButtonText, Qt::black);
    palette.setColor(QPalette::BrightText, QColor(0x666666));
    palette.setColor(QPalette::Light, QColor(0xF2F2F2));
    palette.setColor(QPalette::Midlight, QColor(0xCECECE));
    palette.setColor(QPalette::Mid, QColor(0xC2C2C2));
    palette.setColor(QPalette::Dark, QColor(0x808080));
    palette.setColor(QPalette::Shadow, QColor(0x666666));
    palette.setColor(QPalette::AlternateBase, QColor(0xE6E6E6));
    palette.setColor(QPalette::Highlight, QColor(0x2675BF));
    palette.setColor(QPalette::HighlightedText, Qt::white);

    // QSS 用这两个强调色角色区分默认按钮边框和输入框焦点边框。
    palette.setColor(QPalette::Link, QColor(0x4E9DE7));
    palette.setColor(QPalette::LinkVisited, QColor(0x89B0D4));
    palette.setColor(QPalette::PlaceholderText, QColor(0x808080));
    palette.setColor(QPalette::Inactive, QPalette::Highlight, QColor(0xD3D3D3));
    palette.setColor(QPalette::Inactive, QPalette::HighlightedText, Qt::black);

    // 原生控件使用 Disabled 组，与 QSS 禁用样式保持一致。
    palette.setColor(QPalette::Disabled, QPalette::Base, palette.color(QPalette::Light));
    palette.setColor(QPalette::Disabled, QPalette::Button, palette.color(QPalette::Light));
    palette.setColor(QPalette::Disabled, QPalette::Text, palette.color(QPalette::Dark));
    palette.setColor(QPalette::Disabled, QPalette::WindowText, palette.color(QPalette::Dark));
    palette.setColor(QPalette::Disabled, QPalette::ButtonText, palette.color(QPalette::Dark));
    palette.setColor(QPalette::Disabled, QPalette::PlaceholderText, palette.color(QPalette::Dark));
    palette.setColor(QPalette::Disabled, QPalette::Mid, palette.color(QPalette::Midlight));
    palette.setColor(QPalette::Disabled, QPalette::Highlight, palette.color(QPalette::Midlight));
    palette.setColor(QPalette::Disabled, QPalette::HighlightedText, palette.color(QPalette::Dark));
    QApplication::setPalette(palette);

    // Set application style
    QStringList stylesheets = {":/styles/app.qss"};
#ifdef Q_OS_MAC
    stylesheets.append(":/styles/app.osx.qss");
#endif
#ifdef Q_OS_WIN
    stylesheets.append(":/styles/app.win.qss");
#endif
    for (const auto &filepath : stylesheets) {
        if (QFile file(filepath); file.open(QIODevice::ReadOnly | QIODevice::Text)) {
            app.setStyleSheet(app.styleSheet().append("\n").append(file.readAll()));
        }
    }

#if defined(Q_OS_WINDOWS) && QT_VERSION < QT_VERSION_CHECK(6, 0, 0)
    if (const auto *f = windowsMessageFont(); f != nullptr) {
        if (spdlog::get_level() <= spdlog::level::trace) {
            spdlog::trace("windowsMessageFont family: {}", f->family().toStdString());
            spdlog::trace("windowsMessageFont family size: {}", f->pointSizeF());
        }
        QFont font = QApplication::font();
        font.setPointSizeF(f->pointSizeF());
        font.setFamily(f->family());
        QApplication::setFont(font);
        delete f;
    }
#endif

    MainWindow window;
    window.show();

    return QApplication::exec();
}
