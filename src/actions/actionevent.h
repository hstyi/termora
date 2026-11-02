#pragma once

#include "datacontext.h"
#include "presentation.h"

#include <QMainWindow>
#include <utility>

enum class ActionPlace {
    Toolbar,
    MainMenu,
    ContextMenu,
    Shortcut
};

class ActionEvent {
public:
    ActionEvent(DataContext context, Presentation &presentation, const ActionPlace place)
        : m_context(std::move(context)),
          m_presentation(presentation),
          m_place(place) {}

    template <typename T> std::optional<T> getData(const DataKey<T> &key) const {
        return m_context.getData(key);
    }

    const DataContext &dataContext() const noexcept {
        return m_context;
    }

    Presentation &presentation() noexcept {
        return m_presentation;
    }

    QMainWindow *mainWindow() const;

    const Presentation &presentation() const noexcept {
        return m_presentation;
    }

    ActionPlace place() const noexcept {
        return m_place;
    }

private:
    DataContext m_context;
    Presentation &m_presentation;
    ActionPlace m_place;
};
