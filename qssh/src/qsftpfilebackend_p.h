#pragma once

#include "qsshsftpsession_p.h"

#include <qvfs/private/qvirtualbackend_p.h>

/** 为规范化远端 path 创建后台入口；dispatcher 不拥有原生会话，析构后安全拒绝请求。 */
std::shared_ptr<qvfs::Backend> qsshSftpBackend(std::shared_ptr<QSshSftpSessionPrivate::Dispatcher> dispatcher,
                                               QString path);
