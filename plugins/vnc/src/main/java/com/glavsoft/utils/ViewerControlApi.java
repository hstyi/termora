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
package com.glavsoft.utils;

import com.glavsoft.rfb.client.ClientMessageType;
import com.glavsoft.rfb.client.ClientToServerMessage;
import com.glavsoft.rfb.protocol.Protocol;
import com.glavsoft.rfb.protocol.ProtocolSettings;
import com.glavsoft.transport.BaudrateMeter;

/**
 * @author dime at tightvnc.com
 */
public class ViewerControlApi {
    private final Protocol protocol;
    private BaudrateMeter baudrateMeter;

    public ViewerControlApi(Protocol protocol, BaudrateMeter baudrateMeter) {
        this.protocol = protocol;
        this.baudrateMeter = baudrateMeter;
        protocol.setBaudrateMeter(baudrateMeter);
    }

    public void sendMessage(ClientToServerMessage message) {
        protocol.sendMessage(message);
    }

    public void sendKeepAlive() {
        protocol.sendSupportedEncodingsMessage(protocol.getSettings());
    }

    public void setCompressionLevelTo(int compressionLevel) {
        final ProtocolSettings settings = protocol.getSettings();
        settings.setCompressionLevel(compressionLevel);
        settings.fireListeners();
    }

    public void setJpegQualityTo(int jpegQuality) {
        final ProtocolSettings settings = protocol.getSettings();
        settings.setJpegQuality(jpegQuality);
        settings.fireListeners();
    }

    public void setViewOnly(boolean isViewOnly) {
        final ProtocolSettings settings = protocol.getSettings();
        settings.setViewOnly(isViewOnly);
        settings.fireListeners();
    }


    public int getBaudrate() {
        return baudrateMeter.kBPS();
    }

    /**
     * Check whether remote server is supported for given client-to-server message
     *
     * @param type client-to-server message type to check for
     * @return true when supported
     */
    public boolean isSupported(ClientMessageType type) {
        return protocol.isSupported(type);
    }

}
