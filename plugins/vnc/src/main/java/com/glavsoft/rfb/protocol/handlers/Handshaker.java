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
package com.glavsoft.rfb.protocol.handlers;

import com.glavsoft.exceptions.AuthenticationFailedException;
import com.glavsoft.exceptions.FatalException;
import com.glavsoft.exceptions.TransportException;
import com.glavsoft.exceptions.UnsupportedProtocolVersionException;
import com.glavsoft.exceptions.UnsupportedSecurityTypeException;
import com.glavsoft.rfb.IRequestString;
import com.glavsoft.rfb.protocol.Protocol;
import com.glavsoft.rfb.protocol.auth.AuthHandler;
import com.glavsoft.rfb.protocol.auth.NoneAuthentication;
import com.glavsoft.rfb.protocol.auth.SecurityType;
import com.glavsoft.rfb.protocol.auth.TightAuthentication;
import com.glavsoft.rfb.protocol.auth.VncAuthentication;
import com.glavsoft.rfb.protocol.tunnel.SslTunnel;
import com.glavsoft.rfb.protocol.tunnel.TunnelType;
import com.glavsoft.transport.Transport;
import com.glavsoft.utils.Strings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author dime at glavsoft.com
 */
public class Handshaker {
    private static final int PROTOCOL_STRING_LENGTH = 12;
	private static final String RFB_PROTOCOL_STRING_REGEXP = "^RFB (\\d\\d\\d).(\\d\\d\\d)\n$";
    private static final String DISPATCHER_PROTOCOL_STRING = "TCPDISPATCH\n";

    private static final int MIN_SUPPORTED_VERSION_MAJOR = 3;
	private static final int MIN_SUPPORTED_VERSION_MINOR = 3;

	private static final int MAX_SUPPORTED_VERSION_MAJOR = 3;
	private static final int MAX_SUPPORTED_VERSION_MINOR = 8;
    protected static final int DISPATCHER_PROTOCOL_VERSION = 3;
    protected static final int KEEP_ALIVE_BYTE = 0;
    protected static final int START_BYTE = 1;
    private Protocol protocol;
    private Logger logger;
    private final Map<Integer, AuthHandler> registeredAuthHandlers = new HashMap<Integer, AuthHandler>();

    public Handshaker(Protocol protocol) {
        this.protocol = protocol;
        logger = Logger.getLogger(getClass().getName());
        registerAuthHandler(SecurityType.NONE_AUTHENTICATION.getId(), new NoneAuthentication());
        registerAuthHandler(SecurityType.VNC_AUTHENTICATION.getId(), new VncAuthentication());

        final TightAuthentication tightAuthentication = new TightAuthentication();
        tightAuthentication.registerAuthHandler(new NoneAuthentication());
        tightAuthentication.registerAuthHandler(new VncAuthentication());
        if (protocol.getSettings().getTunnelType() != TunnelType.NOTUNNEL &&
                SslTunnel.isTransportAvailable()) {
            tightAuthentication.registerTunnelingHandler(new SslTunnel());
            registerAuthHandler(SecurityType.TIGHT2_AUTHENTICATION.getId(), tightAuthentication);
        }
        registerAuthHandler(SecurityType.TIGHT_AUTHENTICATION.getId(), tightAuthentication);
    }

    public Transport handshake(Transport transport) throws TransportException, UnsupportedProtocolVersionException, AuthenticationFailedException, FatalException, UnsupportedSecurityTypeException {
        String protocolString = transport.readString(PROTOCOL_STRING_LENGTH);
        if (isDispatcherConnection(protocolString)) {
            handshakeToDispatcher(transport);
            protocolString = transport.readString(PROTOCOL_STRING_LENGTH);
        }
        ProtocolVersion ver = matchProtocolVersion(protocolString);
        transport.write(Strings.getBytesWithCharset("RFB 00" + ver.major + ".00" + ver.minor + "\n", Transport.ISO_8859_1)).flush();
        protocol.setProtocolVersion(ver);
        logger.info("Set protocol version to: " + ver);
        transport = auth(transport, ver);
        return transport;
    }

    /**
     * Make dispatcher connection
     *
     * Dispatcher protocol v.3: '<-' means receive from dispatcher, '->' means send to dispatcher
     * <- "TCPDISPATCH\n" &mdash; already received at this point
     * <- UInt8 numSupportedVersions value
     * <- numSupportedVersions UInt8 values of supported version num
     * -> UInt8 value of version accepted
     * -> UInt8 remoteHostRole value (0 == RFB Server or 1 == RFB Client/viewer)
     * -> UInt32 connId
     * <- UInt32 connId (when 0 == connId, then dispatcher generates unique random connId value
     * 		and sends it to clients, else it doesn't send the one)
     * -> UInt8 secret keyword string length
     * -> String (byte array of ASCII characters) - secret keyword
     * -> UInt8 dispatcher name string length (may equals to 0)
     * -> String (byte array of ASCII characters) - dispatcher name
     * <- UInt8 dispatcher name string length
     * <- String (byte array of ASCII characters) - dispatcher name
     * <- 0 'keep alive byte' or non zero 'start byte' (1)
     * On keep alive byte immediately answer with the same byte, and wain for next byte,
     * on start byte go to ordinary rfb negotiation.
     *
     * @param transport
     *
     * @throws TransportException when some io error happens
     * @throws UnsupportedProtocolVersionException when protocol doesn't match
     * @throws AuthenticationFailedException when connectionId provided by user is wrong
     */
    private void handshakeToDispatcher(Transport transport) throws TransportException, UnsupportedProtocolVersionException, AuthenticationFailedException {
        int numSupportedVersions = transport.readUInt8(); // receive num of supported version followed (u8)
        List<Integer> remoteVersions = new ArrayList<Integer>(numSupportedVersions);
        for (int i = 0; i < numSupportedVersions; ++i) {
            remoteVersions.add(transport.readUInt8()); // receive supported protocol versions (numSupportedVersions x u8)
        }
        logger.fine("Dispatcher protocol versions: " + Arrays.toString(remoteVersions.toArray()));
        if (!remoteVersions.contains(DISPATCHER_PROTOCOL_VERSION)) {
            throw new UnsupportedProtocolVersionException("Dispatcher unsupported protocol versions");
        }
        transport.writeByte(DISPATCHER_PROTOCOL_VERSION); // send protocol version we use (u8)
        transport.writeByte(1).flush(); // send we are the viewer (u8)
        long connectionId = 0;
        IRequestString connIdRetriever = protocol.getConnectionIdRetriever();
        if (null == connIdRetriever) throw new IllegalStateException("ConnectionIdRetriever is null");
        String sId = connIdRetriever.getResult();
        if (Strings.isTrimmedEmpty(sId)) throw new AuthenticationFailedException("ConnectionId is empty");
        try {
            connectionId = Long.parseLong(sId);
        } catch (NumberFormatException nfe) {
            throw new AuthenticationFailedException("Wrong ConnectionId");
        }
        if ( 0 == connectionId) {
            throw new AuthenticationFailedException("ConnectionId have not be equals to zero");
        }
        transport.writeUInt32(connectionId).flush(); // send connectionId (u32)

        transport.writeByte(0); // send UInt8 secret keyword string length. 0 - for none
        // send String (byte array of ASCII characters) - secret keyword.
        // Skip if none
        transport.writeByte(0).flush(); // send UInt8 dispatcher name string length (may equals to 0)
        // send  -> String (byte array of ASCII characters) - dispatcher name.
        // Skip if none
        //logger.fine("Sent: version3, viewer, connectionId: " + connectionId + " secret:0, token: 0");
        int tokenLength = transport.readUInt8(); // receive UInt8 token length
        // receive byte array  - dispatcher token
        byte [] token = transport.readBytes(tokenLength);
        //logger.fine("token: #" + tokenLength + " " + (tokenLength>0?token[0]:"") +(tokenLength>1?token[1]:"")+(tokenLength>2?token[2]:""));
        // receive 0 'keep alive byte' or non zero 'start byte' (1)
        // on keep alive byte send the same to remote
        // on start byte go to starting rfb connection
        int b;
        do {
            b = transport.readByte();
            if (KEEP_ALIVE_BYTE == b) {
                logger.finer("keep-alive");
                transport.writeByte(KEEP_ALIVE_BYTE).flush();
            }
        } while (b != START_BYTE);
        logger.info("Dispatcher handshake completed");
    }

    /**
     * When first 12 bytes sent by server is "TCPDISPATCH\n" this is dispatcher connection
     *
     * @param protocolString string with first 12 bytes sent by server
     * @return true when we connects to dispatcher, not remote rfb server
     */
    private boolean isDispatcherConnection(String protocolString) {
        final boolean dispatcherDetected = DISPATCHER_PROTOCOL_STRING.equals(protocolString);
        if (dispatcherDetected) {
            logger.info("Dispatcher connection detected");
        }
        return dispatcherDetected;
    }

    /**
     * Take first 12 bytes sent by server and match rfb protocol version.
     * RFB protocol version string is "RFB MMM.mmm\n". Where MMM is major
     * protocol version and mmm is minor one.
     *
     * Side effect: set protocol.isMac when MacOs at other side is detected
     *
     * @param protocolString string with first 12 bytes sent by server
     * @return version of protocol will be used
     */
    private ProtocolVersion matchProtocolVersion(String protocolString) throws UnsupportedProtocolVersionException {
        logger.info("Server protocol string: " + protocolString.substring(0, protocolString.length() - 1));
        Pattern pattern = Pattern.compile(RFB_PROTOCOL_STRING_REGEXP);
        final Matcher matcher = pattern.matcher(protocolString);
        if ( ! matcher.matches())
            throw new UnsupportedProtocolVersionException(
                    "Unsupported protocol version: " + protocolString);
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        ProtocolVersion ver;
        boolean isMac = false;
        if (889 == minor) {
            isMac = true;
        }
        if (major < MIN_SUPPORTED_VERSION_MAJOR ||
                MIN_SUPPORTED_VERSION_MAJOR == major && minor < MIN_SUPPORTED_VERSION_MINOR)
            throw new UnsupportedProtocolVersionException(
                    "Unsupported protocol version: " + major + "." + minor);
        if (major > MAX_SUPPORTED_VERSION_MAJOR) {
//            major = MAX_SUPPORTED_VERSION_MAJOR;
            minor = MAX_SUPPORTED_VERSION_MINOR;
        }

		if (minor >= MIN_SUPPORTED_VERSION_MINOR && minor < 7) {
            ver = ProtocolVersion.PROTOCOL_VERSION_3_3;
		} else if (7 == minor) {
            ver = ProtocolVersion.PROTOCOL_VERSION_3_7;
		} else if (minor >= MAX_SUPPORTED_VERSION_MINOR) {
            ver = ProtocolVersion.PROTOCOL_VERSION_3_8;
		} else
			throw new UnsupportedProtocolVersionException("Unsupported protocol version: " + protocolString);
        protocol.setMac(isMac);
        return ver;
    }

    private Transport auth(Transport transport, ProtocolVersion ver) throws UnsupportedSecurityTypeException, TransportException, FatalException, AuthenticationFailedException {
        AuthHandler handler;
        switch (ver) {
            case PROTOCOL_VERSION_3_3:
                handler = auth33(transport);
                break;
            case PROTOCOL_VERSION_3_7:
                handler = auth37_38(transport);
                break;
            case PROTOCOL_VERSION_3_8:
                handler = auth37_38(transport);
                break;
            default:
                throw new IllegalStateException();
        }
        transport = handler.authenticate(transport, protocol);
        if (ver == ProtocolVersion.PROTOCOL_VERSION_3_8 ||
                handler.getType() != SecurityType.NONE_AUTHENTICATION) {
            handler.checkSecurityResult(transport);
        }
        handler.initProcedure(transport, protocol);
        return transport;
    }

    private AuthHandler auth33(Transport transport) throws TransportException, UnsupportedSecurityTypeException {
		int type = transport.readInt32();
        logger.info("Type received: " + type);
		if (0 == type)
			throw new UnsupportedSecurityTypeException(transport.readString());
        AuthHandler handler = registeredAuthHandlers.get(selectAuthHandlerId((byte) (0xff & type)));
        return handler;
    }

    private AuthHandler auth37_38(Transport transport) throws TransportException, UnsupportedSecurityTypeException {
        int secTypesNum = transport.readUInt8();
		if (0 == secTypesNum)
			throw new UnsupportedSecurityTypeException(transport.readString());
		byte[] secTypes = transport.readBytes(secTypesNum);
        logger.info("Security Types received (" + secTypesNum + "): " + Strings.toString(secTypes));
        final int typeIdAccepted = selectAuthHandlerId(secTypes);
        final AuthHandler authHandler = registeredAuthHandlers.get(typeIdAccepted);
        transport.writeByte(typeIdAccepted).flush();
        return authHandler;
    }

    private int selectAuthHandlerId(byte... secTypes)
            throws UnsupportedSecurityTypeException, TransportException {
		AuthHandler handler;
		// Tight2 Authentication very first
		for (byte type : secTypes) {
			if (SecurityType.TIGHT2_AUTHENTICATION.getId() == (0xff & type)) {
				handler = registeredAuthHandlers.get(SecurityType.TIGHT2_AUTHENTICATION.getId());
				if (handler != null) {
                    logger.info("Security Type accepted: " + SecurityType.TIGHT2_AUTHENTICATION.name());
                    return SecurityType.TIGHT2_AUTHENTICATION.getId();
                }
			}
		}
        // Tight Authentication first
        for (byte type : secTypes) {
            if (SecurityType.TIGHT_AUTHENTICATION.getId() == (0xff & type)) {
                handler = registeredAuthHandlers.get(SecurityType.TIGHT_AUTHENTICATION.getId());
                if (handler != null) {
                    logger.info("Security Type accepted: " + SecurityType.TIGHT_AUTHENTICATION.name());
                    return SecurityType.TIGHT_AUTHENTICATION.getId();
                }
            }
        }
		for (byte type : secTypes) {
			handler = registeredAuthHandlers.get(0xff & type);
			if (handler != null) {
                logger.info("Security Type accepted: " + handler.getType());
                return handler.getType().getId();
            }
		}
		throw new UnsupportedSecurityTypeException(
				"No security types supported. Server sent '"
						+ Strings.toString(secTypes)
						+ "' security types, but we do not support any of their.");
	}

    private void registerAuthHandler(int id, AuthHandler handler) {
        registeredAuthHandlers.put(id, handler);
    }

    public static enum ProtocolVersion {
        PROTOCOL_VERSION_3_3(3, 3),
        PROTOCOL_VERSION_3_7(3, 7),
        PROTOCOL_VERSION_3_8(3, 8);

        public final int minor;
        public final int major;

        ProtocolVersion(int major, int minor) {
            this.major = major;
            this.minor = minor;
        }

        @Override
        public String toString() {
            return String.valueOf(major) + "." + String.valueOf(minor);
        }
    }
}
