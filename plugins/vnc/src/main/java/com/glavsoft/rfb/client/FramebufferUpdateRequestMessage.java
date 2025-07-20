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
package com.glavsoft.rfb.client;

import com.glavsoft.exceptions.TransportException;
import com.glavsoft.transport.Transport;


public class FramebufferUpdateRequestMessage implements ClientToServerMessage {
	private final boolean incremental;
	private final int height;
	private final int width;
	private final int y;
	private final int x;

	public FramebufferUpdateRequestMessage(int x, int y, int width,
			int height, boolean incremental) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.incremental = incremental;
	}

	@Override
	public void send(Transport transport) throws TransportException {
		transport.writeByte(ClientMessageType.FRAMEBUFFER_UPDATE_REQUEST.id)
                .writeByte(incremental ? 1 : 0)
                .writeInt16(x)
                .writeInt16(y)
                .writeInt16(width)
                .writeInt16(height)
                .flush();
	}

	@Override
	public String toString() {
		return "FramebufferUpdateRequestMessage: [x: " + x + " y: " + y
		+ " width: " + width + " height: " + height +
		" incremental: " + incremental + "]";
	}

}
