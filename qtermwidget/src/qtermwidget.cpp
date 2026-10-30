#include "qtermwidget_p.h"

#include <QApplication>
#include <QClipboard>
#include <QFontDatabase>
#include <QPainter>
#include <QStyleHints>
#include <QWheelEvent>
#include <algorithm>
#include <climits>
#include <limits>
#include <stdexcept>

namespace {
    constexpr GhosttyColorRgb kForeground = {.r = 220, .g = 220, .b = 220};
    constexpr GhosttyColorRgb kBackground = {.r = 30, .g = 30, .b = 30};
    constexpr bool kTrackCell = true; // 合并同一字符格内的重复移动，SGR 像素模式由编码器另行处理。

    /** 检查 result；operation 标明失败的内核操作，便于定位初始化错误。 */
    void requireSuccess(const GhosttyResult result, const char *operation) {
        if (result != GHOSTTY_SUCCESS) {
            throw std::runtime_error(operation);
        }
    }

    /** 将 color 的 Ghostty RGB 分量转换为 Qt 颜色。 */
    QColor qtColor(GhosttyColorRgb color) {
        return {color.r, color.g, color.b};
    }

    /** 将 point 转为 tag 指定坐标系中的单元格；调用前须验证坐标范围。 */
    GhosttyPoint gridPoint(const QPoint &point, const GhosttyPointTag tag) {
        GhosttyPoint result{};
        result.tag = tag;
        result.value.coordinate = {.x = static_cast<uint16_t>(point.x()), .y = static_cast<uint32_t>(point.y())};
        return result;
    }

    /** 将 event 中的 Qt 键值映射到 Ghostty；可打印文字另由 UTF-8 文本字段传递。 */
    GhosttyKey terminalKey(const QKeyEvent *event) {
        const int key = event->key();
        if (key >= Qt::Key_A && key <= Qt::Key_Z) {
            return static_cast<GhosttyKey>(GHOSTTY_KEY_A + key - Qt::Key_A);
        }
        if (key >= Qt::Key_0 && key <= Qt::Key_9) {
            const auto base = event->modifiers().testFlag(Qt::KeypadModifier) ? GHOSTTY_KEY_NUMPAD_0
                                                                              : GHOSTTY_KEY_DIGIT_0;
            return static_cast<GhosttyKey>(base + key - Qt::Key_0);
        }
        if (key >= Qt::Key_F1 && key <= Qt::Key_F25) {
            return static_cast<GhosttyKey>(GHOSTTY_KEY_F1 + key - Qt::Key_F1);
        }
        switch (key) {
            case Qt::Key_Return:
                return GHOSTTY_KEY_ENTER;
            case Qt::Key_Enter:
                return GHOSTTY_KEY_NUMPAD_ENTER;
            case Qt::Key_Escape:
                return GHOSTTY_KEY_ESCAPE;
            case Qt::Key_Tab:
            case Qt::Key_Backtab:
                return GHOSTTY_KEY_TAB;
            case Qt::Key_Backspace:
                return GHOSTTY_KEY_BACKSPACE;
            case Qt::Key_Insert:
                return GHOSTTY_KEY_INSERT;
            case Qt::Key_Delete:
                return GHOSTTY_KEY_DELETE;
            case Qt::Key_Home:
                return GHOSTTY_KEY_HOME;
            case Qt::Key_End:
                return GHOSTTY_KEY_END;
            case Qt::Key_PageUp:
                return GHOSTTY_KEY_PAGE_UP;
            case Qt::Key_PageDown:
                return GHOSTTY_KEY_PAGE_DOWN;
            case Qt::Key_Left:
                return GHOSTTY_KEY_ARROW_LEFT;
            case Qt::Key_Right:
                return GHOSTTY_KEY_ARROW_RIGHT;
            case Qt::Key_Up:
                return GHOSTTY_KEY_ARROW_UP;
            case Qt::Key_Down:
                return GHOSTTY_KEY_ARROW_DOWN;
            case Qt::Key_Space:
                return GHOSTTY_KEY_SPACE;
            case Qt::Key_QuoteLeft:
                return GHOSTTY_KEY_BACKQUOTE;
            case Qt::Key_Backslash:
                return GHOSTTY_KEY_BACKSLASH;
            case Qt::Key_BracketLeft:
                return GHOSTTY_KEY_BRACKET_LEFT;
            case Qt::Key_BracketRight:
                return GHOSTTY_KEY_BRACKET_RIGHT;
            case Qt::Key_Comma:
                return GHOSTTY_KEY_COMMA;
            case Qt::Key_Equal:
                return GHOSTTY_KEY_EQUAL;
            case Qt::Key_Minus:
                return GHOSTTY_KEY_MINUS;
            case Qt::Key_Period:
                return GHOSTTY_KEY_PERIOD;
            case Qt::Key_Apostrophe:
                return GHOSTTY_KEY_QUOTE;
            case Qt::Key_Semicolon:
                return GHOSTTY_KEY_SEMICOLON;
            case Qt::Key_Slash:
                return GHOSTTY_KEY_SLASH;
            default:
                return GHOSTTY_KEY_UNIDENTIFIED;
        }
    }

    /** 转换 modifiers；Qt 在 macOS 默认将 Command 和物理 Control 的语义互换。 */
    GhosttyMods terminalModifiers(const Qt::KeyboardModifiers modifiers) {
        GhosttyMods result = 0;
        if (modifiers.testFlag(Qt::ShiftModifier)) {
            result |= GHOSTTY_MODS_SHIFT;
        }
        if (modifiers.testFlag(Qt::AltModifier)) {
            result |= GHOSTTY_MODS_ALT;
        }
#ifdef Q_OS_MACOS
        if (modifiers.testFlag(Qt::ControlModifier)) {
            result |= GHOSTTY_MODS_SUPER;
        }
        if (modifiers.testFlag(Qt::MetaModifier)) {
            result |= GHOSTTY_MODS_CTRL;
        }
#else
        if (modifiers.testFlag(Qt::ControlModifier)) {
            result |= GHOSTTY_MODS_CTRL;
        }
        if (modifiers.testFlag(Qt::MetaModifier)) {
            result |= GHOSTTY_MODS_SUPER;
        }
#endif
        return result;
    }
} // namespace

QTermWidgetPrivate::QTermWidgetPrivate(QTermWidget *owner) : q(owner) {}

QTermWidgetPrivate::~QTermWidgetPrivate() {
    for (const auto event : selectionEvents) {
        ghostty_selection_gesture_event_free(event);
    }
    ghostty_selection_gesture_free(selectionGesture, terminal);
    ghostty_mouse_event_free(mouseEvent);
    ghostty_mouse_encoder_free(mouseEncoder);
    ghostty_key_event_free(keyEvent);
    ghostty_key_encoder_free(keyEncoder);
    ghostty_render_state_row_cells_free(cells);
    ghostty_render_state_row_iterator_free(rows);
    ghostty_render_state_free(render);
    ghostty_terminal_free(terminal);
}

void QTermWidgetPrivate::initialize() {
    requireSuccess(ghostty_terminal_new(nullptr, &terminal, gridSize.width(), gridSize.height()),
                   "Create Ghostty terminal");
    requireSuccess(ghostty_render_state_new(nullptr, &render), "Create Ghostty render state");
    requireSuccess(ghostty_render_state_row_iterator_new(nullptr, &rows), "Create Ghostty row iterator");
    requireSuccess(ghostty_render_state_row_cells_new(nullptr, &cells), "Create Ghostty cell iterator");
    requireSuccess(ghostty_key_encoder_new(nullptr, &keyEncoder), "Create Ghostty key encoder");
    requireSuccess(ghostty_key_event_new(nullptr, &keyEvent), "Create Ghostty key event");
    requireSuccess(ghostty_mouse_encoder_new(nullptr, &mouseEncoder), "Create Ghostty mouse encoder");
    requireSuccess(ghostty_mouse_event_new(nullptr, &mouseEvent), "Create Ghostty mouse event");
    requireSuccess(ghostty_selection_gesture_new(nullptr, &selectionGesture), "Create Ghostty selection gesture");
    for (size_t i = 0; i < selectionEvents.size(); ++i) {
        requireSuccess(ghostty_selection_gesture_event_new(nullptr, &selectionEvents[i],
                                                           static_cast<GhosttySelectionGestureEventType>(i)),
                       "Create Ghostty selection event");
    }
    ghostty_mouse_encoder_setopt(mouseEncoder, GHOSTTY_MOUSE_ENCODER_OPT_TRACK_LAST_CELL, &kTrackCell);
    requireSuccess(ghostty_terminal_set(terminal, GHOSTTY_TERMINAL_OPT_USERDATA, this), "Set Ghostty userdata");
    requireSuccess(
        ghostty_terminal_set(terminal, GHOSTTY_TERMINAL_OPT_WRITE_PTY, reinterpret_cast<const void *>(writePty)),
        "Set Ghostty output callback");
    requireSuccess(
        ghostty_terminal_set(terminal, GHOSTTY_TERMINAL_OPT_TITLE_CHANGED, reinterpret_cast<const void *>(titleChange)),
        "Set Ghostty title callback");
    requireSuccess(ghostty_terminal_set(terminal, GHOSTTY_TERMINAL_OPT_BELL, reinterpret_cast<const void *>(ringBell)),
                   "Set Ghostty bell callback");
    // 给默认配色明确的深色背景；OSC 调色仍由 Ghostty 维护。

    ghostty_terminal_set(terminal, GHOSTTY_TERMINAL_OPT_COLOR_FOREGROUND, &kForeground);
    ghostty_terminal_set(terminal, GHOSTTY_TERMINAL_OPT_COLOR_BACKGROUND, &kBackground);
    blinkTimer.setInterval(500);
    QObject::connect(&blinkTimer, &QTimer::timeout, q, [this] {
        blinkOn = !blinkOn;
        if (q->isVisible()) {
            q->update();
        }
    });
    blinkTimer.start();
}

void QTermWidgetPrivate::resizeTerminal() {
    const QFontMetrics metrics(q->font());
    cellSize = {std::max(1, metrics.horizontalAdvance(QLatin1Char('M'))), std::max(1, metrics.height())};
    ascent = metrics.ascent();
    const QSize available = q->contentsRect().size();
    const QSize next(std::clamp(available.width() / cellSize.width(), 1, 65535),
                     std::clamp(available.height() / cellSize.height(), 1, 65535));
    if (!terminal) {
        return;
    }
    // 字号变化时即使行列数不变，也要更新内核用于像素尺寸报告的单元格大小。
    if (const qreal scale = q->devicePixelRatioF();
        ghostty_terminal_resize(terminal, next.width(), next.height(), qRound(cellSize.width() * scale),
                                qRound(cellSize.height() * scale)) != GHOSTTY_SUCCESS) {
        return;
    }
    const bool changed = gridSize != next;
    gridSize = next;
    GhosttyMouseEncoderSize mouseSize = GHOSTTY_INIT_SIZED(GhosttyMouseEncoderSize);
    mouseSize.cell_width = qRound(cellSize.width() * q->devicePixelRatioF());
    mouseSize.cell_height = qRound(cellSize.height() * q->devicePixelRatioF());
    mouseSize.screen_width = gridSize.width() * mouseSize.cell_width;
    mouseSize.screen_height = gridSize.height() * mouseSize.cell_height;
    ghostty_mouse_encoder_setopt(mouseEncoder, GHOSTTY_MOUSE_ENCODER_OPT_SIZE, &mouseSize);
    q->updateGeometry();
    q->update();
    notifySelection();
    if (changed) {
        Q_EMIT q->terminalSizeChanged(next);
    }
}

void QTermWidgetPrivate::notifySelection() {
    const auto current = q->selection();
    const QString text = current ? q->selectedText() : QString();
    if (current == lastSelection && text == lastSelectedText) {
        return;
    }
    lastSelection = current;
    lastSelectedText = text;
    Q_EMIT q->selectionChanged();
}

GhosttyPoint QTermWidgetPrivate::viewportPoint(const QPoint &position) const {
    const QPoint local = position - q->contentsRect().topLeft();
    return gridPoint({std::clamp(local.x() / cellSize.width(), 0, gridSize.width() - 1),
                      std::clamp(local.y() / cellSize.height(), 0, gridSize.height() - 1)},
                     GHOSTTY_POINT_TAG_VIEWPORT);
}

void QTermWidgetPrivate::selectMouse(QMouseEvent *event, const GhosttySelectionGestureEventType type) {
    const auto gestureEvent = selectionEvents[type];
    GhosttyGridRef ref = {};
    if (ghostty_terminal_grid_ref(terminal, viewportPoint(event->pos()), &ref) != GHOSTTY_SUCCESS) {
        return;
    }
    ghostty_selection_gesture_event_set(gestureEvent, GHOSTTY_SELECTION_GESTURE_EVENT_OPT_REF, &ref);
    if (type != GHOSTTY_SELECTION_GESTURE_EVENT_TYPE_RELEASE) {
        // 手势位置和几何统一使用 Qt 逻辑像素；这里不涉及发给应用的鼠标协议坐标。
        const QPointF local = event->localPos() - q->contentsRect().topLeft();
        const GhosttySurfacePosition position = {.x = local.x(), .y = local.y()};
        ghostty_selection_gesture_event_set(gestureEvent, GHOSTTY_SELECTION_GESTURE_EVENT_OPT_POSITION, &position);
    }
    if (type == GHOSTTY_SELECTION_GESTURE_EVENT_TYPE_PRESS) {
        const uint64_t time = static_cast<uint64_t>(event->timestamp()) * 1000000;
        const uint64_t interval = static_cast<uint64_t>(QApplication::doubleClickInterval()) * 1000000;
        const double distance = QApplication::styleHints()->mouseDoubleClickDistance();
        // Qt 将第二次按下改发为 DoubleClick；仍交给内核按时间和距离识别双击、三击。
        // 没有时间戳的合成事件按单击处理，避免把无关的点击误判为连续点击。
        ghostty_selection_gesture_event_set(gestureEvent, GHOSTTY_SELECTION_GESTURE_EVENT_OPT_TIME_NS,
                                            time == 0 ? nullptr : &time);
        ghostty_selection_gesture_event_set(gestureEvent, GHOSTTY_SELECTION_GESTURE_EVENT_OPT_REPEAT_INTERVAL_NS,
                                            &interval);
        ghostty_selection_gesture_event_set(gestureEvent, GHOSTTY_SELECTION_GESTURE_EVENT_OPT_REPEAT_DISTANCE,
                                            &distance);
    } else if (type == GHOSTTY_SELECTION_GESTURE_EVENT_TYPE_DRAG) {
        const GhosttySelectionGestureGeometry geometry = {
            .columns = static_cast<uint32_t>(gridSize.width()),
            .cell_width = static_cast<uint32_t>(cellSize.width()),
            .padding_left = 0,
            .screen_height = static_cast<uint32_t>(q->contentsRect().height())};
        const bool rectangle = event->modifiers().testFlag(Qt::AltModifier);
        ghostty_selection_gesture_event_set(gestureEvent, GHOSTTY_SELECTION_GESTURE_EVENT_OPT_GEOMETRY, &geometry);
        ghostty_selection_gesture_event_set(gestureEvent, GHOSTTY_SELECTION_GESTURE_EVENT_OPT_RECTANGLE, &rectangle);
    }

    GhosttySelection value = GHOSTTY_INIT_SIZED(GhosttySelection);
    auto result = ghostty_selection_gesture_event(selectionGesture, terminal, gestureEvent, &value);
    if (type != GHOSTTY_SELECTION_GESTURE_EVENT_TYPE_RELEASE &&
        (result == GHOSTTY_SUCCESS || result == GHOSTTY_NO_VALUE)) {
        auto behavior = GHOSTTY_SELECTION_GESTURE_BEHAVIOR_CELL;
        if (ghostty_selection_gesture_get(selectionGesture, terminal, GHOSTTY_SELECTION_GESTURE_DATA_BEHAVIOR,
                                          &behavior) == GHOSTTY_SUCCESS) {
            // 内核的选词不会覆盖未写入的空格；双击空白时补齐本行范围，仍安装为内核选区。
            if (behavior == GHOSTTY_SELECTION_GESTURE_BEHAVIOR_WORD &&
                type == GHOSTTY_SELECTION_GESTURE_EVENT_TYPE_PRESS &&
                blankSelection(viewportPoint(event->pos()), &value)) {
                result = GHOSTTY_SUCCESS;
            } else if (behavior == GHOSTTY_SELECTION_GESTURE_BEHAVIOR_LINE) {
                // 空行没有可供内核选行的文字，此时用内核跟踪的锚点和当前格确定范围。
                if (result == GHOSTTY_NO_VALUE &&
                    ghostty_selection_gesture_get(selectionGesture, terminal, GHOSTTY_SELECTION_GESTURE_DATA_ANCHOR,
                                                  &value.start) == GHOSTTY_SUCCESS) {
                    value.end = ref;
                    result = GHOSTTY_SUCCESS;
                }
                // 三击及其后续拖动均保留行尾空白；有文字时沿用内核选出的软换行范围。
                if (result == GHOSTTY_SUCCESS && !expandToFullRows(&value)) {
                    return;
                }
            }
        }
    }
    if (result == GHOSTTY_SUCCESS) {
        ghostty_terminal_set(terminal, GHOSTTY_TERMINAL_OPT_SELECTION, &value);
    } else if (result == GHOSTTY_NO_VALUE && type == GHOSTTY_SELECTION_GESTURE_EVENT_TYPE_PRESS) {
        // 单击清除旧选区；未越过拖选阈值或锚点失效时则保留当前选区。
        ghostty_terminal_set(terminal, GHOSTTY_TERMINAL_OPT_SELECTION, nullptr);
    } else {
        return;
    }
    q->update();
    notifySelection();
}

bool QTermWidgetPrivate::blankSelection(GhosttyPoint point, GhosttySelection *selection) const {
    /** 读取本行 column 列到 ref，判断该格是否为空或只含普通空格／制表符。 */
    const auto readBlank = [&](const uint16_t column, GhosttyGridRef &ref) {
        point.value.coordinate.x = column;
        GhosttyCell cell = {};
        auto wide = GHOSTTY_CELL_WIDE_NARROW;
        if (ghostty_terminal_grid_ref(terminal, point, &ref) != GHOSTTY_SUCCESS ||
            ghostty_grid_ref_cell(&ref, &cell) != GHOSTTY_SUCCESS ||
            ghostty_cell_get(cell, GHOSTTY_CELL_DATA_WIDE, &wide) != GHOSTTY_SUCCESS ||
            wide != GHOSTTY_CELL_WIDE_NARROW) {
            return false;
        }
        // 宽字符占位格属于原字形，空格上的组合字符也不能误判为空白。
        uint32_t codepoint = 0;
        size_t length = 0;
        return ghostty_grid_ref_graphemes(&ref, &codepoint, 1, &length) == GHOSTTY_SUCCESS &&
               (length == 0 || codepoint == ' ' || codepoint == '\t');
    };

    GhosttyGridRef start = GHOSTTY_INIT_SIZED(GhosttyGridRef);
    if (!readBlank(point.value.coordinate.x, start)) {
        return false;
    }
    auto end = start;
    GhosttyGridRef next = GHOSTTY_INIT_SIZED(GhosttyGridRef);
    // 只沿当前显示行扩展，不跨软换行；全空行自然覆盖第 0 列至最后一列。
    while (start.x > 0 && readBlank(start.x - 1, next)) {
        start = next;
    }
    while (end.x + 1 < gridSize.width() && readBlank(end.x + 1, next)) {
        end = next;
    }
    selection->start = start;
    selection->end = end;
    selection->rectangle = false;
    return true;
}

bool QTermWidgetPrivate::expandToFullRows(GhosttySelection *selection) const {
    GhosttySelection expanded = GHOSTTY_INIT_SIZED(GhosttySelection);
    if (ghostty_terminal_selection_ordered(terminal, selection, GHOSTTY_SELECTION_ORDER_FORWARD, &expanded) !=
            GHOSTTY_SUCCESS ||
        ghostty_terminal_selection_adjust(terminal, &expanded, GHOSTTY_SELECTION_ADJUST_END_OF_LINE) !=
            GHOSTTY_SUCCESS) {
        return false;
    }
    // adjust 只移动逻辑终点，暂时交换端点后让内核将另一端移至行首。
    std::swap(expanded.start, expanded.end);
    if (ghostty_terminal_selection_adjust(terminal, &expanded, GHOSTTY_SELECTION_ADJUST_BEGINNING_OF_LINE) !=
        GHOSTTY_SUCCESS) {
        return false;
    }
    std::swap(expanded.start, expanded.end);
    *selection = expanded;
    return true;
}

QRect QTermWidgetPrivate::cursorRect() const {
    GhosttyRenderStateCursor cursor = GHOSTTY_INIT_SIZED(GhosttyRenderStateCursor);
    if (ghostty_render_state_get(render, GHOSTTY_RENDER_STATE_DATA_CURSOR, &cursor) != GHOSTTY_SUCCESS ||
        !cursor.viewport_has_value) {
        return {};
    }
    return {q->contentsRect().topLeft() +
                QPoint(cursor.viewport_x * cellSize.width(), cursor.viewport_y * cellSize.height()),
            cellSize};
}

void QTermWidgetPrivate::sendInput(const QByteArray &bytes) {
    if (bytes.isEmpty()) {
        return;
    }
    blinkOn = true;
    q->scrollToBottom();
    Q_EMIT q->dataToSend(bytes);
}

void QTermWidgetPrivate::writePty(GhosttyTerminal, void *userdata, const uint8_t *data, const size_t len) {
    auto *self = static_cast<QTermWidgetPrivate *>(userdata);
    const QByteArray bytes(reinterpret_cast<const char *>(data), static_cast<int>(len));
    // 内核回调期间不能重入 VT 写入。排队通知，并复制仅在回调期间有效的字节。
    QMetaObject::invokeMethod(
        self->q, [widget = self->q, bytes] { Q_EMIT widget->dataToSend(bytes); }, Qt::QueuedConnection);
}

void QTermWidgetPrivate::titleChange(GhosttyTerminal terminal, void *userdata) {
    auto *self = static_cast<QTermWidgetPrivate *>(userdata);
    GhosttyString value{};
    if (ghostty_terminal_get(terminal, GHOSTTY_TERMINAL_DATA_TITLE, &value) != GHOSTTY_SUCCESS) {
        return;
    }
    const QString title = QString::fromUtf8(reinterpret_cast<const char *>(value.ptr), static_cast<int>(value.len));
    QMetaObject::invokeMethod(
        self->q, [widget = self->q, title] { Q_EMIT widget->titleChanged(title); }, Qt::QueuedConnection);
}

void QTermWidgetPrivate::ringBell(GhosttyTerminal, void *userdata) {
    auto *self = static_cast<QTermWidgetPrivate *>(userdata);
    QMetaObject::invokeMethod(self->q, [widget = self->q] { Q_EMIT widget->bell(); }, Qt::QueuedConnection);
}

bool QTermWidgetPrivate::mouseReporting() const {
    bool enabled = false;
    ghostty_terminal_get(terminal, GHOSTTY_TERMINAL_DATA_MOUSE_TRACKING, &enabled);
    return enabled;
}

QByteArray QTermWidgetPrivate::encodeMouse(const GhosttyMouseAction action, const GhosttyMouseButton button,
                                           const QPointF &position, const Qt::KeyboardModifiers modifiers,
                                           const Qt::MouseButtons buttons) {
    const qreal scale = q->devicePixelRatioF();
    const QSize physicalCell = {qRound(cellSize.width() * scale), qRound(cellSize.height() * scale)};
    const bool pressed = buttons != Qt::NoButton;
    ghostty_mouse_encoder_setopt(mouseEncoder, GHOSTTY_MOUSE_ENCODER_OPT_ANY_BUTTON_PRESSED, &pressed);
    ghostty_mouse_event_set_action(mouseEvent, action);
    if (button == GHOSTTY_MOUSE_BUTTON_UNKNOWN) {
        ghostty_mouse_event_clear_button(mouseEvent);
    } else {
        ghostty_mouse_event_set_button(mouseEvent, button);
    }
    ghostty_mouse_event_set_mods(mouseEvent, terminalModifiers(modifiers));
    // 与 terminal_resize 的物理单元格保持一致，兼容高 DPI 和 SGR 像素坐标报告。
    const QPointF local = position - q->contentsRect().topLeft();
    const GhosttyMousePosition pixels = {
        .x = static_cast<float>(local.x() * physicalCell.width() / cellSize.width()),
        .y = static_cast<float>(local.y() * physicalCell.height() / cellSize.height())};
    ghostty_mouse_event_set_position(mouseEvent, pixels);
    auto output = QByteArray(128, '\0');
    size_t written = 0;
    if (const auto result = ghostty_mouse_encoder_encode(mouseEncoder, mouseEvent, output.data(), output.size(),
                                                         &written);
        result != GHOSTTY_SUCCESS) {
        return {};
    }
    output.resize(static_cast<int>(written));
    return output;
}

bool QTermWidgetPrivate::reportMouse(QMouseEvent *event) {
    if (!mouseReporting()) {
        return false;
    }
    auto action = GHOSTTY_MOUSE_ACTION_PRESS;
    Qt::MouseButton button = event->button();
    if (event->type() == QEvent::MouseMove) {
        action = GHOSTTY_MOUSE_ACTION_MOTION;
        // Qt 的移动事件没有 button，需从当前按住的按钮中选取报告对象。
        button = Qt::NoButton;
        for (const auto candidate :
             {Qt::LeftButton, Qt::MiddleButton, Qt::RightButton, Qt::BackButton, Qt::ForwardButton}) {
            if (event->buttons().testFlag(candidate)) {
                button = candidate;
                break;
            }
        }
    } else if (event->type() == QEvent::MouseButtonRelease) {
        action = GHOSTTY_MOUSE_ACTION_RELEASE;
    } else {
        // Qt 将第二次按下改发为 DoubleClick，仍需向应用报告一次正常的按下。
        q->setFocus(Qt::MouseFocusReason);
    }
    GhosttyMouseButton encodedButton = GHOSTTY_MOUSE_BUTTON_UNKNOWN;
    switch (button) {
        case Qt::LeftButton:
            encodedButton = GHOSTTY_MOUSE_BUTTON_LEFT;
            break;
        case Qt::MiddleButton:
            encodedButton = GHOSTTY_MOUSE_BUTTON_MIDDLE;
            break;
        case Qt::RightButton:
            encodedButton = GHOSTTY_MOUSE_BUTTON_RIGHT;
            break;
        case Qt::BackButton:
            encodedButton = GHOSTTY_MOUSE_BUTTON_EIGHT;
            break;
        case Qt::ForwardButton:
            encodedButton = GHOSTTY_MOUSE_BUTTON_NINE;
            break;
        default:
            break;
    }
    event->accept();
    if (encodedButton == GHOSTTY_MOUSE_BUTTON_UNKNOWN && action != GHOSTTY_MOUSE_ACTION_MOTION) {
        return true;
    }
    const QByteArray output = encodeMouse(action, encodedButton, event->localPos(), event->modifiers(),
                                          event->buttons());
    if (!output.isEmpty()) {
        Q_EMIT q->dataToSend(output);
    }
    return true;
}

void QTermWidgetPrivate::encodeKey(QKeyEvent *event, const bool release) {
    ghostty_key_encoder_setopt_from_terminal(keyEncoder, terminal);
    ghostty_key_event_set_action(keyEvent, release                 ? GHOSTTY_KEY_ACTION_RELEASE
                                           : event->isAutoRepeat() ? GHOSTTY_KEY_ACTION_REPEAT
                                                                   : GHOSTTY_KEY_ACTION_PRESS);
    ghostty_key_event_set_key(keyEvent, terminalKey(event));
    ghostty_key_event_set_mods(keyEvent, terminalModifiers(event->modifiers()));
    QString text = event->text();
    // Qt 对 Ctrl+C 等组合可能给出 C0 字符；由 Ghostty 根据键值生成控制字节。
    if (!text.isEmpty() && (text.front().unicode() < 0x20 || text.front().unicode() == 0x7f ||
                            (text.front().unicode() >= 0xf700 && text.front().unicode() <= 0xf8ff))) {
        text.clear();
    }
    const QByteArray utf8 = text.toUtf8();
    ghostty_key_event_set_utf8(keyEvent, utf8.isEmpty() ? nullptr : utf8.constData(), utf8.size());
    ghostty_key_event_set_composing(keyEvent, !preedit.isEmpty());
    ghostty_key_event_set_consumed_mods(
        keyEvent, text.isEmpty() ? 0 : terminalModifiers(event->modifiers()) & GHOSTTY_MODS_SHIFT);
    const int key = event->key();
    ghostty_key_event_set_unshifted_codepoint(keyEvent, key >= Qt::Key_A && key <= Qt::Key_Z ? 'a' + key - Qt::Key_A
                                                        : key >= 0x20 && key < 0x7f          ? key
                                                                                             : 0);
    QByteArray output(128, '\0');
    size_t written = 0;
    auto result = ghostty_key_encoder_encode(keyEncoder, keyEvent, output.data(), output.size(), &written);
    if (result == GHOSTTY_OUT_OF_SPACE && written <= static_cast<size_t>(std::numeric_limits<int>::max())) {
        output.resize(static_cast<int>(written));
        result = ghostty_key_encoder_encode(keyEncoder, keyEvent, output.data(), output.size(), &written);
    }
    ghostty_key_event_set_utf8(keyEvent, nullptr, 0);
    event->accept();
    if (result == GHOSTTY_SUCCESS) {
        output.resize(static_cast<int>(written));
        sendInput(output);
    }
}

QTermWidget::QTermWidget(QWidget *parent) : QWidget(parent), d_ptr(new QTermWidgetPrivate(this)) {
    Q_D(QTermWidget);
    d->initialize();
    setMouseTracking(true); // 1003 模式需要在没有按键按下时也报告移动。
    setFocusPolicy(Qt::StrongFocus);
    setAttribute(Qt::WA_InputMethodEnabled);
    setAttribute(Qt::WA_OpaquePaintEvent);
    setCursor(Qt::IBeamCursor);
    setFont(QFontDatabase::systemFont(QFontDatabase::FixedFont));
    d->resizeTerminal();
}

QTermWidget::~QTermWidget() = default;

std::optional<QTermWidget::Selection> QTermWidget::selection() const {
    Q_D(const QTermWidget);
    GhosttySelection value = GHOSTTY_INIT_SIZED(GhosttySelection);
    if (ghostty_terminal_get(d->terminal, GHOSTTY_TERMINAL_DATA_SELECTION, &value) != GHOSTTY_SUCCESS) {
        return std::nullopt;
    }
    GhosttyPointCoordinate start{}, end{};
    if (ghostty_terminal_point_from_grid_ref(d->terminal, &value.start, GHOSTTY_POINT_TAG_SCREEN, &start) !=
            GHOSTTY_SUCCESS ||
        ghostty_terminal_point_from_grid_ref(d->terminal, &value.end, GHOSTTY_POINT_TAG_SCREEN, &end) !=
            GHOSTTY_SUCCESS ||
        start.y > INT_MAX || end.y > INT_MAX) {
        return std::nullopt;
    }
    return Selection{.start = {start.x, static_cast<int>(start.y)},
                     .end = {end.x, static_cast<int>(end.y)},
                     .rectangular = value.rectangle};
}

bool QTermWidget::hasSelection() const {
    Q_D(const QTermWidget);
    GhosttySelection value = GHOSTTY_INIT_SIZED(GhosttySelection);
    return ghostty_terminal_get(d->terminal, GHOSTTY_TERMINAL_DATA_SELECTION, &value) == GHOSTTY_SUCCESS;
}

QString QTermWidget::selectedText() const {
    Q_D(const QTermWidget);
    GhosttyTerminalSelectionFormatOptions options = GHOSTTY_INIT_SIZED(GhosttyTerminalSelectionFormatOptions);
    options.emit = GHOSTTY_FORMATTER_FORMAT_PLAIN;
    options.unwrap = true;
    options.trim = true;
    uint8_t *bytes = nullptr;
    size_t length = 0;
    const auto result = ghostty_terminal_selection_format_alloc(d->terminal, nullptr, options, &bytes, &length);
    QString text;
    if (result == GHOSTTY_SUCCESS && length <= static_cast<size_t>(INT_MAX)) {
        text = QString::fromUtf8(reinterpret_cast<const char *>(bytes), static_cast<int>(length));
    }
    ghostty_free(nullptr, bytes, length);
    return text;
}

QSize QTermWidget::terminalSize() const {
    Q_D(const QTermWidget);
    return d->gridSize;
}

QSize QTermWidget::sizeHint() const {
    Q_D(const QTermWidget);
    const QMargins margins = contentsMargins();
    return {80 * d->cellSize.width() + margins.left() + margins.right(),
            24 * d->cellSize.height() + margins.top() + margins.bottom()};
}

QSize QTermWidget::minimumSizeHint() const {
    Q_D(const QTermWidget);
    const QMargins margins = contentsMargins();
    return d->cellSize + QSize(margins.left() + margins.right(), margins.top() + margins.bottom());
}

void QTermWidget::write(const QByteArray &data) {
    Q_D(QTermWidget);
    if (data.isEmpty()) {
        return;
    }
    const bool wasReporting = d->mouseReporting();
    ghostty_terminal_vt_write(d->terminal, reinterpret_cast<const uint8_t *>(data.constData()), data.size());
    ghostty_mouse_encoder_setopt_from_terminal(d->mouseEncoder, d->terminal);
    if (wasReporting != d->mouseReporting()) {
        ghostty_selection_gesture_reset(d->selectionGesture, d->terminal);
        d->wheelRemainder = {};
        d->pixelScrollRemainder = {};
    }
    update();
    d->notifySelection();
}

void QTermWidget::sendText(const QString &text) {
    Q_D(QTermWidget);
    d->sendInput(text.toUtf8());
}

void QTermWidget::pasteText(const QString &text) {
    Q_D(QTermWidget);
    if (text.isEmpty()) {
        return;
    }
    GhosttyTerminalModeConfig mode = {.mode = GHOSTTY_MODE_BRACKETED_PASTE, .value = false};
    ghostty_terminal_get(d->terminal, GHOSTTY_TERMINAL_DATA_MODE, &mode);
    QByteArray input = text.toUtf8();
    size_t length = 0;
    if (ghostty_paste_encode(input.data(), input.size(), mode.value, nullptr, 0, &length) != GHOSTTY_OUT_OF_SPACE ||
        length > INT_MAX) {
        return;
    }
    QByteArray output(static_cast<int>(length), '\0');
    if (ghostty_paste_encode(input.data(), input.size(), mode.value, output.data(), output.size(), &length) !=
        GHOSTTY_SUCCESS) {
        return;
    }
    output.resize(static_cast<int>(length));
    d->sendInput(output);
}

void QTermWidget::paste() {
    pasteText(QApplication::clipboard()->text());
}

void QTermWidget::copy() const {
    if (hasSelection()) {
        QApplication::clipboard()->setText(selectedText());
    }
}

void QTermWidget::selectAll() {
    Q_D(QTermWidget);
    ghostty_selection_gesture_reset(d->selectionGesture, d->terminal);
    GhosttySelection value = GHOSTTY_INIT_SIZED(GhosttySelection);
    // select_all 会裁掉首尾空白；直接指定完整网格边界，让空终端也能全选。
    // SCREEN 起点包含历史，ACTIVE 终点始终指向最新屏幕右下角，不受当前滚动位置影响。
    const auto start = gridPoint({0, 0}, GHOSTTY_POINT_TAG_SCREEN);
    const auto end = gridPoint({d->gridSize.width() - 1, d->gridSize.height() - 1}, GHOSTTY_POINT_TAG_ACTIVE);
    if (ghostty_terminal_grid_ref(d->terminal, start, &value.start) != GHOSTTY_SUCCESS ||
        ghostty_terminal_grid_ref(d->terminal, end, &value.end) != GHOSTTY_SUCCESS) {
        return;
    }
    if (ghostty_terminal_set(d->terminal, GHOSTTY_TERMINAL_OPT_SELECTION, &value) == GHOSTTY_SUCCESS) {
        update();
        d->notifySelection();
    }
}

bool QTermWidget::setSelection(const Selection &selection) {
    Q_D(QTermWidget);
    if (selection.start.x() < 0 || selection.end.x() < 0 || selection.start.y() < 0 || selection.end.y() < 0 ||
        selection.start.x() >= d->gridSize.width() || selection.end.x() >= d->gridSize.width()) {
        return false;
    }
    GhosttySelection value = GHOSTTY_INIT_SIZED(GhosttySelection);
    value.rectangle = selection.rectangular;
    if (ghostty_terminal_grid_ref(d->terminal, gridPoint(selection.start, GHOSTTY_POINT_TAG_SCREEN), &value.start) !=
            GHOSTTY_SUCCESS ||
        ghostty_terminal_grid_ref(d->terminal, gridPoint(selection.end, GHOSTTY_POINT_TAG_SCREEN), &value.end) !=
            GHOSTTY_SUCCESS) {
        return false;
    }
    if (ghostty_terminal_set(d->terminal, GHOSTTY_TERMINAL_OPT_SELECTION, &value) != GHOSTTY_SUCCESS) {
        return false;
    }
    ghostty_selection_gesture_reset(d->selectionGesture, d->terminal);
    update();
    d->notifySelection();
    return true;
}

void QTermWidget::clearSelection() {
    Q_D(QTermWidget);
    ghostty_terminal_set(d->terminal, GHOSTTY_TERMINAL_OPT_SELECTION, nullptr);
    update();
    d->notifySelection();
}

void QTermWidget::scrollLines(const int lines) {
    Q_D(QTermWidget);
    GhosttyTerminalScrollViewport scroll = {};
    scroll.tag = GHOSTTY_SCROLL_VIEWPORT_DELTA;
    scroll.value.delta = lines;
    ghostty_terminal_scroll_viewport(d->terminal, scroll);
    update();
}

void QTermWidget::scrollToBottom() {
    Q_D(QTermWidget);
    GhosttyTerminalScrollViewport scroll = {};
    scroll.tag = GHOSTTY_SCROLL_VIEWPORT_BOTTOM;
    ghostty_terminal_scroll_viewport(d->terminal, scroll);
    update();
}

void QTermWidget::reset() {
    Q_D(QTermWidget);
    ghostty_selection_gesture_reset(d->selectionGesture, d->terminal);
    d->preedit.clear();
    d->shortcutKeys.clear();
    d->wheelRemainder = {};
    d->pixelScrollRemainder = {};
    ghostty_terminal_reset(d->terminal);
    ghostty_mouse_encoder_setopt_from_terminal(d->mouseEncoder, d->terminal);
    update();
    d->notifySelection();
}

bool QTermWidget::event(QEvent *event) {
    if (event->type() == QEvent::ShortcutOverride) {
        event->accept();
        return true;
    }
    if (event->type() == QEvent::KeyPress) {
        keyPressEvent(dynamic_cast<QKeyEvent *>(event));
        return true;
    }
    return QWidget::event(event);
}

void QTermWidget::resizeEvent(QResizeEvent *event) {
    QWidget::resizeEvent(event);
    Q_D(QTermWidget);
    d->resizeTerminal();
}

void QTermWidget::changeEvent(QEvent *event) {
    QWidget::changeEvent(event);
    if (event->type() == QEvent::FontChange || event->type() == QEvent::ContentsRectChange) {
        Q_D(QTermWidget);
        d->resizeTerminal();
    }
}

void QTermWidget::keyPressEvent(QKeyEvent *event) {
    Q_D(QTermWidget);
    const auto modifiers = event->modifiers();
#ifdef Q_OS_MACOS
    const bool clipboardShortcut = modifiers == Qt::ControlModifier;
#else
    const bool clipboardShortcut = modifiers == (Qt::ControlModifier | Qt::ShiftModifier);
#endif
    // Qt 在 macOS 将 Command 表示为 ControlModifier；其他平台的 Ctrl+A 同样执行全选。
    const bool selectAllShortcut = modifiers == Qt::ControlModifier && event->key() == Qt::Key_A;
    if (selectAllShortcut ||
        (clipboardShortcut && (event->key() == Qt::Key_C || event->key() == Qt::Key_V || event->key() == Qt::Key_A))) {
        d->shortcutKeys.insert(event->key());
        event->accept();
        switch (event->key()) {
            case Qt::Key_C:
                copy();
                break;
            case Qt::Key_V:
                paste();
                break;
            case Qt::Key_A:
                selectAll();
                break;
            default:
                break;
        }
        return;
    }
    d->encodeKey(event, false);
}

void QTermWidget::keyReleaseEvent(QKeyEvent *event) {
    Q_D(QTermWidget);
    if (d->shortcutKeys.contains(event->key())) {
        if (!event->isAutoRepeat()) {
            d->shortcutKeys.remove(event->key());
        }
        event->accept();
        return;
    }
    // Qt 的自动重复可能附带伪释放事件，它不是用户实际松开按键。
    if (event->isAutoRepeat()) {
        event->accept();
        return;
    }
    d->encodeKey(event, true);
}

void QTermWidget::mousePressEvent(QMouseEvent *event) {
    Q_D(QTermWidget);
    if (d->reportMouse(event)) {
        return;
    }
    if (event->button() != Qt::LeftButton) {
        QWidget::mousePressEvent(event);
        return;
    }
    setFocus(Qt::MouseFocusReason);
    event->accept();
    d->selectMouse(event, GHOSTTY_SELECTION_GESTURE_EVENT_TYPE_PRESS);
}

void QTermWidget::mouseMoveEvent(QMouseEvent *event) {
    Q_D(QTermWidget);
    if (d->reportMouse(event)) {
        return;
    }
    if (!event->buttons().testFlag(Qt::LeftButton)) {
        QWidget::mouseMoveEvent(event);
        return;
    }
    event->accept();
    d->selectMouse(event, GHOSTTY_SELECTION_GESTURE_EVENT_TYPE_DRAG);
}

void QTermWidget::mouseReleaseEvent(QMouseEvent *event) {
    Q_D(QTermWidget);
    if (d->reportMouse(event)) {
        return;
    }
    if (event->button() == Qt::LeftButton) {
        event->accept();
        d->selectMouse(event, GHOSTTY_SELECTION_GESTURE_EVENT_TYPE_RELEASE);
    } else {
        QWidget::mouseReleaseEvent(event);
    }
}

void QTermWidget::mouseDoubleClickEvent(QMouseEvent *event) {
    mousePressEvent(event);
}

void QTermWidget::wheelEvent(QWheelEvent *event) {
    Q_D(QTermWidget);
    QPoint steps; ///< 已积累的横向和纵向滚动步数，正值表示左／上。
    if (!event->pixelDelta().isNull()) {
        d->pixelScrollRemainder += event->pixelDelta();
        steps = {d->pixelScrollRemainder.x() / d->cellSize.width(), d->pixelScrollRemainder.y() / d->cellSize.height()};
        d->pixelScrollRemainder = {d->pixelScrollRemainder.x() % d->cellSize.width(),
                                   d->pixelScrollRemainder.y() % d->cellSize.height()};
    } else {
        d->wheelRemainder += event->angleDelta();
        steps = {d->wheelRemainder.x() / 120, d->wheelRemainder.y() / 120};
        d->wheelRemainder = {d->wheelRemainder.x() % 120, d->wheelRemainder.y() % 120};
        steps *= QApplication::wheelScrollLines();
    }
    event->accept();
    if (!d->mouseReporting()) {
        scrollLines(-steps.y());
        return;
    }
    // 滚轮使用按钮 4..7 的按下报告，不发送释放；合并后一次发出，避免信号槽重入循环。
    QByteArray output;
    const auto vertical = steps.y() > 0 ? GHOSTTY_MOUSE_BUTTON_FOUR : GHOSTTY_MOUSE_BUTTON_FIVE;
    const auto horizontal = steps.x() > 0 ? GHOSTTY_MOUSE_BUTTON_SIX : GHOSTTY_MOUSE_BUTTON_SEVEN;
    for (int i = 0; i < std::abs(steps.y()); ++i) {
        output += d->encodeMouse(GHOSTTY_MOUSE_ACTION_PRESS, vertical, event->position(), event->modifiers(),
                                 event->buttons());
    }
    for (int i = 0; i < std::abs(steps.x()); ++i) {
        output += d->encodeMouse(GHOSTTY_MOUSE_ACTION_PRESS, horizontal, event->position(), event->modifiers(),
                                 event->buttons());
    }
    if (!output.isEmpty()) {
        Q_EMIT dataToSend(output);
    }
}

void QTermWidget::inputMethodEvent(QInputMethodEvent *event) {
    Q_D(QTermWidget);
    d->preedit = event->preeditString();
    event->accept();
    update();
    sendText(event->commitString());
}

QVariant QTermWidget::inputMethodQuery(const Qt::InputMethodQuery query) const {
    Q_D(const QTermWidget);
    if (query == Qt::ImCursorRectangle) {
        ghostty_render_state_update(d->render, d->terminal);
        return d->cursorRect();
    }
    if (query == Qt::ImFont) {
        return font();
    }
    if (query == Qt::ImEnabled) {
        return true;
    }
    if (query == Qt::ImHints) {
        return QVariant::fromValue(Qt::ImhNoPredictiveText);
    }
    return QWidget::inputMethodQuery(query);
}

void QTermWidget::paintEvent(QPaintEvent *event) {
    Q_D(QTermWidget);
    QPainter painter(this);
    // 仅重绘 Qt 指定的脏区域；先填底色，内核快照更新失败时也不会残留旧画面。
    painter.setClipRegion(event->region());
    painter.fillRect(rect(), QColor(30, 30, 30));
    // 将内核状态同步为本帧只读快照；后续颜色、行、选区和光标都从同一快照读取。
    if (ghostty_render_state_update(d->render, d->terminal) != GHOSTTY_SUCCESS) {
        return;
    }

    GhosttyRenderStateColors colors = GHOSTTY_INIT_SIZED(GhosttyRenderStateColors);
    ghostty_render_state_get(d->render, GHOSTTY_RENDER_STATE_DATA_COLORS, &colors);
    painter.fillRect(rect(), qtColor(colors.background));
    painter.setClipRect(contentsRect(), Qt::IntersectClip);
    GhosttyRenderStateCursor cursor = GHOSTTY_INIT_SIZED(GhosttyRenderStateCursor);
    ghostty_render_state_get(d->render, GHOSTTY_RENDER_STATE_DATA_CURSOR, &cursor);
    // 查看历史时光标可能不在视口内；失焦时保留静态轮廓，避免光标继续闪烁。
    const bool showCursor = cursor.visible && cursor.viewport_has_value &&
                            (!cursor.blinking || d->blinkOn || !hasFocus());
    const QColor cursorColor = qtColor(colors.cursor_has_value ? colors.cursor : colors.foreground);
    ghostty_render_state_get(d->render, GHOSTTY_RENDER_STATE_DATA_ROW_ITERATOR, &d->rows);
    int y = 0;

    // 迭代器已经按当前视口排列行，y 不包含被滚出视口的历史行。
    while (ghostty_render_state_row_iterator_next(d->rows)) {
        ghostty_render_state_row_get(d->rows, GHOSTTY_RENDER_STATE_ROW_DATA_CELLS, &d->cells);
        GhosttyRenderStateRowSelection selected = GHOSTTY_INIT_SIZED(GhosttyRenderStateRowSelection);
        const bool hasSelection = ghostty_render_state_row_get(d->rows, GHOSTTY_RENDER_STATE_ROW_DATA_SELECTION,
                                                               &selected) == GHOSTTY_SUCCESS;
        int x = 0;
        while (ghostty_render_state_row_cells_next(d->cells)) {
            GhosttyCell raw{};
            GhosttyCellWide wide = GHOSTTY_CELL_WIDE_NARROW;
            ghostty_render_state_row_cells_get(d->cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_RAW, &raw);
            ghostty_cell_get(raw, GHOSTTY_CELL_DATA_WIDE, &wide);
            // 宽字符由首格绘制两格，尾格不能再次填充背景，否则会抹掉中文字形。
            if (wide == GHOSTTY_CELL_WIDE_SPACER_TAIL) {
                ++x;
                continue;
            }

            const int columns = wide == GHOSTTY_CELL_WIDE_WIDE ? 2 : 1;
            const QRect cell(contentsRect().topLeft() + QPoint(x * d->cellSize.width(), y * d->cellSize.height()),
                             QSize(columns * d->cellSize.width(), d->cellSize.height()));
            if (!event->region().intersects(cell)) {
                ++x;
                continue;
            }

            GhosttyStyle style = GHOSTTY_INIT_SIZED(GhosttyStyle);
            ghostty_render_state_row_cells_get(d->cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_STYLE, &style);
            GhosttyColorRgb fg = colors.foreground, bg = colors.background;
            ghostty_render_state_row_cells_get(d->cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_FG_COLOR, &fg);
            ghostty_render_state_row_cells_get(d->cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_BG_COLOR, &bg);
            QColor foreground = qtColor(fg), background = qtColor(bg);
            // 颜色覆盖顺序为 SGR 反色、选区、实心光标，确保选中和光标位置可辨认。
            if (style.inverse) {
                std::swap(foreground, background);
            }

            if (hasSelection && x <= selected.end_x && x + columns - 1 >= selected.start_x) {
                foreground = palette().color(QPalette::HighlightedText);
                background = palette().color(QPalette::Highlight);
            }

            const bool atCursor = showCursor && y == cursor.viewport_y && cursor.viewport_x >= x &&
                                  cursor.viewport_x < x + columns;
            const bool blockCursor = atCursor && hasFocus() &&
                                     cursor.visual_style == GHOSTTY_RENDER_STATE_CURSOR_VISUAL_STYLE_BLOCK;
            if (blockCursor) {
                foreground = background;
                background = cursorColor;
            }

            // 隐藏或闪烁阶段只跳过文字，背景仍需重画以擦除上一帧的字形。
            painter.fillRect(cell, background);
            QFont cellFont = font();
            cellFont.setBold(style.bold);
            cellFont.setItalic(style.italic);
            cellFont.setUnderline(style.underline != GHOSTTY_SGR_UNDERLINE_NONE);
            cellFont.setStrikeOut(style.strikethrough);
            cellFont.setOverline(style.overline);
            painter.setFont(cellFont);
            if (style.faint) {
                foreground.setAlpha(128);
            }
            painter.setPen(foreground);

            if (!style.invisible && (!style.blink || d->blinkOn) && wide != GHOSTTY_CELL_WIDE_SPACER_HEAD) {
                // 以完整字素簇交给 Qt 排版，保留组合音标和 emoji；单元格宽度仍由 Ghostty 决定。
                QByteArray utf8(64, '\0');
                GhosttyBuffer buffer = {.ptr = reinterpret_cast<uint8_t *>(utf8.data()),
                                        .cap = static_cast<size_t>(utf8.size()),
                                        .len = 0};
                auto result = ghostty_render_state_row_cells_get(
                    d->cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_GRAPHEMES_UTF8, &buffer);
                if (result == GHOSTTY_OUT_OF_SPACE && buffer.len <= INT_MAX) {
                    utf8.resize(static_cast<int>(buffer.len));
                    buffer.ptr = reinterpret_cast<uint8_t *>(utf8.data());
                    buffer.cap = utf8.size();
                    result = ghostty_render_state_row_cells_get(
                        d->cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_GRAPHEMES_UTF8, &buffer);
                }
                if (result == GHOSTTY_SUCCESS && buffer.len > 0) {
                    painter.save();
                    // 回退字体的实际字宽可能超过终端网格；限制字形不能覆盖相邻单元格。
                    painter.setClipRect(cell, Qt::IntersectClip);
                    painter.drawText(cell.topLeft() + QPoint(0, d->ascent),
                                     QString::fromUtf8(utf8.constData(), static_cast<int>(buffer.len)));
                    painter.restore();
                }
            }

            // 非实心光标在文字之后叠加，避免轮廓、竖线或下划线被字形覆盖。
            if (atCursor && !blockCursor) {
                painter.setPen(cursorColor);
                if (!hasFocus() || cursor.visual_style == GHOSTTY_RENDER_STATE_CURSOR_VISUAL_STYLE_BLOCK_HOLLOW) {
                    painter.drawRect(cell.adjusted(0, 0, -1, -1));
                } else if (cursor.visual_style == GHOSTTY_RENDER_STATE_CURSOR_VISUAL_STYLE_BAR) {
                    painter.fillRect(QRect(cell.topLeft(), QSize(2, cell.height())), cursorColor);
                } else {
                    painter.fillRect(QRect(cell.bottomLeft() - QPoint(0, 1), QSize(cell.width(), 2)), cursorColor);
                }
            }
            ++x;
        }
        ++y;
    }

    // 输入法预编辑仅叠加在光标位置；提交前不写入内核，不影响历史和复制内容。
    if (!d->preedit.isEmpty() && cursor.viewport_has_value) {
        const QRect cursorRect = d->cursorRect();
        QFont preeditFont = font();
        preeditFont.setUnderline(true);
        painter.setFont(preeditFont);
        const QRect area(cursorRect.topLeft(),
                         QSize(QFontMetrics(preeditFont).horizontalAdvance(d->preedit), d->cellSize.height()));
        painter.fillRect(area, qtColor(colors.background));
        painter.setPen(qtColor(colors.foreground));
        painter.drawText(area.topLeft() + QPoint(0, d->ascent), d->preedit);
    }

    // 标记本次快照已消费，让下一次 update 追踪内核后续产生的变更。
    ghostty_render_state_clean(d->render);
}
