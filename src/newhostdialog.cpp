#include "newhostdialog.h"

#include "hosttreemodel.h"
#include "icons.h"
#include "itemdelegate.h"
#include "macscrollstyle.h"
#include "stores/hoststore.h"
#include "tablewidget.h"
#include "treeview.h"

#include <QButtonGroup>
#include <QCheckBox>
#include <QComboBox>
#include <QDateTime>
#include <QDialogButtonBox>
#include <QGridLayout>
#include <QGroupBox>
#include <QHeaderView>
#include <QLabel>
#include <QLineEdit>
#include <QListView>
#include <QListWidget>
#include <QPushButton>
#include <QRegularExpression>
#include <QScrollArea>
#include <QSet>
#include <QSortFilterProxyModel>
#include <QSpinBox>
#include <QStyle>
#include <QStyleOptionViewItem>
#include <QStyledItemDelegate>
#include <QTabBar>
#include <QTabWidget>
#include <QTableWidget>
#include <QTextDocument>
#include <QTextEdit>
#include <QToolButton>
#include <QUuid>
#include <QVBoxLayout>
#include <QtMath>
#include <algorithm>
#include <limits>
#include <ranges>
#include <utility>

#if QT_VERSION < QT_VERSION_CHECK(6, 0, 0)
#include <QTextCodec>
#endif

namespace {
    class NewWidget : public QWidget {

    public:
        virtual std::optional<store::Host> host() = 0;

        virtual bool canTest() const {
            return false;
        }
    };

    QComboBox *createComboBox(QWidget *parent) {
        auto *combo = new QComboBox(parent);
        auto *view = new QListView(combo);
        view->setMouseTracking(true);
        view->setVerticalScrollMode(QAbstractItemView::ScrollPerPixel);
        combo->setView(view);
        // 默认的 QItemDelegate 不使用 QListView::item 的 QSS 绘制规则。
        combo->setItemDelegate(new ItemDelegate(view));
        MacScrollStyle::use(view);
        // macOS 默认会跳过非编辑下拉框；显式允许 Tab 和鼠标聚焦。
        combo->setFocusPolicy(Qt::StrongFocus);
        return combo;
    }

    QTableWidget *createTable(QWidget *parent, const QStringList &headers) {
        auto *table = new TableWidget(parent);
        table->setColumnCount(headers.size());
        table->setHorizontalHeaderLabels(headers);
        return table;
    }

    QList<int> selectedRows(const QTableWidget *table) {
        QList<int> rows;
        for (const auto &index : table->selectionModel()->selectedRows()) {
            rows.push_back(index.row());
        }
        std::sort(rows.begin(), rows.end());
        return rows;
    }

    void setTextEditRows(QTextEdit *edit, int rows) {
        edit->setAcceptRichText(false);
        edit->setTabChangesFocus(true);
        edit->setFixedHeight(rows * edit->fontMetrics().lineSpacing() + qCeil(2 * edit->document()->documentMargin()) +
                             2 * edit->frameWidth());
    }

    class TunnelingDialog : public Dialog {
    public:
        explicit TunnelingDialog(const store::Tunneling &tunneling, QWidget *parent) : Dialog(parent) {
            setWindowTitle(tr("Tunnel"));
            m_name_edit = new QLineEdit(tunneling.name, this);
            m_type_combobox = createComboBox(this);
            m_type_combobox->addItem(tr("Local"), static_cast<int>(store::TunnelingType::Local));
            m_type_combobox->addItem(tr("Remote"), static_cast<int>(store::TunnelingType::Remote));
            m_type_combobox->addItem(tr("Dynamic"), static_cast<int>(store::TunnelingType::Dynamic));
            m_type_combobox->setCurrentIndex(m_type_combobox->findData(static_cast<int>(tunneling.type)));
            m_source_host_edit = new QLineEdit(tunneling.sourceHost, this);
            m_source_port_spinbox = new QSpinBox(this);
            m_source_port_spinbox->setRange(1, 65535);
            m_source_port_spinbox->setValue(tunneling.sourcePort);
            m_destination_host_edit = new QLineEdit(tunneling.destinationHost, this);
            m_destination_port_spinbox = new QSpinBox(this);
            m_destination_port_spinbox->setRange(1, 65535);
            m_destination_port_spinbox->setValue(tunneling.destinationPort);
            m_error_label = new QLabel(this);
            m_error_label->setWordWrap(true);
            m_error_label->hide();
            m_buttons = new QDialogButtonBox(QDialogButtonBox::Ok | QDialogButtonBox::Cancel, this);

            auto *form = new QGridLayout;
            form->setColumnStretch(1, 1);
            form->addWidget(new QLabel(tr("Name:"), this), 0, 0);
            form->addWidget(m_name_edit, 0, 1, 1, 2);
            form->addWidget(new QLabel(tr("Type:"), this), 1, 0);
            form->addWidget(m_type_combobox, 1, 1, 1, 2);
            form->addWidget(new QLabel(tr("Source:"), this), 2, 0);
            form->addWidget(m_source_host_edit, 2, 1);
            form->addWidget(m_source_port_spinbox, 2, 2);
            form->addWidget(new QLabel(tr("Destination:"), this), 3, 0);
            form->addWidget(m_destination_host_edit, 3, 1);
            form->addWidget(m_destination_port_spinbox, 3, 2);
            auto *layout = new QVBoxLayout(this);
            layout->addLayout(form);
            layout->addWidget(m_error_label);
            layout->addWidget(m_buttons);
            connect(m_type_combobox, QOverload<int>::of(&QComboBox::currentIndexChanged), this,
                    [this] { updateDestination(); });
            connect(m_buttons, &QDialogButtonBox::rejected, this, &QDialog::reject);
            connect(m_buttons, &QDialogButtonBox::accepted, this, [this] {
                for (auto *edit : {m_name_edit, m_source_host_edit, m_destination_host_edit}) {
                    if (edit->isEnabled() && edit->text().trimmed().isEmpty()) {
                        m_error_label->setText(tr("Enter a name, source address and destination address."));
                        m_error_label->show();
                        edit->setFocus();
                        return;
                    }
                }
                accept();
            });
            updateDestination();
            resize(480, sizeHint().height());
        }

        store::Tunneling tunneling() const {
            store::Tunneling tunneling;
            tunneling.name = m_name_edit->text().trimmed();
            tunneling.type = static_cast<store::TunnelingType>(m_type_combobox->currentData().toInt());
            tunneling.sourceHost = m_source_host_edit->text().trimmed();
            tunneling.sourcePort = m_source_port_spinbox->value();
            if (tunneling.type != store::TunnelingType::Dynamic) {
                tunneling.destinationHost = m_destination_host_edit->text().trimmed();
                tunneling.destinationPort = m_destination_port_spinbox->value();
            } else {
                tunneling.destinationHost.clear();
                tunneling.destinationPort = 0;
            }
            return tunneling;
        }

    private:
        void updateDestination() {
            const bool enabled = m_type_combobox->currentData().toInt() !=
                                 static_cast<int>(store::TunnelingType::Dynamic);
            m_destination_host_edit->setEnabled(enabled);
            m_destination_port_spinbox->setEnabled(enabled);
        }

        QLineEdit *m_name_edit = nullptr;
        QComboBox *m_type_combobox = nullptr;
        QLineEdit *m_source_host_edit = nullptr;
        QSpinBox *m_source_port_spinbox = nullptr;
        QLineEdit *m_destination_host_edit = nullptr;
        QSpinBox *m_destination_port_spinbox = nullptr;
        QLabel *m_error_label = nullptr;
        QDialogButtonBox *m_buttons = nullptr;
    };

    class JumpHostFilterModel : public QSortFilterProxyModel {
    public:
        JumpHostFilterModel(QSet<QString> ids, QObject *parent) : QSortFilterProxyModel(parent), m_ids(std::move(ids)) {
            setRecursiveFilteringEnabled(true);
        }

    protected:
        bool filterAcceptsRow(int row, const QModelIndex &parent) const override {
            return m_ids.contains(sourceModel()->index(row, 0, parent).data(Qt::UserRole).toString());
        }

    private:
        QSet<QString> m_ids;
    };

    class JumpHostDialog : public Dialog {
    public:
        JumpHostDialog(const QSet<QString> &excluded, QWidget *parent) : Dialog(parent) {
            setWindowTitle(tr("Jump Hosts"));
            for (const auto &host : HostStore::instance()->hosts()) {
                if ((host.protocol.compare("ssh", Qt::CaseInsensitive) == 0) && !excluded.contains(host.id)) {
                    m_allowed_ids.insert(host.id);
                }
            }
            m_tree_model = new HostTreeModel(this);
            m_filter_model = new JumpHostFilterModel(m_allowed_ids, this);
            m_filter_model->setSourceModel(m_tree_model);
            m_treeview = new TreeView(this);
            m_treeview->setModel(m_filter_model);
            m_treeview->setSelectionMode(QAbstractItemView::ExtendedSelection);
            m_treeview->expandAll();
            m_buttons = new QDialogButtonBox(QDialogButtonBox::Ok | QDialogButtonBox::Cancel, this);
            m_buttons->button(QDialogButtonBox::Ok)->setEnabled(false);
            auto *layout = new QVBoxLayout(this);
            layout->addWidget(m_treeview, 1);
            layout->addWidget(m_buttons);
            connect(m_buttons, &QDialogButtonBox::rejected, this, &QDialog::reject);
            connect(m_buttons, &QDialogButtonBox::accepted, this, &QDialog::accept);
            connect(m_treeview->selectionModel(), &QItemSelectionModel::selectionChanged, this,
                    [this] { m_buttons->button(QDialogButtonBox::Ok)->setEnabled(!hostIds().isEmpty()); });
            resize(480, 420);
        }

        QStringList hostIds() const {
            QStringList ids;
            for (const auto &index : m_treeview->selectionModel()->selectedRows()) {
                const auto id = index.data(Qt::UserRole).toString();
                if (m_allowed_ids.contains(id)) {
                    ids.push_back(id);
                }
            }
            return ids;
        }

    private:
        QSet<QString> m_allowed_ids;
        HostTreeModel *m_tree_model = nullptr;
        JumpHostFilterModel *m_filter_model = nullptr;
        TreeView *m_treeview = nullptr;
        QDialogButtonBox *m_buttons = nullptr;
    };

    class LoginScriptDialog : public Dialog {
    public:
        LoginScriptDialog(const store::LoginScript &script, QWidget *parent) : Dialog(parent) {
            setWindowTitle(tr("Login Scripts"));
            m_expect_edit = new QLineEdit(script.expect, this);
            m_expect_edit->setPlaceholderText(tr("Optional"));
            m_send_edit = new QLineEdit(script.send, this);
            m_regex_button = new QToolButton(this);
            m_regex_button->setText(".*");
            m_regex_button->setToolTip(tr("Regular expression"));
            m_regex_button->setCheckable(true);
            m_regex_button->setChecked(script.regex);
            m_regex_button->setIconSize(QSize(16, 16));
            m_regex_button->setIcon(icons::regex());

            m_match_case_button = new QToolButton(this);
            m_match_case_button->setText("Aa");
            m_match_case_button->setToolTip(tr("Match case"));
            m_match_case_button->setCheckable(true);
            m_match_case_button->setChecked(script.matchCase);
            m_match_case_button->setIconSize(QSize(16, 16));
            m_match_case_button->setIcon(icons::matchCase());

            m_error_label = new QLabel(this);
            m_error_label->setWordWrap(true);
            m_error_label->hide();

            m_buttons = new QDialogButtonBox(QDialogButtonBox::Ok | QDialogButtonBox::Cancel, this);
            auto *expect = new QHBoxLayout;
            expect->setSpacing(6);
            expect->addWidget(m_expect_edit, 1);
            expect->addWidget(m_regex_button);
            expect->addWidget(m_match_case_button);

            auto *form = new QGridLayout;
            form->setColumnStretch(1, 1);
            form->addWidget(new QLabel(tr("Expect:"), this), 0, 0);
            form->addLayout(expect, 0, 1);
            form->addWidget(new QLabel(tr("Send:"), this), 1, 0);
            form->addWidget(m_send_edit, 1, 1);

            auto *layout = new QVBoxLayout(this);
            layout->addLayout(form);
            layout->addWidget(m_error_label);
            layout->addWidget(m_buttons);
            connect(m_buttons, &QDialogButtonBox::rejected, this, &QDialog::reject);
            connect(m_buttons, &QDialogButtonBox::accepted, this, [this] {
                if (m_send_edit->text().trimmed().isEmpty()) {
                    m_error_label->setText(tr("Enter the text to send."));
                    m_error_label->show();
                    m_send_edit->setFocus();
                    return;
                }
                if (const QRegularExpression expression(m_expect_edit->text());
                    m_regex_button->isChecked() && !expression.isValid()) {
                    m_error_label->setText(expression.errorString());
                    m_error_label->show();
                    m_expect_edit->setFocus();
                    return;
                }
                accept();
            });
            resize(480, QDialog::sizeHint().height());
        }

        store::LoginScript script() const {
            return {.expect = m_expect_edit->text(),
                    .send = m_send_edit->text(),
                    .regex = m_regex_button->isChecked(),
                    .matchCase = m_match_case_button->isChecked()};
        }

    private:
        QLineEdit *m_expect_edit = nullptr;
        QLineEdit *m_send_edit = nullptr;
        QToolButton *m_regex_button = nullptr;
        QToolButton *m_match_case_button = nullptr;
        QLabel *m_error_label = nullptr;
        QDialogButtonBox *m_buttons = nullptr;
    };

    class NewHostOptionsWidget : public NewWidget {
    protected:
        enum class Mode {
            Host,
            Folder,
        };

        explicit NewHostOptionsWidget(Mode mode) : m_mode(mode) {
            initViews();
            initTabOrder();
            initEvents();
            if (m_mode == Mode::Host) {
                updateAuthentication();
            }
            updateProxy();
        }

    public:
        std::optional<store::Host> host() override {
            store::Host host = {};
            host.name = m_name_edit->text().trimmed();
            host.proxy.type = static_cast<store::ProxyType>(m_proxy_type_combobox->currentData().toInt());
            if (!requireField(m_name_edit) || (m_mode == Mode::Host && !requireField(m_host_edit)) ||
                (host.proxy.type != store::ProxyType::No && !requireField(m_proxy_host_edit))) {
                return std::nullopt;
            }

            host.id = QUuid::createUuid().toString(QUuid::WithoutBraces);
            host.protocol = m_mode == Mode::Folder ? QStringLiteral("folder") : QStringLiteral("ssh");
            if (m_mode == Mode::Host) {
                host.host = m_host_edit->text().trimmed();
                host.port = m_port_spinbox->value();
                host.username = m_username_edit->text().trimmed();
                host.authentication.type = static_cast<store::AuthenticationType>(
                    m_authentication_type_combobox->currentData().toInt());
                if (host.authentication.type == store::AuthenticationType::Password) {
                    host.authentication.password = m_password_edit->text();
                }
            }

            host.remark = m_remark_edit->toPlainText();
            if (host.proxy.type != store::ProxyType::No) {
                host.proxy.host = m_proxy_host_edit->text().trimmed();
                host.proxy.port = m_proxy_port_spinbox->value();
                host.proxy.authenticationType = static_cast<store::AuthenticationType>(
                    m_proxy_authentication_type_combobox->currentData().toInt());
                if (host.proxy.authenticationType == store::AuthenticationType::Password) {
                    host.proxy.username = m_proxy_username_edit->text().trimmed();
                    host.proxy.password = m_proxy_password_edit->text();
                }
            }

            for (const auto &jumpHost : m_jump_hosts) {
                host.options.jumpHosts.push_back(jumpHost.id);
            }

            host.options.loginScripts = m_login_scripts;
            host.options.encoding = m_encoding_combobox->currentText();
            host.options.env = m_environment_edit->toPlainText();
            host.options.startupCommand = m_startup_command_edit->text();
            host.options.timeout = m_timeout_spinbox->value();
            host.options.heartbeatInterval = m_heartbeat_interval_spinbox->value();
            host.options.backspace = m_backspace_combobox->currentData().toString();
            host.options.altModifier = m_alt_modifier_combobox->currentData().toString();
            host.options.keywordHighlightSetId = m_highlight_set_combobox->currentData().toString();
            if (m_mode == Mode::Host) {
                host.options.tunnelings = m_tunnelings;
                host.options.enableX11Forwarding = m_x11_forwarding_checkbox->isChecked();
                host.options.x11Forwarding = m_x11_server_edit->text().trimmed();
                host.options.forwardAgent = m_forward_agent_checkbox->isChecked();
                host.options.sftpDefaultDirectory = m_sftp_default_directory_edit->text().trimmed();
            }
            host.version = 1;
            host.createdAt = QDateTime::currentMSecsSinceEpoch();
            host.updatedAt = host.createdAt;
            return host;
        }

    private:
        void initViews() {
            m_list_widget = new QListWidget(this);
            m_list_widget->setItemDelegate(new ItemDelegate(m_list_widget));
            m_list_widget->setIconSize(QSize(16, 16));
            m_list_widget->setFixedWidth(180);
            m_list_widget->setStyleSheet(QString("QListWidget { background-color: %1; border-right: 1px solid %2; "
                                                 "border-radius: 0; } QListView::item { margin: 1px 0; } ")
                                             .arg(palette().window().color().name())
                                             .arg(palette().mid().color().name()));

            m_list_widget->addItem(new QListWidgetItem(icons::settings(), tr("Properties")));
            m_list_widget->addItem(new QListWidgetItem(icons::network(), tr("Proxy")));
            if (m_mode == Mode::Host) {
                m_list_widget->addItem(new QListWidgetItem(icons::show_write_access(), tr("Tunnel")));
            }
            m_list_widget->addItem(new QListWidgetItem(icons::server(), tr("Jump Hosts")));
            m_list_widget->addItem(new QListWidgetItem(icons::terminal(), tr("Terminal")));
            if (m_mode == Mode::Host) {
                m_list_widget->addItem(new QListWidgetItem(icons::folder(), tr("Transfer")));
            }

            m_stacked_widget = new QStackedWidget(this);

            initProperties();
            initProxy();
            initJumpHosts();
            initTerminal();

            if (m_mode == Mode::Host) {
                initTunnel();
                initTransfer();
            }

            m_stacked_widget->addWidget(m_properties_widget);
            m_stacked_widget->addWidget(m_proxy_widget);
            if (m_mode == Mode::Host) {
                m_stacked_widget->addWidget(m_tunnel_widget);
            }
            m_stacked_widget->addWidget(m_jump_hosts_widget);
            m_stacked_widget->addWidget(m_terminal_widget);
            if (m_mode == Mode::Host) {
                m_stacked_widget->addWidget(m_transfer_widget);
            }
            m_list_widget->setCurrentRow(0);

            auto *layout = new QHBoxLayout(this);
            layout->setContentsMargins(0, 0, 0, 0);
            layout->setSpacing(0);
            layout->addWidget(m_list_widget);
            layout->addWidget(m_stacked_widget, 1);
            setFocusProxy(m_name_edit);
        }

        bool requireField(QLineEdit *field) {
            if (!field->text().trimmed().isEmpty()) {
                return true;
            }
            for (int row = 0; row < m_stacked_widget->count(); ++row) {
                if (m_stacked_widget->widget(row)->isAncestorOf(field)) {
                    m_list_widget->setCurrentRow(row);
                    break;
                }
            }
            field->setFocus(Qt::OtherFocusReason);
            return false;
        }

        void initTabOrder() {
            setTabOrder(m_list_widget, m_name_edit);
            if (m_mode == Mode::Folder) {
                setTabOrder(m_name_edit, m_remark_edit);
                return;
            }

            // 显式使用表单顺序，避免提前创建的备注框打乱焦点链。
            setTabOrder(m_name_edit, m_host_edit);
            setTabOrder(m_host_edit, m_port_spinbox);
            setTabOrder(m_port_spinbox, m_username_edit);
            setTabOrder(m_username_edit, m_authentication_type_combobox);
            setTabOrder(m_authentication_type_combobox, m_password_edit);
            setTabOrder(m_password_edit, m_remark_edit);
        }

        void initProperties() {
            m_properties_widget = new QWidget(m_stacked_widget);
            m_name_edit = new QLineEdit(m_properties_widget);
            m_remark_edit = new QTextEdit(m_properties_widget);
            MacScrollStyle::use(m_remark_edit);
            setTextEditRows(m_remark_edit, 8);

            if (m_mode == Mode::Folder) {
                auto *layout = new QGridLayout(m_properties_widget);
                layout->setColumnStretch(1, 1);
                layout->addWidget(createLabel(tr("Name:"), m_name_edit), 0, 0);
                layout->addWidget(m_name_edit, 0, 1);
                layout->addWidget(createLabel(tr("Remark:"), m_remark_edit), 1, 0, Qt::AlignTop);
                layout->addWidget(m_remark_edit, 1, 1);
                layout->setRowStretch(2, 1);
                return;
            }

            m_host_edit = new QLineEdit(m_properties_widget);
            m_port_spinbox = new QSpinBox(m_properties_widget);
            m_port_spinbox->setRange(1, 65535);
            m_port_spinbox->setValue(22);
            m_port_spinbox->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Fixed);
            m_username_edit = new QLineEdit(m_properties_widget);
            m_authentication_type_combobox = createComboBox(m_properties_widget);
            m_authentication_type_combobox->addItem(tr("None"), static_cast<int>(store::AuthenticationType::No));
            m_authentication_type_combobox->addItem(tr("Password"),
                                                    static_cast<int>(store::AuthenticationType::Password));
            m_authentication_type_combobox->addItem(tr("Public Key"),
                                                    static_cast<int>(store::AuthenticationType::PublicKey));
            m_authentication_type_combobox->addItem(tr("SSH Agent"),
                                                    static_cast<int>(store::AuthenticationType::SSHAgent));
            m_authentication_type_combobox->addItem(tr("Keyboard Interactive"),
                                                    static_cast<int>(store::AuthenticationType::KeyboardInteractive));
            m_authentication_type_combobox->setCurrentIndex(1);
            m_password_edit = new QLineEdit(m_properties_widget);
            m_password_edit->setEchoMode(QLineEdit::Password);

            auto *layout = new QGridLayout(m_properties_widget);
            layout->setSpacing(6);
            layout->setColumnStretch(1, 2);
            layout->setColumnStretch(3, 1);
            layout->addWidget(createLabel(tr("Name:"), m_name_edit), 0, 0);
            layout->addWidget(m_name_edit, 0, 1, 1, 3);
            layout->addWidget(createLabel(tr("Host:"), m_host_edit), 1, 0);
            layout->addWidget(m_host_edit, 1, 1);
            layout->addWidget(createLabel(tr("Port:"), m_port_spinbox), 1, 2);
            layout->addWidget(m_port_spinbox, 1, 3);
            layout->addWidget(createLabel(tr("Username:"), m_username_edit), 2, 0);
            layout->addWidget(m_username_edit, 2, 1, 1, 3);
            layout->addWidget(createLabel(tr("Authentication:"), m_authentication_type_combobox), 3, 0);
            layout->addWidget(m_authentication_type_combobox, 3, 1, 1, 3);
            layout->addWidget(createLabel(tr("Password:"), m_password_edit), 4, 0);
            layout->addWidget(m_password_edit, 4, 1, 1, 3);
            layout->addWidget(createLabel(tr("Remark:"), m_remark_edit), 5, 0, Qt::AlignTop);
            layout->addWidget(m_remark_edit, 5, 1, 1, 3);
            layout->setRowStretch(6, 1);
        }

        void initProxy() {
            m_proxy_widget = new QWidget(m_stacked_widget);
            m_proxy_type_combobox = createComboBox(m_proxy_widget);
            m_proxy_type_combobox->addItem(tr("None"), static_cast<int>(store::ProxyType::No));
            m_proxy_type_combobox->addItem(tr("HTTP"), static_cast<int>(store::ProxyType::HTTP));
            m_proxy_type_combobox->addItem(tr("SOCKS5"), static_cast<int>(store::ProxyType::SOCKS5));
            m_proxy_host_edit = new QLineEdit(m_proxy_widget);
            m_proxy_port_spinbox = new QSpinBox(m_proxy_widget);
            m_proxy_port_spinbox->setRange(1, 65535);
            m_proxy_port_spinbox->setValue(1080);
            m_proxy_port_spinbox->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Fixed);
            m_proxy_authentication_type_combobox = createComboBox(m_proxy_widget);
            m_proxy_authentication_type_combobox->addItem(tr("None"), static_cast<int>(store::AuthenticationType::No));
            m_proxy_authentication_type_combobox->addItem(tr("Password"),
                                                          static_cast<int>(store::AuthenticationType::Password));
            m_proxy_username_edit = new QLineEdit(m_proxy_widget);
            m_proxy_password_edit = new QLineEdit(m_proxy_widget);
            m_proxy_password_edit->setEchoMode(QLineEdit::Password);

            auto *layout = new QGridLayout(m_proxy_widget);
            layout->setSpacing(6);
            layout->setColumnStretch(1, 2);
            layout->setColumnStretch(3, 1);
            layout->addWidget(createLabel(tr("Proxy Type:"), m_proxy_type_combobox), 0, 0);
            layout->addWidget(m_proxy_type_combobox, 0, 1, 1, 3);
            layout->addWidget(createLabel(tr("Host:"), m_proxy_host_edit), 1, 0);
            layout->addWidget(m_proxy_host_edit, 1, 1);
            layout->addWidget(createLabel(tr("Port:"), m_proxy_port_spinbox), 1, 2);
            layout->addWidget(m_proxy_port_spinbox, 1, 3);
            layout->addWidget(createLabel(tr("Authentication:"), m_proxy_authentication_type_combobox), 2, 0);
            layout->addWidget(m_proxy_authentication_type_combobox, 2, 1, 1, 3);
            layout->addWidget(createLabel(tr("Username:"), m_proxy_username_edit), 3, 0);
            layout->addWidget(m_proxy_username_edit, 3, 1, 1, 3);
            layout->addWidget(createLabel(tr("Password:"), m_proxy_password_edit), 4, 0);
            layout->addWidget(m_proxy_password_edit, 4, 1, 1, 3);
            layout->setRowStretch(5, 1);
        }

        void initTunnel() {
            m_tunnel_widget = new QWidget(m_stacked_widget);
            m_tunnel_table = createTable(m_tunnel_widget, {tr("Name"), tr("Type"), tr("Source"), tr("Destination")});
            m_add_tunnel_button = new QPushButton(tr("Add"), m_tunnel_widget);
            m_edit_tunnel_button = new QPushButton(tr("Edit"), m_tunnel_widget);
            m_remove_tunnel_button = new QPushButton(tr("Delete"), m_tunnel_widget);
            m_edit_tunnel_button->setEnabled(false);
            m_remove_tunnel_button->setEnabled(false);
            auto *buttons = new QHBoxLayout;
            buttons->setSpacing(6);
            buttons->addWidget(m_add_tunnel_button);
            buttons->addWidget(m_edit_tunnel_button);
            buttons->addWidget(m_remove_tunnel_button);
            buttons->addStretch();

            auto *x11 = new QGroupBox(tr("X11 Forwarding"), m_tunnel_widget);
            m_x11_forwarding_checkbox = new QCheckBox(tr("X DISPLAY:"), x11);
            m_x11_server_edit = new QLineEdit(QStringLiteral("localhost:0"), x11);
            m_x11_server_edit->setEnabled(false);

            auto *x11Layout = new QHBoxLayout(x11);
            x11Layout->setContentsMargins(0, 0, 0, 0);
            x11Layout->setSpacing(0);
            x11Layout->addWidget(m_x11_forwarding_checkbox);
            x11Layout->addWidget(m_x11_server_edit, 1);

            auto *agent = new QGroupBox(tr("Agent Forwarding"), m_tunnel_widget);
            m_forward_agent_checkbox = new QCheckBox(tr("Enable ForwardAgent"), agent);

            auto *agentLayout = new QHBoxLayout(agent);
            agentLayout->setContentsMargins(0, 0, 0, 0);
            agentLayout->setSpacing(0);
            agentLayout->addWidget(m_forward_agent_checkbox);

            auto *forwarding = new QHBoxLayout;
            forwarding->addWidget(x11, 1);
            forwarding->addWidget(agent);

            auto *layout = new QVBoxLayout(m_tunnel_widget);
            layout->addWidget(new QLabel(tr("TCP/IP Forwarding:"), m_tunnel_widget));
            layout->addWidget(m_tunnel_table, 1);
            layout->addLayout(buttons);
            layout->addLayout(forwarding);
        }

        void initJumpHosts() {
            m_jump_hosts_widget = new QWidget(m_stacked_widget);
            m_jump_hosts_table = createTable(m_jump_hosts_widget, {tr("Name"), tr("Host")});
            m_add_jump_host_button = new QPushButton(tr("Add"), m_jump_hosts_widget);
            m_remove_jump_host_button = new QPushButton(tr("Delete"), m_jump_hosts_widget);
            m_move_jump_host_up_button = new QPushButton(tr("Move Up"), m_jump_hosts_widget);
            m_move_jump_host_down_button = new QPushButton(tr("Move Down"), m_jump_hosts_widget);
            auto *buttons = new QHBoxLayout;
            buttons->setSpacing(6);
            buttons->addWidget(m_add_jump_host_button);
            buttons->addWidget(m_remove_jump_host_button);
            buttons->addWidget(m_move_jump_host_up_button);
            buttons->addWidget(m_move_jump_host_down_button);
            buttons->addStretch();
            auto *layout = new QVBoxLayout(m_jump_hosts_widget);
            layout->addWidget(new QLabel(tr("Jump Hosts:"), m_jump_hosts_widget));
            layout->addWidget(m_jump_hosts_table, 1);
            layout->addLayout(buttons);
            updateJumpHostButtons();
        }

        void initTerminal() {
            m_terminal_widget = new QWidget(m_stacked_widget);
            m_terminal_tabs = new QTabWidget(m_terminal_widget);
            m_terminal_tabs->tabBar()->setDrawBase(false);
            m_terminal_tabs->tabBar()->setExpanding(false);
            m_terminal_tabs->setUsesScrollButtons(true);
            auto *general = new QWidget(m_terminal_tabs);
            auto *scroll = new QScrollArea(m_terminal_tabs);
            scroll->setWidgetResizable(true);
            scroll->setFrameShape(QFrame::NoFrame);
            scroll->setWidget(general);
            MacScrollStyle::use(scroll);

            m_encoding_combobox = createComboBox(general);
            QStringList encodings;
#if QT_VERSION < QT_VERSION_CHECK(6, 0, 0)
            for (const auto &encoding : QTextCodec::availableCodecs()) {
                encodings.push_back(QString::fromLatin1(encoding));
            }
#else
            encodings = {"UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE", "UTF-32", "ISO-8859-1", "US-ASCII"};
#endif
            encodings.removeDuplicates();
            encodings.sort(Qt::CaseInsensitive);
            m_encoding_combobox->addItems(encodings);
            m_encoding_combobox->setCurrentText(QStringLiteral("UTF-8"));
            m_highlight_set_combobox = createComboBox(general);
            m_highlight_set_combobox->addItem(tr("None"), QStringLiteral("-1"));
            m_highlight_set_combobox->addItem(tr("Default"), QStringLiteral("0"));
            m_highlight_set_combobox->setCurrentIndex(1);
            m_alt_modifier_combobox = createComboBox(general);
            m_alt_modifier_combobox->addItem(tr("8-bit"), QStringLiteral("EightBit"));
            m_alt_modifier_combobox->addItem(tr("Characters preceded by ESC"),
                                             QStringLiteral("CharactersPrecededByESC"));
            m_backspace_combobox = createComboBox(general);
            m_backspace_combobox->addItem(tr("Delete"), QStringLiteral("Delete"));
            m_backspace_combobox->addItem(tr("Backspace"), QStringLiteral("Backspace"));
            m_backspace_combobox->addItem(tr("VT220"), QStringLiteral("VT220"));
            m_timeout_spinbox = new QSpinBox(general);
            m_timeout_spinbox->setRange(10, std::numeric_limits<int>::max());
            m_timeout_spinbox->setValue(60);
            m_timeout_spinbox->setSuffix(tr(" s"));
            m_heartbeat_interval_spinbox = new QSpinBox(general);
            m_heartbeat_interval_spinbox->setRange(3, std::numeric_limits<int>::max());
            m_heartbeat_interval_spinbox->setValue(30);
            m_heartbeat_interval_spinbox->setSuffix(tr(" s"));
            m_startup_command_edit = new QLineEdit(general);
            m_environment_edit = new QTextEdit(general);
            MacScrollStyle::use(m_environment_edit);
            setTextEditRows(m_environment_edit, 8);
            m_environment_edit->setPlaceholderText(QStringLiteral("NAME=value"));

            auto *form = new QGridLayout(general);
            form->setSpacing(6);
            form->setColumnStretch(1, 1);
            form->addWidget(createLabel(tr("Encoding:"), m_encoding_combobox), 0, 0);
            form->addWidget(m_encoding_combobox, 0, 1);
            form->addWidget(createLabel(tr("Highlight:"), m_highlight_set_combobox), 1, 0);
            form->addWidget(m_highlight_set_combobox, 1, 1);
            form->addWidget(createLabel(tr("Alt Modifier:"), m_alt_modifier_combobox), 2, 0);
            form->addWidget(m_alt_modifier_combobox, 2, 1);
            form->addWidget(createLabel(tr("Backspace:"), m_backspace_combobox), 3, 0);
            form->addWidget(m_backspace_combobox, 3, 1);
            form->addWidget(createLabel(tr("Timeout:"), m_timeout_spinbox), 4, 0);
            form->addWidget(m_timeout_spinbox, 4, 1);
            form->addWidget(createLabel(tr("Heartbeat Interval:"), m_heartbeat_interval_spinbox), 5, 0);
            form->addWidget(m_heartbeat_interval_spinbox, 5, 1);
            form->addWidget(createLabel(tr("Startup Command:"), m_startup_command_edit), 6, 0);
            form->addWidget(m_startup_command_edit, 6, 1);
            form->addWidget(createLabel(tr("Environment:"), m_environment_edit), 7, 0, Qt::AlignTop);
            form->addWidget(m_environment_edit, 7, 1);
            form->setRowStretch(8, 1);

            auto *scripts = new QWidget(m_terminal_tabs);
            m_login_scripts_table = createTable(scripts, {tr("Expect"), tr("Send")});
            m_add_login_script_button = new QPushButton(tr("Add"), scripts);
            m_edit_login_script_button = new QPushButton(tr("Edit"), scripts);
            m_remove_login_script_button = new QPushButton(tr("Delete"), scripts);
            m_edit_login_script_button->setEnabled(false);
            m_remove_login_script_button->setEnabled(false);
            auto *buttons = new QHBoxLayout;
            buttons->setSpacing(6);
            buttons->addWidget(m_add_login_script_button);
            buttons->addWidget(m_edit_login_script_button);
            buttons->addWidget(m_remove_login_script_button);
            buttons->addStretch();
            auto *scriptsLayout = new QVBoxLayout(scripts);
            scriptsLayout->addWidget(m_login_scripts_table, 1);
            scriptsLayout->addLayout(buttons);
            m_terminal_tabs->addTab(scroll, tr("General"));
            m_terminal_tabs->addTab(scripts, tr("Login Scripts"));
            auto *layout = new QVBoxLayout(m_terminal_widget);
            layout->setContentsMargins(0, 0, 0, 0);
            layout->addWidget(m_terminal_tabs);
        }

        void initTransfer() {
            m_transfer_widget = new QWidget(m_stacked_widget);
            m_sftp_default_directory_edit = new QLineEdit(m_transfer_widget);
            auto *layout = new QGridLayout(m_transfer_widget);
            layout->setSpacing(6);
            layout->setColumnStretch(1, 1);
            layout->addWidget(createLabel(tr("Default Directory:"), m_sftp_default_directory_edit), 0, 0);
            layout->addWidget(m_sftp_default_directory_edit, 0, 1);
            layout->setRowStretch(1, 1);
        }

        void editTunnel(int row) {
            const bool editing = row >= 0 && row < static_cast<int>(m_tunnelings.size());
            TunnelingDialog dialog(editing ? m_tunnelings[row] : store::Tunneling{}, this);
            if (dialog.exec() != QDialog::Accepted) {
                return;
            }
            if (editing) {
                m_tunnelings[row] = dialog.tunneling();
            } else {
                row = static_cast<int>(m_tunnelings.size());
                m_tunnelings.push_back(dialog.tunneling());
                m_tunnel_table->insertRow(row);
            }
            const auto &tunneling = m_tunnelings[row];
            QString type = tr("Local");
            if (tunneling.type == store::TunnelingType::Remote) {
                type = tr("Remote");
            } else if (tunneling.type == store::TunnelingType::Dynamic) {
                type = tr("Dynamic");
            }
            m_tunnel_table->setItem(row, 0, new QTableWidgetItem(tunneling.name));
            m_tunnel_table->setItem(row, 1, new QTableWidgetItem(type));
            m_tunnel_table->setItem(row, 2, new QTableWidgetItem(endpoint(tunneling.sourceHost, tunneling.sourcePort)));
            m_tunnel_table->setItem(
                row, 3,
                new QTableWidgetItem(tunneling.type == store::TunnelingType::Dynamic
                                         ? QString()
                                         : endpoint(tunneling.destinationHost, tunneling.destinationPort)));
            m_tunnel_table->setCurrentCell(row, 0);
            m_tunnel_table->selectRow(row);
        }

        void addJumpHosts() {
            QSet<QString> excluded;
            for (const auto &host : m_jump_hosts) {
                excluded.insert(host.id);
            }
            JumpHostDialog dialog(excluded, this);
            if (dialog.exec() != QDialog::Accepted) {
                return;
            }
            for (const auto &id : dialog.hostIds()) {
                const auto host = HostStore::instance()->host(id);
                if (!host) {
                    continue;
                }
                const int row = static_cast<int>(m_jump_hosts.size());
                m_jump_hosts.push_back(*host);
                m_jump_hosts_table->insertRow(row);
                m_jump_hosts_table->setItem(row, 0, new QTableWidgetItem(host->name));
                m_jump_hosts_table->setItem(row, 1, new QTableWidgetItem(endpoint(host->host, host->port)));
                m_jump_hosts_table->setCurrentCell(row, 0);
                m_jump_hosts_table->selectRow(row);
            }
            updateJumpHostButtons();
        }

        void moveJumpHosts(int direction) {
            auto rows = selectedRows(m_jump_hosts_table);
            if (rows.isEmpty() || (direction < 0 && rows.front() == 0) ||
                (direction > 0 && rows.back() == static_cast<int>(m_jump_hosts.size()) - 1)) {
                return;
            }
            if (direction > 0) {
                std::reverse(rows.begin(), rows.end());
            }
            m_jump_hosts_table->clearSelection();
            for (const int row : rows) {
                const int target = row + direction;
                std::swap(m_jump_hosts[row], m_jump_hosts[target]);
                for (int column = 0; column < m_jump_hosts_table->columnCount(); ++column) {
                    auto *item = m_jump_hosts_table->takeItem(row, column);
                    auto *other = m_jump_hosts_table->takeItem(target, column);
                    m_jump_hosts_table->setItem(row, column, other);
                    m_jump_hosts_table->setItem(target, column, item);
                }
            }
            for (const int row : rows) {
                m_jump_hosts_table->selectionModel()->select(m_jump_hosts_table->model()->index(row + direction, 0),
                                                             QItemSelectionModel::Select | QItemSelectionModel::Rows);
            }
            m_jump_hosts_table->selectionModel()->setCurrentIndex(
                m_jump_hosts_table->model()->index(rows.front() + direction, 0), QItemSelectionModel::NoUpdate);
            updateJumpHostButtons();
        }

        void updateJumpHostButtons() {
            const auto rows = selectedRows(m_jump_hosts_table);
            m_remove_jump_host_button->setEnabled(!rows.isEmpty());
            m_move_jump_host_up_button->setEnabled(!rows.isEmpty() && rows.front() > 0);
            m_move_jump_host_down_button->setEnabled(!rows.isEmpty() &&
                                                     rows.back() + 1 < m_jump_hosts_table->rowCount());
        }

        void editLoginScript(int row) {
            const bool editing = row >= 0 && row < static_cast<int>(m_login_scripts.size());
            LoginScriptDialog dialog(editing ? m_login_scripts[row] : store::LoginScript{}, this);
            if (dialog.exec() != QDialog::Accepted) {
                return;
            }
            if (editing) {
                m_login_scripts[row] = dialog.script();
            } else {
                row = static_cast<int>(m_login_scripts.size());
                m_login_scripts.push_back(dialog.script());
                m_login_scripts_table->insertRow(row);
            }
            m_login_scripts_table->setItem(row, 0, new QTableWidgetItem(m_login_scripts[row].expect));
            m_login_scripts_table->setItem(row, 1, new QTableWidgetItem(m_login_scripts[row].send));
            m_login_scripts_table->setCurrentCell(row, 0);
            m_login_scripts_table->selectRow(row);
        }

        static QString endpoint(const QString &host, int port) {
            return host.contains(':') ? QStringLiteral("[%1]:%2").arg(host).arg(port)
                                      : QStringLiteral("%1:%2").arg(host).arg(port);
        }

        void initEvents() {
            connect(m_list_widget, &QListWidget::currentRowChanged, m_stacked_widget, &QStackedWidget::setCurrentIndex);
            if (m_mode == Mode::Host) {
                connect(m_authentication_type_combobox, QOverload<int>::of(&QComboBox::currentIndexChanged), this,
                        [this] { updateAuthentication(); });
            }
            connect(m_proxy_type_combobox, QOverload<int>::of(&QComboBox::currentIndexChanged), this,
                    [this] { updateProxy(); });
            connect(m_proxy_authentication_type_combobox, QOverload<int>::of(&QComboBox::currentIndexChanged), this,
                    [this] { updateProxy(); });
            if (m_mode == Mode::Host) {
                connect(m_x11_forwarding_checkbox, &QCheckBox::toggled, m_x11_server_edit, &QWidget::setEnabled);
                connect(m_add_tunnel_button, &QPushButton::clicked, this, [this] { editTunnel(-1); });
                connect(m_edit_tunnel_button, &QPushButton::clicked, this, [this] {
                    const auto rows = selectedRows(m_tunnel_table);
                    if (rows.size() == 1) {
                        editTunnel(rows.front());
                    }
                });
                connect(m_tunnel_table, &QTableWidget::cellDoubleClicked, this,
                        [this](int row, int) { editTunnel(row); });
                connect(m_tunnel_table, &QTableWidget::itemSelectionChanged, this, [this] {
                    const auto rows = selectedRows(m_tunnel_table);
                    m_edit_tunnel_button->setEnabled(rows.size() == 1);
                    m_remove_tunnel_button->setEnabled(!rows.isEmpty());
                });
                connect(m_remove_tunnel_button, &QPushButton::clicked, this, [this] {
                    for (const auto rows = selectedRows(m_tunnel_table); const int row : std::views::reverse(rows)) {
                        m_tunnelings.erase(m_tunnelings.begin() + row);
                        m_tunnel_table->removeRow(row);
                    }
                });
            }
            connect(m_add_jump_host_button, &QPushButton::clicked, this, &NewHostOptionsWidget::addJumpHosts);
            connect(m_jump_hosts_table, &QTableWidget::itemSelectionChanged, this,
                    &NewHostOptionsWidget::updateJumpHostButtons);
            connect(m_remove_jump_host_button, &QPushButton::clicked, this, [this] {
                for (const auto rows = selectedRows(m_jump_hosts_table); const int row : std::views::reverse(rows)) {
                    m_jump_hosts.erase(m_jump_hosts.begin() + row);
                    m_jump_hosts_table->removeRow(row);
                }
                updateJumpHostButtons();
            });
            connect(m_move_jump_host_up_button, &QPushButton::clicked, this, [this] { moveJumpHosts(-1); });
            connect(m_move_jump_host_down_button, &QPushButton::clicked, this, [this] { moveJumpHosts(1); });
            connect(m_add_login_script_button, &QPushButton::clicked, this, [this] { editLoginScript(-1); });
            connect(m_edit_login_script_button, &QPushButton::clicked, this, [this] {
                if (const auto rows = selectedRows(m_login_scripts_table); rows.size() == 1) {
                    editLoginScript(rows.front());
                }
            });
            connect(m_login_scripts_table, &QTableWidget::cellDoubleClicked, this,
                    [this](int row, int) { editLoginScript(row); });
            connect(m_login_scripts_table, &QTableWidget::itemSelectionChanged, this, [this] {
                const auto rows = selectedRows(m_login_scripts_table);
                m_edit_login_script_button->setEnabled(rows.size() == 1);
                m_remove_login_script_button->setEnabled(!rows.isEmpty());
            });
            connect(m_remove_login_script_button, &QPushButton::clicked, this, [this] {
                for (const auto rows = selectedRows(m_login_scripts_table); const int row : std::views::reverse(rows)) {
                    m_login_scripts.erase(m_login_scripts.begin() + row);
                    m_login_scripts_table->removeRow(row);
                }
            });
        }

        void updateAuthentication() {
            m_password_edit->setEnabled(m_authentication_type_combobox->currentData().toInt() ==
                                        static_cast<int>(store::AuthenticationType::Password));
        }

        void updateProxy() {
            const bool enabled = m_proxy_type_combobox->currentData().toInt() != static_cast<int>(store::ProxyType::No);
            const bool password = enabled && m_proxy_authentication_type_combobox->currentData().toInt() ==
                                                 static_cast<int>(store::AuthenticationType::Password);
            m_proxy_host_edit->setEnabled(enabled);
            m_proxy_port_spinbox->setEnabled(enabled);
            m_proxy_authentication_type_combobox->setEnabled(enabled);
            m_proxy_username_edit->setEnabled(password);
            m_proxy_password_edit->setEnabled(password);
        }

        static QLabel *createLabel(const QString &text, QWidget *buddy) {
            auto *label = new QLabel(text, buddy->parentWidget());
            label->setBuddy(buddy);
            return label;
        }

    private:
        const Mode m_mode;
        QListWidget *m_list_widget = nullptr;
        QStackedWidget *m_stacked_widget = nullptr;
        QWidget *m_properties_widget = nullptr;
        QWidget *m_proxy_widget = nullptr;
        QWidget *m_tunnel_widget = nullptr;
        QTableWidget *m_tunnel_table = nullptr;
        QPushButton *m_add_tunnel_button = nullptr;
        QPushButton *m_edit_tunnel_button = nullptr;
        QPushButton *m_remove_tunnel_button = nullptr;
        QCheckBox *m_x11_forwarding_checkbox = nullptr;
        QLineEdit *m_x11_server_edit = nullptr;
        QCheckBox *m_forward_agent_checkbox = nullptr;
        QWidget *m_jump_hosts_widget = nullptr;
        QTableWidget *m_jump_hosts_table = nullptr;
        QPushButton *m_add_jump_host_button = nullptr;
        QPushButton *m_remove_jump_host_button = nullptr;
        QPushButton *m_move_jump_host_up_button = nullptr;
        QPushButton *m_move_jump_host_down_button = nullptr;
        QWidget *m_terminal_widget = nullptr;
        QTabWidget *m_terminal_tabs = nullptr;
        QComboBox *m_encoding_combobox = nullptr;
        QComboBox *m_highlight_set_combobox = nullptr;
        QComboBox *m_alt_modifier_combobox = nullptr;
        QComboBox *m_backspace_combobox = nullptr;
        QSpinBox *m_timeout_spinbox = nullptr;
        QSpinBox *m_heartbeat_interval_spinbox = nullptr;
        QLineEdit *m_startup_command_edit = nullptr;
        QTextEdit *m_environment_edit = nullptr;
        QTableWidget *m_login_scripts_table = nullptr;
        QPushButton *m_add_login_script_button = nullptr;
        QPushButton *m_edit_login_script_button = nullptr;
        QPushButton *m_remove_login_script_button = nullptr;
        QWidget *m_transfer_widget = nullptr;
        QLineEdit *m_sftp_default_directory_edit = nullptr;
        std::vector<store::Tunneling> m_tunnelings;
        std::vector<store::Host> m_jump_hosts;
        std::vector<store::LoginScript> m_login_scripts;
        QLineEdit *m_name_edit = nullptr;
        QLineEdit *m_host_edit = nullptr;
        QSpinBox *m_port_spinbox = nullptr;
        QLineEdit *m_username_edit = nullptr;
        QComboBox *m_authentication_type_combobox = nullptr;
        QLineEdit *m_password_edit = nullptr;
        QTextEdit *m_remark_edit = nullptr;
        QComboBox *m_proxy_type_combobox = nullptr;
        QLineEdit *m_proxy_host_edit = nullptr;
        QSpinBox *m_proxy_port_spinbox = nullptr;
        QComboBox *m_proxy_authentication_type_combobox = nullptr;
        QLineEdit *m_proxy_username_edit = nullptr;
        QLineEdit *m_proxy_password_edit = nullptr;
    };

    class NewHostWidget : public NewHostOptionsWidget {
    public:
        explicit NewHostWidget() : NewHostOptionsWidget(Mode::Host) {}

        bool canTest() const override {
            return true;
        }
    };

    class NewFolderWidget : public NewHostOptionsWidget {
    public:
        explicit NewFolderWidget() : NewHostOptionsWidget(Mode::Folder) {}
    };
} // namespace

NewHostDialog::NewHostDialog(QWidget *parent) : HeadlessDialog(true, parent) {
    initViews();
    initEvents();
}

void NewHostDialog::initViews() {
    m_protocol_bar = new QWidget(this);
    m_protocol_bar->setAttribute(Qt::WA_StyledBackground);
    m_protocol_bar->setObjectName("ToolBar");
    m_protocol_bar->setStyleSheet(
        QString("QWidget#ToolBar { border-top: 1px solid %1; }").arg(palette().mid().color().name()));

    m_folder_button = new QToolButton(m_protocol_bar);
    m_folder_button->setText(tr("Folder"));
    m_folder_button->setIcon(icons::folder());
    m_folder_button->setIconSize(QSize(22, 22));
    m_folder_button->setToolButtonStyle(Qt::ToolButtonTextUnderIcon);

    m_ssh2_button = new QToolButton(m_protocol_bar);
    m_ssh2_button->setText(tr("SSH"));
    m_ssh2_button->setIcon(icons::terminal());
    m_ssh2_button->setIconSize(QSize(22, 22));
    m_ssh2_button->setToolButtonStyle(Qt::ToolButtonTextUnderIcon);

    auto *type_buttons = new QButtonGroup(this);
    type_buttons->addButton(m_ssh2_button);
    type_buttons->addButton(m_folder_button);

    m_folder_button->setCheckable(true);
    m_ssh2_button->setCheckable(true);
    m_ssh2_button->setChecked(true);

    auto *buttons_layout = new QHBoxLayout(m_protocol_bar);
    buttons_layout->setSpacing(8);
    buttons_layout->setContentsMargins(0, 4, 0, 4);
    buttons_layout->addStretch();
    buttons_layout->addWidget(m_ssh2_button);
    buttons_layout->addWidget(m_folder_button);
    buttons_layout->addStretch();

    m_stacked_widget = new QStackedWidget(this);
    m_stacked_widget->setObjectName("RootStackedWidget");
    m_stacked_widget->addWidget(new NewHostWidget());
    m_stacked_widget->addWidget(new NewFolderWidget());
    m_stacked_widget->setStyleSheet(QString(R"(
    QStackedWidget#RootStackedWidget {
        border-top: 1px solid %1;
        border-bottom: 1px solid %1;
    }
)")
                                        .arg(palette().mid().color().name()));
    m_stacked_widget->setCurrentIndex(0);

    m_footer_widget = new QWidget(this);
    m_footer_widget->setAttribute(Qt::WA_StyledBackground);
    m_message_label = new QLabel(m_footer_widget);
    m_message_label->setWordWrap(true);
    m_message_label->hide();
    m_cancel_button = new QPushButton(tr("Cancel"), m_footer_widget);
    m_test_connection_button = new QPushButton(tr("Test Connection"), m_footer_widget);
    m_ok_button = new QPushButton(tr("OK"), m_footer_widget);
    m_ok_button->setDefault(true);
    m_cancel_button->setAutoDefault(false);
    m_test_connection_button->setAutoDefault(false);

    auto *footer_layout = new QHBoxLayout(m_footer_widget);
    footer_layout->setContentsMargins(12, 8, 12, 8);
    footer_layout->setSpacing(8);
    footer_layout->addWidget(m_message_label, 1);
    footer_layout->addStretch(1);
    footer_layout->addWidget(m_cancel_button);
    footer_layout->addWidget(m_test_connection_button);
    footer_layout->addWidget(m_ok_button);

    auto *root_layout = qobject_cast<QVBoxLayout *>(layout());
    root_layout->addWidget(m_protocol_bar);
    root_layout->addWidget(m_stacked_widget, 1);
    root_layout->addWidget(m_footer_widget);
    updateFooter();

    resize(660, 560);
}

void NewHostDialog::initEvents() {
    connect(m_folder_button, &QToolButton::clicked, this, [this] { m_stacked_widget->setCurrentIndex(1); });
    connect(m_ssh2_button, &QToolButton::clicked, this, [this] { m_stacked_widget->setCurrentIndex(0); });
    connect(m_stacked_widget, &QStackedWidget::currentChanged, this, [this](int index) {
        m_ssh2_button->setChecked(index == 0);
        m_folder_button->setChecked(index == 1);
        updateFooter();
        m_stacked_widget->currentWidget()->setFocus();
    });
    connect(m_cancel_button, &QPushButton::clicked, this, &QDialog::reject);
    connect(m_ok_button, &QPushButton::clicked, this, &NewHostDialog::accept);
    connect(m_test_connection_button, &QPushButton::clicked, this, [this] {
        auto *widget = dynamic_cast<NewWidget *>(m_stacked_widget->currentWidget());
        if (!widget || !widget->canTest()) {
            return;
        }
        const auto host = widget->host();
        if (!host) {
            showValidationError();
            return;
        }
        m_message_label->hide();
        Q_EMIT testConnectionRequested(*host);
    });
}

void NewHostDialog::updateFooter() {
    const auto *widget = dynamic_cast<const NewWidget *>(m_stacked_widget->currentWidget());
    m_test_connection_button->setVisible(widget && widget->canTest());
    m_message_label->hide();
    setWindowTitle(m_stacked_widget->currentIndex() == 1 ? tr("New Folder") : tr("New Host"));
}

void NewHostDialog::showValidationError() {
    m_message_label->setText(tr("Complete the required fields."));
    m_message_label->show();
}

void NewHostDialog::accept() {
    auto *widget = dynamic_cast<NewWidget *>(m_stacked_widget->currentWidget());
    if (!widget) {
        return;
    }
    auto host = widget->host();
    if (!host) {
        showValidationError();
        return;
    }
    m_host = std::move(host);
    QDialog::accept();
}

std::optional<store::Host> NewHostDialog::host() {
    if (m_host) {
        return m_host;
    }
    if (auto *widget = dynamic_cast<NewWidget *>(m_stacked_widget->currentWidget())) {
        return widget->host();
    }
    return std::nullopt;
}
