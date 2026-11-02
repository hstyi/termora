#include "macscrollstyle.h"

#include <QEvent>
#include <QGraphicsOpacityEffect>
#include <QHoverEvent>
#include <QPainter>
#include <QPointer>
#include <QPropertyAnimation>
#include <QProxyStyle>
#include <QScopedValueRollback>
#include <QScrollArea>
#include <QScrollBar>
#include <QStyleFactory>
#include <QStyleOptionSlider>
#include <QTimer>
#include <QVariantAnimation>
#include <QWidget>

namespace {
    // Qt 5.15 QMacStyle 的 regular overlay scroller 宽度。
    constexpr int kKnobWidth = 7;
#ifdef Q_OS_MAC
    constexpr int kScrollBarExtent = 15;
    constexpr int kExpandedKnobWidth = 11;
    constexpr int kKnobMargin = 0;
    // QMacStyle 原值为 450ms；这里按交互反馈延长可见时间。
    constexpr int kFadeOutDelay = 1200;
    constexpr int kFadeOutDuration = 200;
    constexpr auto kActiveOverlayScrollBarProperty = "macOSActiveOverlayScrollBar";
#else
    constexpr int kScrollBarExtent = kKnobWidth;
    constexpr int kKnobMargin = 0;
    // 轨道颜色越大越明显
    constexpr int kTrackAlpha = 0;
    constexpr auto kHoveredOverlayScrollBarProperty = "overlayScrollBarHovered";
    constexpr auto kOverlayScrollBarHoverProgressProperty = "overlayScrollBarHoverProgress";
#endif
    constexpr int kExpandDuration = 200;
    constexpr auto kMacOSOverlayScrollBarsInstalledProperty = "macOSOverlayScrollBarsInstalled";
    constexpr auto kMacOSOverlayScrollBarProperty = "macOSOverlayScrollBar";
    constexpr auto kReversedOverlayScrollBarProperty = "overlayScrollBarReversed";

    class OverlayScrollBarPositionController final : public QObject {
    public:
        OverlayScrollBarPositionController(QScrollBar *scrollBar, QAbstractScrollArea *scrollArea)
            : QObject(scrollArea),
              m_scrollBar(scrollBar),
              m_scrollArea(scrollArea),
              m_container(scrollBar->parentWidget()) {
            scrollBar->installEventFilter(this);
            scrollArea->installEventFilter(this);
            if (m_container && m_container != scrollArea) {
                m_container->installEventFilter(this);
            }
            updatePosition();
        }

    protected:
        bool eventFilter(QObject *watched, QEvent *event) override {
            switch (event->type()) {
                case QEvent::Move:
                case QEvent::Resize:
                case QEvent::Show:
                case QEvent::LayoutRequest:
                case QEvent::StyleChange:
                case QEvent::LayoutDirectionChange:
                case QEvent::ContentsRectChange:
                    updatePosition();
                    break;
                case QEvent::DynamicPropertyChange:
                    if (watched == m_scrollBar && static_cast<QDynamicPropertyChangeEvent *>(event)->propertyName() ==
                                                      kReversedOverlayScrollBarProperty) {
                        updatePosition();
                    }
                    break;
                default:
                    break;
            }
            return QObject::eventFilter(watched, event);
        }

    private:
        void updatePosition() {
            if (m_updating || !m_scrollBar || !m_scrollArea || !m_container ||
                m_container->parentWidget() != m_scrollArea) {
                return;
            }
            QScopedValueRollback<bool> guard(m_updating, true);
            const bool reversed = m_scrollBar->property(kReversedOverlayScrollBarProperty).toBool();
            const QRect bounds = m_scrollArea->contentsRect();
            QPoint position = m_container->pos();
            // Qt lays out each scroll bar inside a container. Move that container
            // after Qt's layout events so painting and hit testing stay together.
            // Keep Qt's length and header offsets along the scrolling axis.
            if (m_scrollBar->orientation() == Qt::Vertical) {
                position.setX(reversed ? bounds.left() : bounds.right() - m_container->width() + 1);
            } else {
                position.setY(reversed ? bounds.top() : bounds.bottom() - m_container->height() + 1);
            }
            if (position != m_container->pos()) {
                m_container->move(position);
            }
        }

        QPointer<QScrollBar> m_scrollBar;
        QPointer<QAbstractScrollArea> m_scrollArea;
        QPointer<QWidget> m_container;
        bool m_updating = false;
    };

    class WindowsOverlayScrollBarStyle final : public QProxyStyle {
    public:
        WindowsOverlayScrollBarStyle() : QProxyStyle(QStyleFactory::create("Fusion")) {}

        int styleHint(const StyleHint hint, const QStyleOption *option, const QWidget *widget,
                      QStyleHintReturn *returnData) const override {
            if (hint == SH_ScrollBar_Transient) {
                return true;
            }
            return QProxyStyle::styleHint(hint, option, widget, returnData);
        }

        int pixelMetric(const PixelMetric metric, const QStyleOption *option, const QWidget *widget) const override {
            switch (metric) {
                case PM_ScrollBarExtent:
                case PM_ScrollView_ScrollBarOverlap:
                    return kScrollBarExtent;
                case PM_ScrollBarSliderMin:
                    return 24;
                default:
                    return QProxyStyle::pixelMetric(metric, option, widget);
            }
        }

        void drawComplexControl(const ComplexControl control, const QStyleOptionComplex *option, QPainter *painter,
                                const QWidget *widget) const override {
            if (control == CC_ScrollBar) {
#ifndef Q_OS_MAC
                const qreal progress = widget ? widget->property(kOverlayScrollBarHoverProgressProperty).toReal() : 0.0;
                if (progress > 0.0) {
                    QColor color = option->palette.text().color();
                    color.setAlpha(qRound(kTrackAlpha * progress));
                    painter->fillRect(option->rect, color);
                }
#endif
                // 滑块由 WindowsOverlayScrollBarHandle 绘制；底层 style 负责轨道、几何和命中测试。
                return;
            }
            QProxyStyle::drawComplexControl(control, option, painter, widget);
        }
    };

    class WindowsOverlayScrollBarHandle final : public QWidget {
    public:
        explicit WindowsOverlayScrollBarHandle(QWidget *parent) : QWidget(parent) {
            setAttribute(Qt::WA_NoSystemBackground);
            setAttribute(Qt::WA_TransparentForMouseEvents);
        }

        qreal expansionProgress() const {
            return m_expansionProgress;
        }

        void setExpansionProgress(const qreal progress) {
            m_expansionProgress = qBound(0.0, progress, 1.0);
            update();
        }

    protected:
        void paintEvent(QPaintEvent *event) override {
            Q_UNUSED(event);
            QPainter painter(this);
#ifdef Q_OS_MAC
            painter.setRenderHint(QPainter::Antialiasing);
#endif
            painter.setPen(Qt::NoPen);

            // 滚动条颜色
            QColor color = palette().text().color();
            color.setAlpha(qRound(35 + 20 * m_expansionProgress));
            painter.setBrush(color);

#ifdef Q_OS_MAC
            QRectF knobRect(rect());
            knobRect.adjust(0.5, 0.5, -0.5, -0.5);
            const qreal radius = qMin(knobRect.width(), knobRect.height()) / 2.0;
            painter.drawRoundedRect(knobRect, radius, radius);
#else
            painter.drawRect(rect());
#endif
        }

    private:
        qreal m_expansionProgress = 0.0;
    };

    QRect scrollBarSliderRect(const QScrollBar *scrollBar) {
        QStyleOptionSlider option;
        option.initFrom(scrollBar);
        option.subControls = QStyle::SC_All;
        option.orientation = scrollBar->orientation();
        option.minimum = scrollBar->minimum();
        option.maximum = scrollBar->maximum();
        option.sliderPosition = scrollBar->sliderPosition();
        option.sliderValue = scrollBar->value();
        option.singleStep = scrollBar->singleStep();
        option.pageStep = scrollBar->pageStep();
        option.upsideDown = scrollBar->invertedAppearance();
        if (option.orientation == Qt::Horizontal) {
            option.state |= QStyle::State_Horizontal;
        }
        return scrollBar->style()->subControlRect(QStyle::CC_ScrollBar, &option, QStyle::SC_ScrollBarSlider, scrollBar);
    }

    QRect scrollBarHandleRect(const QScrollBar *scrollBar, int knobWidth) {
        const QRect sliderRect = scrollBarSliderRect(scrollBar);
        const bool reversed = scrollBar->property(kReversedOverlayScrollBarProperty).toBool();
        if (scrollBar->orientation() == Qt::Vertical) {
            const int x = reversed ? kKnobMargin : scrollBar->width() - kKnobMargin - knobWidth;
            return QRect(x, sliderRect.y() + kKnobMargin, knobWidth, qMax(1, sliderRect.height() - kKnobMargin * 2));
        }
        const int y = reversed ? kKnobMargin : scrollBar->height() - kKnobMargin - knobWidth;
        return QRect(sliderRect.x() + kKnobMargin, y, qMax(1, sliderRect.width() - kKnobMargin * 2), knobWidth);
    }

#ifdef Q_OS_MAC
    class WindowsOverlayScrollBarController final : public QObject {
    public:
        WindowsOverlayScrollBarController(QScrollBar *scrollBar, QAbstractScrollArea *scrollArea)
            : QObject(scrollArea),
              m_scrollBar(scrollBar),
              m_scrollArea(scrollArea),
              m_handle(new WindowsOverlayScrollBarHandle(scrollBar)),
              m_opacityEffect(new QGraphicsOpacityEffect(scrollBar)),
              m_fadeAnimation(new QPropertyAnimation(m_opacityEffect, "opacity", this)),
              m_expandAnimation(new QVariantAnimation(this)),
              m_fadeTimer(new QTimer(this)) {
            m_opacityEffect->setOpacity(0.0);
            m_scrollBar->setGraphicsEffect(m_opacityEffect);
            // 仅 macOS 在隐藏时让鼠标事件穿透，滚动显示后再恢复悬停和拖动交互。
            updateMouseTransparency();
            connect(m_opacityEffect, &QGraphicsOpacityEffect::opacityChanged, this,
                    [this](qreal) { updateMouseTransparency(); });
            m_scrollBar->setAttribute(Qt::WA_Hover);
            m_scrollBar->setMouseTracking(true);
            m_scrollBar->installEventFilter(this);
            m_scrollArea->installEventFilter(this);

            m_fadeAnimation->setDuration(kFadeOutDuration);
            m_fadeAnimation->setEndValue(0.0);
            m_fadeAnimation->setEasingCurve(QEasingCurve::Linear);

            m_expandAnimation->setDuration(kExpandDuration);
            m_expandAnimation->setEasingCurve(QEasingCurve::Linear);
            connect(m_expandAnimation, &QVariantAnimation::valueChanged, this, [this](const QVariant &value) {
                m_handle->setExpansionProgress(value.toReal());
                updateHandleGeometry();
            });

            m_fadeTimer->setInterval(kFadeOutDelay);
            m_fadeTimer->setSingleShot(true);

            connect(m_fadeTimer, &QTimer::timeout, this, [this] {
                if (m_scrollBar->underMouse() || m_scrollBar->isSliderDown()) {
                    return;
                }
                m_fadeAnimation->stop();
                m_fadeAnimation->setStartValue(m_opacityEffect->opacity());
                m_fadeAnimation->start();
            });
            connect(m_scrollBar, &QScrollBar::valueChanged, this, [this] {
                updateHandleGeometry();
                reveal(false);
            });
            connect(m_scrollBar, &QScrollBar::sliderPressed, this, [this] {
                animateExpansion(true);
                reveal(true);
            });
            connect(m_scrollBar, &QScrollBar::sliderReleased, this, [this] {
                if (!m_scrollBar->underMouse()) {
                    animateExpansion(false);
                    scheduleFadeOut();
                }
            });
            connect(m_scrollBar, &QScrollBar::rangeChanged, this, [this](const int minimum, const int maximum) {
                if (minimum >= maximum) {
                    m_handle->hide();
                    m_fadeTimer->stop();
                    m_fadeAnimation->stop();
                    m_opacityEffect->setOpacity(0.0);
                } else {
                    updateHandleGeometry();
                }
            });

            updateHandleGeometry();
        }

        bool eventFilter(QObject *watched, QEvent *event) override {
            if (watched == m_scrollArea) {
                switch (event->type()) {
                    case QEvent::Hide:
                    case QEvent::HideToParent:
                    case QEvent::WindowDeactivate:
                        resetInteractionState();
                        break;
                    case QEvent::DynamicPropertyChange:
                        if (const auto *change = dynamic_cast<QDynamicPropertyChangeEvent *>(event);
                            change->propertyName() == kActiveOverlayScrollBarProperty) {
                            if (const int activeOrientation = m_scrollArea->property(kActiveOverlayScrollBarProperty)
                                                                  .toInt();
                                activeOrientation != static_cast<int>(m_scrollBar->orientation())) {
                                fadeOutQuickly();
                            }
                        }
                        break;
                    default:
                        break;
                }
                return QObject::eventFilter(watched, event);
            }

            if (watched != m_scrollBar) {
                return QObject::eventFilter(watched, event);
            }

            switch (event->type()) {
                case QEvent::Enter:
                case QEvent::HoverEnter:
                case QEvent::MouseMove:
                case QEvent::HoverMove:
                case QEvent::MouseButtonPress:
                    if (m_opacityEffect->opacity() > 0.0) {
                        animateExpansion(true);
                        reveal(true);
                    }
                    break;
                case QEvent::Leave:
                case QEvent::HoverLeave:
                    if (!m_scrollBar->isSliderDown()) {
                        animateExpansion(false);
                        scheduleFadeOut();
                    }
                    break;
                case QEvent::Resize:
                case QEvent::Show:
                case QEvent::StyleChange:
                case QEvent::LayoutDirectionChange:
                    updateHandleGeometry();
                    break;
                case QEvent::DynamicPropertyChange:
                    if (static_cast<QDynamicPropertyChangeEvent *>(event)->propertyName() ==
                        kReversedOverlayScrollBarProperty) {
                        updateHandleGeometry();
                    }
                    break;
                default:
                    break;
            }
            return QObject::eventFilter(watched, event);
        }

    private:
        void updateMouseTransparency() const {
            const bool transparent = m_opacityEffect->opacity() <= 0.0;
            m_scrollBar->setAttribute(Qt::WA_TransparentForMouseEvents, transparent);

            // Qt 的滚动条容器覆盖在 viewport 上方，本体穿透后容器也需要穿透。
            if (auto *container = m_scrollBar->parentWidget();
                container && container->parentWidget() == m_scrollArea && container != m_scrollArea->viewport()) {
                container->setAttribute(Qt::WA_TransparentForMouseEvents, transparent);
            }
        }

        void fadeOutQuickly() {
            m_fadeTimer->stop();

            animateExpansion(false);

            m_fadeAnimation->stop();
            m_fadeAnimation->setDuration(kFadeOutDuration);
            m_fadeAnimation->setStartValue(m_opacityEffect->opacity());
            m_fadeAnimation->setEndValue(0.0);
            m_fadeAnimation->start();
        }

        void resetInteractionState() {
            if (QWidget::mouseGrabber() == m_scrollBar) {
                m_scrollBar->releaseMouse();
            }
            if (m_scrollBar->isSliderDown()) {
                m_scrollBar->setSliderDown(false);
            }

            m_fadeTimer->stop();
            m_fadeAnimation->stop();
            m_expandAnimation->stop();
            m_expanded = false;
            m_handle->setExpansionProgress(0.0);
            updateHandleGeometry();
            m_opacityEffect->setOpacity(0.0);
        }

        void animateExpansion(const bool expanded) {
            if (m_expanded == expanded) {
                return;
            }

            m_expanded = expanded;
            m_expandAnimation->stop();
            m_expandAnimation->setStartValue(m_handle->expansionProgress());
            m_expandAnimation->setEndValue(expanded ? 1.0 : 0.0);
            m_expandAnimation->start();
        }

        void updateHandleGeometry() const {
            if (m_scrollBar->minimum() >= m_scrollBar->maximum()) {
                m_handle->hide();
                return;
            }

            const int knobWidth = qRound(kKnobWidth +
                                         (kExpandedKnobWidth - kKnobWidth) * m_handle->expansionProgress());
            m_handle->setGeometry(scrollBarHandleRect(m_scrollBar, knobWidth));
            m_handle->show();
            m_handle->raise();
        }

        void reveal(const bool keepVisible) {
            if (m_scrollBar->minimum() >= m_scrollBar->maximum()) {
                return;
            }

            const auto *otherBar = m_scrollBar->orientation() == Qt::Vertical ? m_scrollArea->horizontalScrollBar()
                                                                              : m_scrollArea->verticalScrollBar();
            // 正在拖动的滚动条优先保持显示。
            if (otherBar->isSliderDown()) {
                return;
            }

            // 属性改变会通知两个 Controller；
            // 另一个 Controller 收到通知后立即变透明。
            m_scrollArea->setProperty("macOSActiveOverlayScrollBar", static_cast<int>(m_scrollBar->orientation()));

            m_fadeTimer->stop();
            m_fadeAnimation->stop();
            m_opacityEffect->setOpacity(1.0);

            if (!keepVisible && !m_scrollBar->underMouse() && !m_scrollBar->isSliderDown()) {
                scheduleFadeOut();
            }
        }

        void scheduleFadeOut() const {
            m_fadeTimer->start();
        }

        QScrollBar *m_scrollBar;
        QAbstractScrollArea *m_scrollArea;
        WindowsOverlayScrollBarHandle *m_handle;
        QGraphicsOpacityEffect *m_opacityEffect;
        QPropertyAnimation *m_fadeAnimation;
        QVariantAnimation *m_expandAnimation;
        QTimer *m_fadeTimer;
        bool m_expanded = false;
    };
#else
    class PersistentOverlayScrollBarController final : public QObject {
    public:
        PersistentOverlayScrollBarController(QScrollBar *scrollBar, QAbstractScrollArea *scrollArea)
            : QObject(scrollArea),
              m_scrollBar(scrollBar),
              m_handle(new WindowsOverlayScrollBarHandle(scrollBar)),
              m_hoverAnimation(new QVariantAnimation(this)) {
            m_scrollBar->setAttribute(Qt::WA_OpaquePaintEvent, false);
            m_scrollBar->setAutoFillBackground(false);
            m_scrollBar->setAttribute(Qt::WA_Hover);
            m_scrollBar->setMouseTracking(true);
            m_scrollBar->installEventFilter(this);

            m_hoverAnimation->setDuration(kExpandDuration);
            m_hoverAnimation->setEasingCurve(QEasingCurve::InOutQuad);
            m_hoverAnimation->setStartValue(0.0);
            m_hoverAnimation->setEndValue(1.0);
            connect(m_hoverAnimation, &QVariantAnimation::valueChanged, this, [this](const QVariant &value) {
                const qreal progress = value.toReal();
                m_handle->setExpansionProgress(progress);
                m_scrollBar->setProperty(kOverlayScrollBarHoverProgressProperty, progress);
                m_scrollBar->update();
            });
            connect(m_scrollBar, &QScrollBar::valueChanged, this, [this] { updateHandleGeometry(); });
            connect(m_scrollBar, &QScrollBar::rangeChanged, this, [this] { updateHandleGeometry(); });
            connect(m_scrollBar, &QScrollBar::sliderPressed, this, [this] { setHovered(true); });
            connect(m_scrollBar, &QScrollBar::sliderReleased, this, [this] { setHovered(m_scrollBar->underMouse()); });

            updateHandleGeometry();
        }

        bool eventFilter(QObject *watched, QEvent *event) override {
            if (watched != m_scrollBar) {
                return QObject::eventFilter(watched, event);
            }
            switch (event->type()) {
                case QEvent::Enter:
                case QEvent::HoverEnter:
                case QEvent::MouseMove:
                case QEvent::HoverMove:
                    setHovered(true);
                    break;
                case QEvent::Leave:
                case QEvent::HoverLeave:
                    if (!m_scrollBar->isSliderDown()) {
                        setHovered(false);
                    }
                    break;
                case QEvent::Hide:
                    setHovered(false);
                    break;
                case QEvent::Resize:
                case QEvent::Show:
                case QEvent::StyleChange:
                case QEvent::LayoutDirectionChange:
                    updateHandleGeometry();
                    break;
                case QEvent::DynamicPropertyChange:
                    if (static_cast<QDynamicPropertyChangeEvent *>(event)->propertyName() ==
                        kReversedOverlayScrollBarProperty) {
                        updateHandleGeometry();
                    }
                    break;
                default:
                    break;
            }
            return QObject::eventFilter(watched, event);
        }

    private:
        void setHovered(bool hovered) {
            hovered = hovered && m_scrollBar->minimum() < m_scrollBar->maximum();
            if (m_scrollBar->property(kHoveredOverlayScrollBarProperty).toBool() == hovered) {
                return;
            }
            m_scrollBar->setProperty(kHoveredOverlayScrollBarProperty, hovered);
            // 动画运行时只反转方向，轨道透明度和滑块颜色都从当前进度继续。
            m_hoverAnimation->setDirection(hovered ? QAbstractAnimation::Forward : QAbstractAnimation::Backward);
            if (m_hoverAnimation->state() != QAbstractAnimation::Running) {
                m_hoverAnimation->start();
            }
        }

        void updateHandleGeometry() {
            if (m_scrollBar->minimum() >= m_scrollBar->maximum()) {
                m_handle->hide();
                setHovered(false);
                return;
            }
            m_handle->setGeometry(scrollBarHandleRect(m_scrollBar, kKnobWidth));
            m_handle->show();
            m_handle->raise();
        }

        QScrollBar *m_scrollBar;
        WindowsOverlayScrollBarHandle *m_handle;
        QVariantAnimation *m_hoverAnimation;
    };
#endif

    void installOverlayScrollBar(QScrollBar *scrollBar, QStyle *style, QAbstractScrollArea *scrollArea) {
        scrollBar->setContextMenuPolicy(Qt::PreventContextMenu);
        scrollBar->setProperty(kMacOSOverlayScrollBarProperty, true);
        scrollBar->setStyle(style);
#ifdef Q_OS_MAC
        new WindowsOverlayScrollBarController(scrollBar, scrollArea);
#else
        new PersistentOverlayScrollBarController(scrollBar, scrollArea);
#endif
        new OverlayScrollBarPositionController(scrollBar, scrollArea);
    }
} // namespace

void MacScrollStyle::use(QWidget *widget, Direction vertical, Direction horizontal) {
    auto *area = qobject_cast<QAbstractScrollArea *>(widget);
    if (!area) {
        return;
    }

    area->verticalScrollBar()->setProperty(kReversedOverlayScrollBarProperty, vertical == Direction::Reverse);
    area->horizontalScrollBar()->setProperty(kReversedOverlayScrollBarProperty, horizontal == Direction::Reverse);

    if (!area->property(kMacOSOverlayScrollBarsInstalledProperty).toBool()) {
        area->setProperty(kMacOSOverlayScrollBarsInstalledProperty, true);
        auto *style = new WindowsOverlayScrollBarStyle;
        style->setParent(area);
        installOverlayScrollBar(area->verticalScrollBar(), style, area);
        installOverlayScrollBar(area->horizontalScrollBar(), style, area);
    }

    // QAbstractScrollArea 会根据 transient/overlap 指标重新把滚动条放到 viewport 上方。
    area->setVerticalScrollBarPolicy(area->verticalScrollBarPolicy());
    area->setHorizontalScrollBarPolicy(area->horizontalScrollBarPolicy());
}
