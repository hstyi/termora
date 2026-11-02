#pragma once
#include "hosttreeaction.h"

class NewHostAction : public HostTreeAction {
public:
    explicit NewHostAction() : HostTreeAction() {
        auto &presentation = templatePresentation();
        presentation.text = "New Host";
    }

    void execute(ActionEvent &event) override;
};