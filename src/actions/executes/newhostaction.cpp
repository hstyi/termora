#include "newhostaction.h"

#include "../../newhostdialog.h"
#include "../../stores/hoststore.h"

void NewHostAction::execute(ActionEvent &event) {
    const auto folderId = currentFolderId(event);
    NewHostDialog dialog(event.mainWindow());
    dialog.exec();
}