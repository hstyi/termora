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
package com.glavsoft.rfb;

import com.glavsoft.exceptions.TransportException;
import com.glavsoft.transport.Transport;

/**
 * Structure used to describe protocol options such as tunneling methods,
 * authentication schemes and message types (protocol versions 3.7t, 3.8t).
 * typedef struct _rfbCapabilityInfo {
 *  CARD32 code;                // numeric identifier
 *  CARD8 vendorSignature[4];   // vendor identification
 *  CARD8 nameSignature[8];     // abbreviated option name
 * } rfbCapabilityInfo;
 */
public class RfbCapabilityInfo {
	/*
	 * Vendors known by TightVNC: standard VNC/RealVNC, TridiaVNC, and TightVNC.
	 * #define rfbStandardVendor "STDV"
	 * #define rfbTridiaVncVendor "TRDV"
	 * #define rfbTightVncVendor "TGHT"
	 */
	public static final String VENDOR_STANDARD = "STDV";
	public static final String VENDOR_TRIADA = "TRDV";
	public static final String VENDOR_TIGHT = "TGHT";

	public static final String TUNNELING_NO_TUNNEL = "NOTUNNEL";

	public static final String AUTHENTICATION_NO_AUTH = "NOAUTH__";
	public static final String AUTHENTICATION_VNC_AUTH ="VNCAUTH_";

	public static final String ENCODING_COPYRECT = "COPYRECT";
	public static final String ENCODING_HEXTILE = "HEXTILE_";
	public static final String ENCODING_ZLIB = "ZLIB____";
	public static final String ENCODING_ZRLE = "ZRLE____";
	public static final String ENCODING_RRE = "RRE_____";
	public static final String ENCODING_TIGHT = "TIGHT___";
	// "Pseudo" encoding types
	public static final String ENCODING_RICH_CURSOR = "RCHCURSR";
	public static final String ENCODING_CURSOR_POS = "POINTPOS";
	public static final String ENCODING_DESKTOP_SIZE = "NEWFBSIZ";

	private int code;
	private String vendorSignature;
	private String nameSignature;
	private boolean enable;

    public RfbCapabilityInfo(int code, String vendorSignature, String nameSignature) {
		this.code = code;
		this.vendorSignature = vendorSignature;
		this.nameSignature = nameSignature;
		enable = true;
	}

    public RfbCapabilityInfo() {
        this(0, "", "");
    }

    public RfbCapabilityInfo readFrom(Transport transport) throws TransportException {
        code = transport.readInt32();
        vendorSignature = transport.readString(4);
        nameSignature = transport.readString(8);
        enable = true;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RfbCapabilityInfo that = (RfbCapabilityInfo) o;
        return code == that.code &&
                nameSignature.equals(that.nameSignature) &&
                vendorSignature.equals(that.vendorSignature);
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + vendorSignature.hashCode();
        result = 31 * result + nameSignature.hashCode();
        return result;
    }

    public void setEnable(boolean enable) {
		this.enable = enable;
	}

	public int getCode() {
		return code;
	}

	public String getVendorSignature() {
		return vendorSignature;
	}

	public String getNameSignature() {
		return nameSignature;
	}

	public boolean isEnabled() {
		return enable;
	}

    @Override
    public String toString() {
        return "RfbCapabilityInfo{" +
                "code=" + code +
                ", vendorSignature='" + vendorSignature + '\'' +
                ", nameSignature='" + nameSignature + '\'' +
                '}';
    }
}
