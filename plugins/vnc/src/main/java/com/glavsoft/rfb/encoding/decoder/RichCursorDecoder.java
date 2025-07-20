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
package com.glavsoft.rfb.encoding.decoder;

import com.glavsoft.drawing.Renderer;
import com.glavsoft.exceptions.TransportException;
import com.glavsoft.transport.Transport;

/**
 * Decoder for RichCursor pseudo encoding
 */
public class RichCursorDecoder extends Decoder {

	@Override
	public void decode(Transport transport, Renderer renderer,
			FramebufferUpdateRectangle rect) throws TransportException {
		int bytesPerPixel = renderer.getBytesPerPixel();
		int length = rect.width * rect.height * bytesPerPixel;
		if (0 == length)
			return;
		byte[] buffer = ByteBuffer.getInstance().getBuffer(length);
		transport.readBytes(buffer, 0, length);

		StringBuilder sb = new StringBuilder(" ");
		for (int i=0; i<length; ++i) {
			sb.append(Integer.toHexString(buffer[i]&0xff)).append(" ");
		}
		int scanLine = (rect.width + 7) / 8;
		byte[] bitmask = new byte[scanLine * rect.height];
		transport.readBytes(bitmask, 0, bitmask.length);

		sb = new StringBuilder(" ");
        for (byte aBitmask : bitmask) {
            sb.append(Integer.toHexString(aBitmask & 0xff)).append(" ");
        }
		int[] cursorPixels = new int[rect.width * rect.height];
		for (int y = 0; y < rect.height; ++y) {
			for (int x = 0; x < rect.width; ++x) {
				int offset = y * rect.width + x;
				cursorPixels[offset] = isBitSet(bitmask[y * scanLine + x / 8], x % 8) ?
					0xFF000000 | renderer.getPixelColor(buffer, offset * bytesPerPixel) :
					0; // transparent
			}
		}
		renderer.createCursor(cursorPixels, rect);
	}

	private boolean isBitSet(byte aByte, int index) {
		return (aByte & 1 << 7 - index) > 0;
	}

}
