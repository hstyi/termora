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
import com.glavsoft.rfb.encoding.EncodingType;
import com.glavsoft.transport.Transport;

import java.util.Set;

public class SetEncodingsMessage implements ClientToServerMessage {
	private final Set<EncodingType> encodings;

	public SetEncodingsMessage(Set<EncodingType> set) {
		this.encodings = set;
	}

	@Override
	public void send(Transport transport) throws TransportException {
		transport.writeByte(ClientMessageType.SET_ENCODINGS.id)
                .zero(1) // padding byte
		        .writeInt16(encodings.size());
		for (EncodingType enc : encodings) {
			transport.writeInt32(enc.getId());
		}
		transport.flush();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("SetEncodingsMessage: [encodings: ");
		for (EncodingType enc : encodings) {
			sb.append(enc.name()).append(',');
		}
		sb.setLength(sb.length()-1);
		return sb.append(']').toString();
	}

}
