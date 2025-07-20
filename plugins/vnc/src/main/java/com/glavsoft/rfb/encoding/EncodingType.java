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
package com.glavsoft.rfb.encoding;

import com.glavsoft.rfb.encoding.decoder.CopyRectDecoder;
import com.glavsoft.rfb.encoding.decoder.CursorPosDecoder;
import com.glavsoft.rfb.encoding.decoder.Decoder;
import com.glavsoft.rfb.encoding.decoder.DesctopSizeDecoder;
import com.glavsoft.rfb.encoding.decoder.FakeDecoder;
import com.glavsoft.rfb.encoding.decoder.HextileDecoder;
import com.glavsoft.rfb.encoding.decoder.RREDecoder;
import com.glavsoft.rfb.encoding.decoder.RawDecoder;
import com.glavsoft.rfb.encoding.decoder.RichCursorDecoder;
import com.glavsoft.rfb.encoding.decoder.TightDecoder;
import com.glavsoft.rfb.encoding.decoder.ZRLEDecoder;
import com.glavsoft.rfb.encoding.decoder.ZlibDecoder;

import java.util.LinkedHashSet;

/**
 * Encoding types
 */
public enum EncodingType {
	/**
	 * Desktop data representes as raw bytes stream
	 */
	RAW_ENCODING(0, "Raw", RawDecoder.class),
	/**
	 * Specfies encodings which allow to copy part of image in client's
	 * framebuffer from one place to another.
	 */
	COPY_RECT(1, "CopyRect", CopyRectDecoder.class),
	RRE(2, "RRE", RREDecoder.class),
	/**
	 *  Hextile encoding, uses palettes, filling and raw subencoding
	 */
    HEXTILE(5, "Hextile", HextileDecoder.class),
    /**
     * This encoding is like raw but previously all data compressed with zlib.
     */
    ZLIB(6, "ZLib", ZlibDecoder.class),
	/**
	 * Tight Encoding for slow connection. It is uses raw data, palettes, filling
	 * and jpeg subencodings
	 */
	TIGHT(7, "Tight", TightDecoder.class),
    //ZlibHex(8),
	/**
	 * ZRLE Encoding is like Hextile but previously all data compressed with zlib.
	 */
    ZRLE(16, "ZRLE", ZRLEDecoder.class),

    /**
     * Rich Cursor pseudo encoding which allows to transfer cursor shape
     * with transparency
     */
    RICH_CURSOR(0xFFFFFF11, "RichCursor", RichCursorDecoder.class),
    /**
     * Desktop Size Pseudo encoding allows to notificate client about
     *  remote screen resolution changed.
     */
    DESKTOP_SIZE(0xFFFFFF21, "DesctopSize", DesctopSizeDecoder.class),
    /**
     * Cusros position encoding allows to transfer remote cursor position to
     * client side.
     */
	CURSOR_POS(0xFFFFFF18, "CursorPos", CursorPosDecoder.class),

	COMPRESS_LEVEL_0(0xFFFFFF00 + 0, "CompressionLevel0", FakeDecoder.class),
	COMPRESS_LEVEL_1(0xFFFFFF00 + 1, "CompressionLevel1", null),
	COMPRESS_LEVEL_2(0xFFFFFF00 + 2, "CompressionLevel2", null),
	COMPRESS_LEVEL_3(0xFFFFFF00 + 3, "CompressionLevel3", null),
	COMPRESS_LEVEL_4(0xFFFFFF00 + 4, "CompressionLevel4", null),
	COMPRESS_LEVEL_5(0xFFFFFF00 + 5, "CompressionLevel5", null),
	COMPRESS_LEVEL_6(0xFFFFFF00 + 6, "CompressionLevel6", null),
	COMPRESS_LEVEL_7(0xFFFFFF00 + 7, "CompressionLevel7", null),
	COMPRESS_LEVEL_8(0xFFFFFF00 + 8, "CompressionLevel8", null),
	COMPRESS_LEVEL_9(0xFFFFFF00 + 9, "CompressionLevel9", null),

	JPEG_QUALITY_LEVEL_0(0xFFFFFFE0 + 0, "JpegQualityLevel0", FakeDecoder.class),
	JPEG_QUALITY_LEVEL_1(0xFFFFFFE0 + 1, "JpegQualityLevel1", null),
	JPEG_QUALITY_LEVEL_2(0xFFFFFFE0 + 2, "JpegQualityLevel2", null),
	JPEG_QUALITY_LEVEL_3(0xFFFFFFE0 + 3, "JpegQualityLevel3", null),
	JPEG_QUALITY_LEVEL_4(0xFFFFFFE0 + 4, "JpegQualityLevel4", null),
	JPEG_QUALITY_LEVEL_5(0xFFFFFFE0 + 5, "JpegQualityLevel5", null),
	JPEG_QUALITY_LEVEL_6(0xFFFFFFE0 + 6, "JpegQualityLevel6", null),
	JPEG_QUALITY_LEVEL_7(0xFFFFFFE0 + 7, "JpegQualityLevel7", null),
	JPEG_QUALITY_LEVEL_8(0xFFFFFFE0 + 8, "JpegQualityLevel8", null),
	JPEG_QUALITY_LEVEL_9(0xFFFFFFE0 + 9, "JpegQualityLevel9", null);

	private final int id;
	private final String name;
    public final Class<? extends Decoder> klass;

    private EncodingType(int id, String name, Class<? extends Decoder> klass) {
		this.id = id;
		this.name = name;
        this.klass = klass;
    }

	public int getId() {
		return id;
	}
	public String getName() {
		return name;
	}

	public static final LinkedHashSet<EncodingType> ordinaryEncodings = new LinkedHashSet<EncodingType>();
	static {
		ordinaryEncodings.add(ZRLE);
		ordinaryEncodings.add(TIGHT);
		ordinaryEncodings.add(ZLIB);
		ordinaryEncodings.add(HEXTILE);
		ordinaryEncodings.add(RRE);
		ordinaryEncodings.add(COPY_RECT);
		ordinaryEncodings.add(RAW_ENCODING);
	}

	public static final LinkedHashSet<EncodingType> pseudoEncodings = new LinkedHashSet<EncodingType>();
	static {
		pseudoEncodings.add(RICH_CURSOR);
		pseudoEncodings.add(CURSOR_POS);
		pseudoEncodings.add(DESKTOP_SIZE);
	}

	public static EncodingType byId(int id) {
		// TODO needs to speedup with hash usage?
		for (EncodingType type : values()) {
			if (type.getId() == id)
				return type;
		}
		throw new IllegalArgumentException("Unsupported encoding code: " + id);
	}

}
