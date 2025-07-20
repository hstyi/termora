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

public class PointerEventMessage implements ClientToServerMessage {
	private final byte buttonMask;
	private final short x;
	private final short y;

	public PointerEventMessage(byte buttonMask, short x, short y) {
		this.buttonMask = buttonMask;
		this.x = x;
		this.y = y;
	}

	@Override
	public void send(Transport transport) throws TransportException {
		transport.writeByte(ClientMessageType.POINTER_EVENT.id)
                .writeByte(buttonMask)
                .writeInt16(x)
                .writeInt16(y)
                .flush();
	}

	@Override
	public String toString() {
		return "PointerEventMessage: [x: "+ x +", y: "+ y + ", button-mask: " +
		buttonMask +"]";
	}

}
