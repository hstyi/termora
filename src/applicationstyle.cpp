#include "applicationstyle.h"

#include <QWidget>
#include <spdlog/spdlog.h>

ApplicationStyle::ApplicationStyle(QStyle *style) : QProxyStyle(style) {}

int ApplicationStyle::styleHint(StyleHint hint, const QStyleOption *option, const QWidget *widget,
                                QStyleHintReturn *returnData) const {
    switch (hint) {
        /*
        // 子菜单弹出延迟 (ms)
        case QStyle::SH_Menu_SubMenuPopupDelay:
            return 100;

        // 斜向移动到子菜单时的容忍，防止鼠标轻微偏移就切换菜单项
        case QStyle::SH_Menu_SloppySubMenus:
            return 1;

        // 离开后不要启动 sloppy 倒计时（Fusion 漏掉的，导致 3-4 秒延迟的元凶）
        case QStyle::SH_Menu_SubMenuDontStartSloppyOnLeave:
            return 1;

        // 只允许朝子菜单方向移动时才保持 sloppy，反方向立即切换
        case QStyle::SH_Menu_SubMenuUniDirection:
            return 1;

        // 朝错误方向移动几次后才取消 sloppy（配合 UniDirection）
        case QStyle::SH_Menu_SubMenuUniDirectionFailCount:
            return 1;

        // sloppy 超时时间 (ms)，超时后强制关闭子菜单
        case QStyle::SH_Menu_SubMenuSloppyCloseTimeout:
            return 300;

        // sloppy 选择超时 (ms)
        case QStyle::SH_Menu_SubMenuSloppySelectOtherActions:
            return 1;
*/

        // 悬停提示弹出延迟 (ms)
        case SH_ToolTip_WakeUpDelay:
            return 75;
        //  n 毫秒内切换 tooltip 基本立即显示
        case SH_ToolTip_FallAsleepDelay:
            return 1000;

        default:
            return QProxyStyle::styleHint(hint, option, widget, returnData);
    }
}

void ApplicationStyle::drawPrimitive(PrimitiveElement pe, const QStyleOption *opt, QPainter *p,
                                     const QWidget *w) const {
    QProxyStyle::drawPrimitive(pe, opt, p, w);
}

int ApplicationStyle::pixelMetric(const PixelMetric metric, const QStyleOption *option, const QWidget *widget) const {
    return QProxyStyle::pixelMetric(metric, option, widget);
}

ApplicationStyle::~ApplicationStyle() {
    if (spdlog::get_level() <= spdlog::level::trace) {
        spdlog::trace("ApplicationStyle::~ApplicationStyle()");
    }
}
