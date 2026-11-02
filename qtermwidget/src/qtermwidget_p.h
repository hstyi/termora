#pragma once

#include <QSet>
#include <QTimer>
#include <array>
#include <ghostty/vt.h>
#include <qtermwidget/qtermwidget.h>

class QScrollBar; ///< 由 Qt 管理交互和外观的垂直滚动条。

/** 管理 Ghostty 资源；所有内核访问与 QWidget 一样限定在 GUI 线程。 */
class QTermWidgetPrivate {
public:
    /** 绑定 owner 控件；资源由 initialize() 初始化，保证部分初始化失败也能释放。 */
    explicit QTermWidgetPrivate(QTermWidget *owner);
    /** 按依赖顺序释放迭代器、编码器、选区手势和终端。 */
    ~QTermWidgetPrivate();
    /** 创建内核及复用的渲染／输入对象，失败时抛出异常。 */
    void initialize();
    /** 用控件字体和可用区域重新计算网格，并通知连接层。 */
    void resizeTerminal();
    /** 返回当前样式下垂直滚动条占用的逻辑像素宽度。 */
    int scrollBarWidth() const;
    /** 返回扣除垂直滚动条后的终端绘制及鼠标交互区域，坐标相对于控件。 */
    QRect terminalRect() const;
    /** 从 Ghostty 同步滚动范围、分页大小和位置，阻断反馈信号以避免回写视口。 */
    void syncScrollBar();
    /** 比较当前选区与上次快照，仅在范围或文本变化时发出通知。 */
    void notifySelection();
    /** 把 position 像素坐标限制到视口内并转换为 Ghostty 坐标。 */
    GhosttyPoint viewportPoint(const QPoint &position) const;
    /** 编码 event 按键；release 为 true 时发送释放动作。 */
    void encodeKey(QKeyEvent *event, bool release);
    /** 返回应用是否启用了终端鼠标报告。 */
    bool mouseReporting() const;
    /** 按当前协议编码 action/button；position 为 Qt 逻辑坐标，modifiers/buttons 为事件当前状态。 */
    QByteArray encodeMouse(GhosttyMouseAction action, GhosttyMouseButton button, const QPointF &position,
                           Qt::KeyboardModifiers modifiers, Qt::MouseButtons buttons);
    /** 应用捕获鼠标时编码并发送 event，返回是否已消费该事件。 */
    bool reportMouse(QMouseEvent *event);
    /** 将 event 转为 type 指定的选区手势，锚点、连续点击及拖选范围由 Ghostty 计算。 */
    void selectMouse(QMouseEvent *event, GhosttySelectionGestureEventType type);
    /** 使用最近的鼠标位置处理 type 手势；timestamp 为按下事件的 Qt 毫秒时间戳，定时滚动时无需提供。 */
    void applySelectionGesture(GhosttySelectionGestureEventType type, ulong timestamp = 0);
    /** 取消手势并停止自动滚动，保留已经建立的选区。 */
    void resetSelectionGesture();
    /** 从视口坐标 point 向本行两侧查找连续空白；找到时将范围写入 selection，否则返回 false。 */
    bool blankSelection(GhosttyPoint point, GhosttySelection *selection) const;
    /** 将 selection 按正向排列并扩展到首行行首和末行行尾，保留范围内的空白。 */
    bool expandToFullRows(GhosttySelection *selection) const;
    /** 返回输入法与光标绘制使用的视口光标矩形。 */
    QRect cursorRect() const;
    /** 将 bytes 发给连接层，并让视口回到最新输出；空数据不发送。 */
    void sendInput(const QByteArray &bytes);
    /** 缓存内核应答；terminal 是回调来源，userdata 是私有对象，data/len 是借用字节。 */
    static void writePty(GhosttyTerminal terminal, void *userdata, const uint8_t *data, size_t len);
    /** 转发标题；terminal 是回调来源，userdata 是私有对象。 */
    static void titleChange(GhosttyTerminal terminal, void *userdata);
    /** 转发响铃；terminal 是回调来源，userdata 是私有对象。 */
    static void ringBell(GhosttyTerminal terminal, void *userdata);

    QTermWidget *q;                                     ///< 所属控件，生命周期覆盖私有对象。
    QScrollBar *verticalScrollBar = nullptr;            ///< 固定放在右侧的垂直滚动条，由所属控件负责销毁。
    GhosttyTerminal terminal = nullptr;                 ///< VT 解析、屏幕、回滚历史及受跟踪的活动选区。
    GhosttyRenderState render = nullptr;                ///< 与内核同步的可见屏幕快照。
    GhosttyRenderStateRowIterator rows = nullptr;       ///< 复用的可见行迭代器。
    GhosttyRenderStateRowCells cells = nullptr;         ///< 复用的行内单元格迭代器。
    GhosttyKeyEncoder keyEncoder = nullptr;             ///< 根据终端模式生成键盘字节。
    GhosttyKeyEvent keyEvent = nullptr;                 ///< 复用的 Ghostty 键盘事件。
    GhosttyMouseEncoder mouseEncoder = nullptr;         ///< 根据应用启用的跟踪模式和协议编码鼠标。
    GhosttyMouseEvent mouseEvent = nullptr;             ///< 复用的鼠标事件，包含按钮、修饰键和像素位置。
    GhosttySelectionGesture selectionGesture = nullptr; ///< 内核维护的点击序列及随滚屏、重排跟踪的拖选状态。
    /** 按 PRESS、RELEASE、DRAG、AUTOSCROLL_TICK 顺序复用的事件。 */
    std::array<GhosttySelectionGestureEvent, 4> selectionEvents = {};
    QTimer selectionScrollTimer; ///< 驱动边缘拖选，鼠标停留不动时仍向内核发送自动滚动事件。
    QPointF selectionPosition;   ///< 最近一次本地选区事件的控件逻辑坐标，保留超出视口的距离。
    Qt::KeyboardModifiers selectionModifiers = Qt::NoModifier; ///< 最近一次选区事件的修饰键，供定时拖选保留矩形模式。
    QSize gridSize = {80, 24};                                 ///< 当前内核中的列数和行数。
    QSize cellSize = {1, 1};                                   ///< 字符单元格大小，单位为 Qt 逻辑像素。
    int ascent = 1;                                            ///< 基线距离单元格顶部的逻辑像素数。
    QPoint wheelRemainder;                                     ///< 两轴尚未累计满一个滚轮刻度的角度增量。
    QPoint pixelScrollRemainder;                               ///< 触控板两轴尚未累计满一个单元格的逻辑像素增量。
    QSet<int> shortcutKeys;                                    ///< 已被控件快捷键消费的按键，防止只发送其释放事件。
    QString preedit;                                           ///< 输入法尚未提交的文本，仅覆盖绘制，不写入终端。
    std::optional<QTermWidget::Selection> lastSelection;       ///< 用于变化通知的上一次选区坐标快照。
    QString lastSelectedText;                                  ///< 用于检测选区内文本被终端改写。
    QTimer blinkTimer;                                         ///< 驱动光标和闪烁文字的重绘。
    bool blinkOn = true;                                       ///< 当前闪烁相位；输入后恢复可见。
};
