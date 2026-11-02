#pragma once

#include "datakey.h"

#include <QAbstractItemModel>
#include <QMainWindow>
#include <QString>
#include <QTreeView>

namespace DataKeys {
    inline const auto MAIN_WINDOW = DataKey<QMainWindow *>("termora.main-window");
    inline const auto HOST_TREE_VIEW = DataKey<QTreeView *>("termora.host-tree");
    inline const auto HOST_TREE_MODEL = DataKey<QAbstractItemModel *>("termora.host-tree-model");
} // namespace DataKeys
