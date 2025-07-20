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
package com.glavsoft.rfb.protocol;

import com.glavsoft.rfb.encoding.PixelFormat;
import com.glavsoft.rfb.protocol.handlers.Handshaker;
import com.glavsoft.rfb.protocol.tunnel.TunnelType;
import com.glavsoft.transport.Transport;

public class ProtocolContext {
    int fbWidth;
    int fbHeight;
    PixelFormat pixelFormat;
    Transport transport;
    String remoteDesktopName;
    boolean isTight;
    Handshaker.ProtocolVersion protocolVersion;
    ProtocolSettings settings;
    private TunnelType tunnelType;

    public PixelFormat getPixelFormat() {
        return pixelFormat;
    }

    public void setPixelFormat(PixelFormat pixelFormat) {
        this.pixelFormat = pixelFormat;
    }

    public String getRemoteDesktopName() {
        return remoteDesktopName;
    }

    public void setRemoteDesktopName(String name) {
        remoteDesktopName = name;
    }

    public int getFbWidth() {
        return fbWidth;
    }

    public void setFbWidth(int fbWidth) {
        this.fbWidth = fbWidth;
    }

    public int getFbHeight() {
        return fbHeight;
    }

    public void setFbHeight(int fbHeight) {
        this.fbHeight = fbHeight;
    }

    public ProtocolSettings getSettings() {
        return settings;
    }

    public Transport getTransport() {
        return transport;
    }

    public void setTight(boolean isTight) {
        this.isTight = isTight;
    }

    public boolean isTight() {
        return isTight;
    }

    public void setProtocolVersion(Handshaker.ProtocolVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Handshaker.ProtocolVersion getProtocolVersion() {
        return protocolVersion;
    }

    public void setTunnelType(TunnelType tunnelType) {
        this.tunnelType = tunnelType;
    }

    public TunnelType getTunnelType() {
        return tunnelType;
    }
}