#include "actionevent.h"

#include "datakeys.h"

QMainWindow *ActionEvent::mainWindow() const {
    if (const auto main_window = getData(DataKeys::MAIN_WINDOW); main_window.has_value()) {
        return main_window.value();
    }
    return nullptr;
}
