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
package com.glavsoft.rfb.protocol.tunnel;

import com.glavsoft.rfb.RfbCapabilityInfo;

/**
 * @author dime at tightvnc.com
 */
public enum TunnelType {
    NOTUNNEL(0, RfbCapabilityInfo.VENDOR_STANDARD, "NOTUNNEL", ""),

    SSL(2, RfbCapabilityInfo.VENDOR_TIGHT, "SSL_____", "SSL/TLS");

    public final int code;
    public final String vendor;
    public final String name;
    public final String hrName;

    TunnelType(int code, String vendor, String name, String humanReadableName) {
        this.code = code;
        this.vendor = vendor;
        this.name = name;
        hrName = humanReadableName;
    }

    public static TunnelType byCode(int code) {
        for (TunnelType type : values()) {
            if (type.code == code) return type;
        }
        return null;
    }

}
