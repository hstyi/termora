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

    auto palette = QApplication::palette();
    palette.setColor(QPalette::Window, QColor(0xf9f9f9));
    palette.setColor(QPalette::WindowText, QColor(0x212121));
    palette.setColor(QPalette::BrightText, QColor(0x6b6b6b));
    palette.setColor(QPalette::Light, QColor(0xededed));
    palette.setColor(QPalette::Midlight, QColor(0xe9e9e9));
    palette.setColor(QPalette::Mid, QColor(0xD0D0D0));
    palette.setColor(QPalette::PlaceholderText, QColor(0x6c707e));
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
