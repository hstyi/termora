// Copyright (C) 2010 - 2014 GlavSoft LLC.
// All rights reserved.
//
// -----------------------------------------------------------------------
// This file is part of the TightVNC software.  Please visit our Web site:
//
//                       http://www.tightvnc.com/
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
// -----------------------------------------------------------------------
//
package com.glavsoft.viewer.workers;

/**
 * Factory that creates NetworkConnectionWorker and RfbConnectionWorker
 * @author dime at tightvnc.com
 */
public abstract class AbstractConnectionWorkerFactory {
    /**
     * Creates NetworkConnectionWorker
     * @return NetworkConnectionWorker created
     */
    public abstract NetworkConnectionWorker createNetworkConnectionWorker();

    /**
     * Creates RfbConnectionWorker
     * @return RfbConnectionWorker created
     */
    public abstract RfbConnectionWorker createRfbConnectionWorker();

    public abstract void setPredefinedPassword(String predefinedPassword);
}
