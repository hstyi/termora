#pragma once

#include "actionevent.h"

#include <QAction>
#include <QKeySequence>
#include <QList>

class AnAction {
public:
    virtual ~AnAction() = default;

    Presentation &templatePresentation() noexcept {
        return m_template_presentation;
    }

    const Presentation &templatePresentation() const noexcept {
        return m_template_presentation;
    }

    virtual QList<QKeySequence> shortcuts() const {
        return {};
    }

    void addTextOverride(const ActionPlace place, QString text) {
        m_text_overrides[place] = std::move(text);
    }

    void applyTextOverride(const ActionPlace place, Presentation &presentation) const {
        if (const auto it = m_text_overrides.find(place); it != m_text_overrides.end()) {
            presentation.text = it->second;
        }
    }

    virtual void update(ActionEvent &event) const {}

    virtual void execute(ActionEvent &event) = 0;

private:
    Presentation m_template_presentation;
    std::map<ActionPlace, QString> m_text_overrides;
};
