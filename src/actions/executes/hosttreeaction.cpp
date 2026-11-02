#include "hosttreeaction.h"

#include "../../stores/hoststore.h"
#include "../datakeys.h"

void HostTreeAction::update(ActionEvent &event) const {
    AnAction::update(event);
}

std::optional<QString> HostTreeAction::currentFolderId(ActionEvent &event) {
    Q_UNUSED(this);
    const auto tree = event.getData(DataKeys::HOST_TREE_VIEW);
    const auto model = event.getData(DataKeys::HOST_TREE_MODEL);
    if (!tree.has_value() || !model.has_value()) {
        return std::nullopt;
    }

    const auto id = model.value()->data(tree.value()->currentIndex(), Qt::UserRole);
    auto folderId = QString();

    if (id.isValid() && id.type() == QVariant::String) {
        if (const auto host = HostStore::instance()->host(id.toString()); host.has_value()) {
            if (host.value().protocol == "folder") {
                folderId = host->id;
            } else {
                folderId = host->parentId;
            }
        }
    } else {
        return std::nullopt;
    }

    return folderId;
}