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

/**
 * A key press or release. Down-flag is non-zero (true) if the key is now pressed, zero
 * (false) if it is now released. The key itself is specified using the "keysym" values
 * defined by the X Window System.
 * 1 - U8  - message-type
 * 1 - U8  - down-flag
 * 2 - -   - padding
 * 4 - U32 - key
 * For most ordinary keys, the "keysym" is the same as the corresponding ASCII value.
 * For full details, see The Xlib Reference Manual, published by O'Reilly &amp; Associates,
 * or see the header file &lt;X11/keysymdef.h&gt; from any X Window System installation.
 */
public class KeyEventMessage implements ClientToServerMessage {

	private final int key;
	private final boolean downFlag;

	public KeyEventMessage(int key, boolean downFlag) {
		this.downFlag = downFlag;
		this.key = key;
	}

	@Override
	public void send(Transport transport) throws TransportException {
		transport.writeByte(ClientMessageType.KEY_EVENT.id)
                .writeByte(downFlag ? 1 : 0)
                .zero(2) // padding
		        .write(key)
                .flush();
	}

	@Override
	public String toString() {
		return "[KeyEventMessage: [down-flag: "+downFlag + ", key: " + key +"("+Integer.toHexString(key)+")]";
	}

}
