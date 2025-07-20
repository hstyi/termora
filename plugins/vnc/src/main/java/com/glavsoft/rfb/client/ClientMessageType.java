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

/**
 * @author dime at glavsoft.com
 */
public enum ClientMessageType {
    SET_PIXEL_FORMAT(0),
	SET_ENCODINGS(2),
	FRAMEBUFFER_UPDATE_REQUEST(3),
	KEY_EVENT(4),
	POINTER_EVENT(5),
	CLIENT_CUT_TEXT(6),

    VIDEO_RECTANGLE_SELECTION(151),
    VIDEO_FREEZE(152);

    final public int id;

    ClientMessageType(int id) {
        this.id = id;
    }

    private static final ClientMessageType [] standardTypes =
            {SET_PIXEL_FORMAT, SET_ENCODINGS, FRAMEBUFFER_UPDATE_REQUEST, KEY_EVENT, POINTER_EVENT, CLIENT_CUT_TEXT};

    public static boolean isStandardType(ClientMessageType type) {
        for (final ClientMessageType it : standardTypes) {
            if (it == type) {
                return true;
            }
        }
        return false;
    }

    public static ClientMessageType byId(int id) {
		for (ClientMessageType type : values()) {
			if (type.id == id)
				return type;
		}
		throw new IllegalArgumentException("Unsupported client message type: " + id);
	}

}
