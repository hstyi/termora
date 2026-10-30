#pragma once

#include <QByteArray>
#include <QScopedPointer>
#include <QWidget>
#include <optional>

// 封装 Ghostty 句柄和控件内部状态，公共头文件不依赖 Ghostty。
class QTermWidgetPrivate;

/**
 * 基于 Ghostty VT 内核的终端视图，不负责创建 shell 或建立 SSH 连接。
 * 所有接口均应在 GUI 线程调用；后台收到的数据应通过排队连接传给 write()。
 */
class QTermWidget : public QWidget {
    Q_OBJECT

public:
    /** 当前活动屏幕中的选区快照；坐标从零开始，包含两端，行号包含回滚历史。
     * 快照不会随输出或重排自动更新，需要时应重新调用 selection()。
     */
    struct Selection {
        QPoint start;             ///< 起点，x 为列、y 为行；保留拖选方向。
        QPoint end;               ///< 终点，x 为列、y 为行；保留拖选方向。
        bool rectangular = false; ///< 是否按矩形选取，而非按文本顺序选取。

        /** 比较 other 的端点和选区形状，用于识别选区变化。 */
        bool operator==(const Selection &other) const = default;
    };

    /** 创建终端，parent 为管理控件生命周期的父控件。 */
    explicit QTermWidget(QWidget *parent = nullptr);
    /** 释放终端内核和渲染资源。 */
    ~QTermWidget() override;

    /** 返回活动屏幕的当前选区；没有选区时返回 std::nullopt。 */
    std::optional<Selection> selection() const;
    /** 返回是否存在选区，选区内容可以为空白。 */
    bool hasSelection() const;
    /** 返回选中的 Unicode 文本，合并软换行并去掉行尾空白。 */
    QString selectedText() const;
    /** 返回终端字符网格尺寸，width 为列数，height 为行数。 */
    QSize terminalSize() const;
    /** 返回可容纳默认 80 列、24 行的建议像素尺寸。 */
    QSize sizeHint() const override;
    /** 返回至少容纳一个字符单元格的像素尺寸。 */
    QSize minimumSizeHint() const override;
    /** 查询 query 指定的输入法信息，包括候选框应跟随的光标矩形。 */
    QVariant inputMethodQuery(Qt::InputMethodQuery query) const override;

public Q_SLOTS:
    /** 将 SSH／PTY 输出 data 原样送入 Ghostty；支持分段 UTF-8 和转义序列，不作本地回显。 */
    void write(const QByteArray &data);
    /** 将 text 按 UTF-8 直接发给 SSH／PTY，不解释按键，也不添加粘贴边界。 */
    void sendText(const QString &text);
    /** 将 text 按终端的 bracketed paste 模式编码后发送，换行由 Ghostty 规范化。 */
    void pasteText(const QString &text);
    /** 发送系统剪贴板的文本，等价于对剪贴板内容调用 pasteText()。 */
    void paste();
    /** 将当前选区复制到系统剪贴板；无选区时保留原剪贴板。 */
    void copy() const;
    /** 选中活动屏幕的完整网格，包括回滚历史、首尾空白及尚未写入的空行。 */
    void selectAll();
    /** 安装 selection 指定的选区；坐标越界时返回 false 并保留原选区。 */
    bool setSelection(const QTermWidget::Selection &selection);
    /** 清除活动屏幕的选区。 */
    void clearSelection();
    /** 将视口滚动 lines 行；负数向历史方向滚动，正数向最新输出滚动。 */
    void scrollLines(int lines);
    /** 将视口移动到最新输出。 */
    void scrollToBottom();
    /** 重置终端模式、屏幕、历史和选区，不断开外部连接。 */
    void reset();

Q_SIGNALS:
    /** data 是应写入 SSH／PTY 的原始字节，包含键盘、粘贴和终端协议应答。 */
    void dataToSend(const QByteArray &data);
    /** size 是新的字符网格尺寸，连接层可据此调整远端 PTY。 */
    void terminalSizeChanged(const QSize &size);
    /** 当前选区的范围或文本发生变化，可重新读取 selection() 和 selectedText()。 */
    void selectionChanged();
    /** title 是终端程序通过 OSC 设置的新标题，由宿主决定如何展示。 */
    void titleChanged(const QString &title);
    /** 终端收到 BEL，宿主可选择播放提示音或显示提醒。 */
    void bell();

protected:
    /** 处理 event 中的快捷键竞争和 Tab，避免终端按键被焦点导航吞掉。 */
    bool event(QEvent *event) override;
    /** 根据 event 请求的更新区域绘制终端网格、选区和光标。 */
    void paintEvent(QPaintEvent *event) override;
    /** 根据 event 提供的新像素尺寸调整终端字符网格。 */
    void resizeEvent(QResizeEvent *event) override;
    /** 响应 event 中的字体变化，重新计算字符单元格。 */
    void changeEvent(QEvent *event) override;
    /** 将 event 中的按键按当前终端模式编码，或处理复制、粘贴快捷键。 */
    void keyPressEvent(QKeyEvent *event) override;
    /** 将 event 中的按键释放交给 Ghostty，以支持请求释放事件的键盘协议。 */
    void keyReleaseEvent(QKeyEvent *event) override;
    /** 应用开启鼠标报告时发送 event，否则在对应单元格建立拖选锚点。 */
    void mousePressEvent(QMouseEvent *event) override;
    /** 应用开启鼠标报告时发送 event 的移动，否则更新本地拖选；Alt 使用矩形选区。 */
    void mouseMoveEvent(QMouseEvent *event) override;
    /** 将 event 释放动作报告给应用，或结束本地拖选。 */
    void mouseReleaseEvent(QMouseEvent *event) override;
    /** 将 event 双击的第二次按下报告给应用，或按 Ghostty 单词边界选择文字。 */
    void mouseDoubleClickEvent(QMouseEvent *event) override;
    /** 应用开启鼠标报告时发送 event 的滚轮动作，否则滚动本地历史。 */
    void wheelEvent(QWheelEvent *event) override;
    /** 接收 event 中的输入法预编辑文本和已提交文本。 */
    void inputMethodEvent(QInputMethodEvent *event) override;

private:
    QScopedPointer<QTermWidgetPrivate> d_ptr; ///< 独占内核、渲染、输入和选区交互状态。
    Q_DECLARE_PRIVATE(QTermWidget)
};
