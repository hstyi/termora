#pragma once
#include "QToolButton"
#include "dialog.h"
#include "stores/entities.h"

#include <QStackedWidget>
#include <optional>

class NewHostDialog : public HeadlessDialog {
    Q_OBJECT
public:
    explicit NewHostDialog(QWidget *parent = nullptr);

private:
    void initViews();
    void initEvents();

public:
    std::optional<store::Host> host();

private:
    QStackedWidget *m_stacked_widget = nullptr;
    QToolButton *m_folder_button = nullptr;
    QToolButton *m_ssh2_button = nullptr;
};