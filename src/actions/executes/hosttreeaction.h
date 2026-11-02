#pragma once
#include "../anaction.h"

#include <optional>

class HostTreeAction : public AnAction {

public:
    void update(ActionEvent &event) const override;

protected:
    /**
     * 返回当前选中的文件夹，如果选中的是一个 Host 那么会返回它的父ID
     */
    std::optional<QString> currentFolderId(ActionEvent &event);
};