#pragma once

#include "stores/entities.h"

#include <QAbstractItemModel>
#include <map>
#include <memory>
#include <vector>

class HostTreeModel : public QAbstractItemModel {
    Q_OBJECT

public:
    explicit HostTreeModel(QObject *parent = nullptr);

    QModelIndex index(int row, int column, const QModelIndex &parent = {}) const override;
    QModelIndex parent(const QModelIndex &index) const override;
    int rowCount(const QModelIndex &parent = {}) const override;
    int columnCount(const QModelIndex &parent = {}) const override;
    QVariant data(const QModelIndex &index, int role = Qt::DisplayRole) const override;
    QVariant headerData(int section, Qt::Orientation orientation, int role = Qt::DisplayRole) const override;
    Qt::ItemFlags flags(const QModelIndex &index) const override;

private:
    struct Node {
        store::Host host;
        Node *parent = nullptr;
        int row = 0;
        std::vector<Node *> children;
    };

    Node *node(const QModelIndex &index) const;
    Node *findNode(const QString &id) const;
    Node *parentNode(const store::Host &host, const Node *item) const;
    QModelIndex nodeIndex(const Node *item) const;
    std::vector<Node *> &children(Node *parent);
    static void updateRows(std::vector<Node *> &siblings);
    bool moveNode(Node *item, Node *parent, const store::Host &host);
    void adoptChildren(Node *parent);
    void load();
    void upsertHost(const QString &id);
    void removeHost(const QString &id);

private:
    std::map<QString, std::unique_ptr<Node>> m_nodes;
    std::vector<Node *> m_roots;
};
