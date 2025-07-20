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

import java.nio.charset.Charset;
import java.util.Arrays;

import static com.glavsoft.utils.Strings.getBytesWithCharset;

/**
 * ClientCutText
 * The client has new ISO 8859-1 (Latin-1) text in its cut buffer. Ends of lines are repre-
 * sented by the linefeed / newline character (value 10) alone. No carriage-return (value
 * 13) is needed. There is currently no way to transfer text outside the Latin-1 character
 * set.
 * 1      - U8       - 6
 * 3      -          - padding
 * 4      - U32      - length
 * length - U8 array - text
 */
public class ClientCutTextMessage implements ClientToServerMessage {
	private final byte [] bytes;

	public ClientCutTextMessage(String str, Charset charset) {
        final byte[] b = charset != null? getBytesWithCharset(str, charset): str.getBytes();
        this.bytes = Arrays.copyOf(b, b.length);
	}

	@Override
	public void send(Transport transport) throws TransportException {
		transport.writeByte(ClientMessageType.CLIENT_CUT_TEXT.id)
                .zero(3) // padding
		        .writeInt32(bytes.length)
                .write(bytes)
		        .flush();
	}

	@Override
	public String toString() {
		return "ClientCutTextMessage: [length: " + bytes.length +", text: ...]";
	}
}
