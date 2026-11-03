#pragma once
#include "QToolButton"
#include "dialog.h"
#include "stores/entities.h"

#include <QLabel>
#include <QPushButton>
#include <QStackedWidget>
#include <optional>

class NewHostDialog : public HeadlessDialog {
    Q_OBJECT
public:
    explicit NewHostDialog(QWidget *parent = nullptr);

private:
    void initViews();
    void initEvents();
    void updateFooter();
    void showValidationError();

public:
    std::optional<store::Host> host();

public Q_SLOTS:
    void accept() override;

Q_SIGNALS:
    void testConnectionRequested(const store::Host &host);

private:
    QStackedWidget *m_stacked_widget = nullptr;
    QToolButton *m_folder_button = nullptr;
    QToolButton *m_ssh2_button = nullptr;
    QWidget *m_protocol_bar = nullptr;
    QWidget *m_footer_widget = nullptr;
    QLabel *m_message_label = nullptr;
    QPushButton *m_cancel_button = nullptr;
    QPushButton *m_test_connection_button = nullptr;
    QPushButton *m_ok_button = nullptr;
    std::optional<store::Host> m_host;
};
