#include "hosttreemodel.h"

#include "QStyle"
#include "icons.h"
#include "stores/hoststore.h"

#include <QApplication>
#include <QHash>
#include <algorithm>
#include <utility>

namespace {
    bool hostLessThan(const store::Host &left, const store::Host &right) {
        return left.sort != right.sort ? left.sort < right.sort : left.id < right.id;
    }
} // namespace

HostTreeModel::HostTreeModel(QObject *parent) : QAbstractItemModel(parent) {
    auto *store = HostStore::instance();
    connect(store, &HostStore::hostInserted, this, &HostTreeModel::upsertHost);
    connect(store, &HostStore::hostRemoved, this, &HostTreeModel::removeHost);
    connect(store, &HostStore::hostChanged, this, &HostTreeModel::upsertHost);
    load();
}

QModelIndex HostTreeModel::index(int row, int column, const QModelIndex &parent) const {
    if (row < 0 || column != 0) {
        return {};
    }

    const auto *parentNode = node(parent);
    if (parent.isValid() && !parentNode) {
        return {};
    }
    const auto &siblings = parentNode ? parentNode->children : m_roots;
    if (row >= static_cast<int>(siblings.size())) {
        return {};
    }
    return nodeIndex(siblings[row]);
}

QModelIndex HostTreeModel::parent(const QModelIndex &index) const {
    const auto *item = node(index);
    return item ? nodeIndex(item->parent) : QModelIndex{};
}

int HostTreeModel::rowCount(const QModelIndex &parent) const {
    if (!parent.isValid()) {
        return static_cast<int>(m_roots.size());
    }
    const auto *item = node(parent);
    return item ? static_cast<int>(item->children.size()) : 0;
}

int HostTreeModel::columnCount(const QModelIndex &parent) const {
    return !parent.isValid() || node(parent) ? 1 : 0;
}

QVariant HostTreeModel::data(const QModelIndex &index, int role) const {
    const auto *item = node(index);
    if (!item) {
        return {};
    }
    switch (role) {
        case Qt::DisplayRole:
            return item->host.name;
        case Qt::DecorationRole:
            if (item->host.protocol == "folder") {
                return QApplication::style()->standardIcon(QStyle::SP_DirIcon);
            }
            return icons::terminal();
        case Qt::ToolTipRole:
            return item->host.remark;
        case Qt::UserRole:
            return item->host.id;
        default:
            break;
    }
    return {};
}

QVariant HostTreeModel::headerData(int section, Qt::Orientation orientation, int role) const {
    if (section == 0 && orientation == Qt::Horizontal && role == Qt::DisplayRole) {
        return tr("Name");
    }
    return {};
}

Qt::ItemFlags HostTreeModel::flags(const QModelIndex &index) const {
    return node(index) ? Qt::ItemIsEnabled | Qt::ItemIsSelectable : Qt::NoItemFlags;
}

HostTreeModel::Node *HostTreeModel::node(const QModelIndex &index) const {
    if (!index.isValid() || index.model() != this || index.column() != 0) {
        return nullptr;
    }
    return static_cast<Node *>(index.internalPointer());
}

HostTreeModel::Node *HostTreeModel::findNode(const QString &id) const {
    const auto it = m_nodes.find(id);
    return it != m_nodes.end() ? it->second.get() : nullptr;
}

HostTreeModel::Node *HostTreeModel::parentNode(const store::Host &host, const Node *item) const {
    auto *parent = host.parentId.isEmpty() ? nullptr : findNode(host.parentId);
    for (auto *ancestor = parent; ancestor; ancestor = ancestor->parent) {
        if (ancestor == item) {
            return nullptr;
        }
    }
    return parent;
}

QModelIndex HostTreeModel::nodeIndex(const Node *item) const {
    return item ? createIndex(item->row, 0, const_cast<Node *>(item)) : QModelIndex{};
}

std::vector<HostTreeModel::Node *> &HostTreeModel::children(Node *parent) {
    return parent ? parent->children : m_roots;
}

void HostTreeModel::updateRows(std::vector<Node *> &siblings) {
    for (int row = 0; row < static_cast<int>(siblings.size()); ++row) {
        siblings[row]->row = row;
    }
}

bool HostTreeModel::moveNode(Node *item, Node *parent, const store::Host &host) {
    auto &source = children(item->parent);
    auto &destination = children(parent);
    const int sourceRow = item->row;
    const bool sameParent = item->parent == parent;
    const auto it = std::lower_bound(
        destination.begin(), destination.end(), host,
        [](const Node *node, const store::Host &value) { return hostLessThan(node->host, value); });
    int row = static_cast<int>(it - destination.begin());
    if (sameParent && sourceRow < row) {
        --row;
    }
    if (sameParent && sourceRow == row) {
        return true;
    }

    destination.reserve(destination.size() + 1);
    // 同一父节点下向后移动时，Qt 的目标行以移除前的位置计算。
    const int destinationRow = sameParent && row > sourceRow ? row + 1 : row;
    if (!beginMoveRows(nodeIndex(item->parent), sourceRow, sourceRow, nodeIndex(parent), destinationRow)) {
        return false;
    }
    source.erase(source.begin() + sourceRow);
    destination.insert(destination.begin() + row, item);
    item->parent = parent;
    updateRows(source);
    if (!sameParent) {
        updateRows(destination);
    }
    endMoveRows();
    return true;
}

void HostTreeModel::adoptChildren(Node *parent) {
    // 父记录晚于子记录出现时，仅将等待该父节点的根节点移回其下。
    const auto roots = m_roots;
    for (auto *item : roots) {
        if (item != parent && item->host.parentId == parent->host.id && parentNode(item->host, item) == parent) {
            moveNode(item, parent, item->host);
        }
    }
}

void HostTreeModel::load() {
    auto hosts = HostStore::instance()->hosts();
    std::vector<Node *> ordered;
    ordered.reserve(hosts.size());
    for (auto &host : hosts) {
        auto item = std::make_unique<Node>();
        item->host = std::move(host);
        ordered.push_back(item.get());
        m_nodes.emplace(item->host.id, std::move(item));
    }
    for (auto *item : ordered) {
        item->parent = item->host.parentId.isEmpty() ? nullptr : findNode(item->host.parentId);
    }

    // 初始化时断开循环父子关系，保证所有节点均可从根访问。
    QHash<Node *, unsigned char> states;
    for (auto *item : ordered) {
        std::vector<Node *> path;
        auto *current = item;
        while (current && states.value(current) == 0) {
            states[current] = 1;
            path.push_back(current);
            current = current->parent;
        }
        if (current && states.value(current) == 1) {
            current->parent = nullptr;
        }
        for (auto *visited : path) {
            states[visited] = 2;
        }
    }

    for (auto *item : ordered) {
        auto &siblings = children(item->parent);
        item->row = static_cast<int>(siblings.size());
        siblings.push_back(item);
    }
}

void HostTreeModel::upsertHost(const QString &id) {
    auto host = HostStore::instance()->host(id);
    if (!host) {
        return;
    }

    if (auto *item = findNode(id)) {
        auto *parent = parentNode(*host, item);
        const bool parentChanged = parent != item->parent;
        QVector<int> roles;
        if (item->host.name != host->name) {
            roles.push_back(Qt::DisplayRole);
        }
        if (item->host.remark != host->remark) {
            roles.push_back(Qt::ToolTipRole);
        }
        if (!moveNode(item, parent, *host)) {
            return;
        }
        item->host = std::move(*host);
        if (!roles.isEmpty()) {
            const auto index = nodeIndex(item);
            Q_EMIT dataChanged(index, index, roles);
        }
        if (parentChanged) {
            adoptChildren(item);
        }
        return;
    }

    auto item = std::make_unique<Node>();
    item->host = std::move(*host);
    item->parent = parentNode(item->host, item.get());
    auto *inserted = item.get();
    auto &siblings = children(inserted->parent);
    const auto it = std::lower_bound(
        siblings.begin(), siblings.end(), inserted,
        [](const Node *left, const Node *right) { return hostLessThan(left->host, right->host); });
    const int row = static_cast<int>(it - siblings.begin());
    siblings.reserve(siblings.size() + 1);
    m_nodes.emplace(id, std::move(item));
    beginInsertRows(nodeIndex(inserted->parent), row, row);
    siblings.insert(siblings.begin() + row, inserted);
    updateRows(siblings);
    endInsertRows();
    adoptChildren(inserted);
}

void HostTreeModel::removeHost(const QString &id) {
    auto *item = findNode(id);
    if (!item) {
        return;
    }

    // 存储只删除该记录；保留仍在存储中的子节点及其子树。
    while (!item->children.empty()) {
        auto *child = item->children.front();
        if (!moveNode(child, nullptr, child->host)) {
            return;
        }
    }

    auto &siblings = children(item->parent);
    const int row = item->row;
    beginRemoveRows(nodeIndex(item->parent), row, row);
    siblings.erase(siblings.begin() + row);
    updateRows(siblings);
    m_nodes.erase(id);
    endRemoveRows();
}
