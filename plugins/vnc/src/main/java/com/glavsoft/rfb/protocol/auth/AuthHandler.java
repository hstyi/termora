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
package com.glavsoft.rfb.protocol.auth;

import com.glavsoft.exceptions.AuthenticationFailedException;
import com.glavsoft.exceptions.ClosedConnectionException;
import com.glavsoft.exceptions.FatalException;
import com.glavsoft.exceptions.TransportException;
import com.glavsoft.exceptions.UnsupportedSecurityTypeException;
import com.glavsoft.rfb.encoding.ServerInitMessage;
import com.glavsoft.rfb.protocol.Protocol;
import com.glavsoft.transport.Transport;

import java.util.logging.Logger;

public abstract class AuthHandler {
    private static final int AUTH_RESULT_OK = 0;
//	private static final int AUTH_RESULT_FAILED = 1;
    private Logger logger;

    /**
     * Not thread safe, no need to be thread safe
     */
    protected Logger logger() {
        if (null == logger) {
            logger = Logger.getLogger(getClass().getName());
        }
        return logger;
    }
    /**
	 * Authenticate using appropriate auth scheme
     *
     * @param transport transport for i/o
     * @param protocol rfb protocol object
     * @return transport for future i/o using
	 */
	public abstract Transport authenticate(Transport transport, Protocol protocol)
		throws TransportException, FatalException, UnsupportedSecurityTypeException;
	public abstract SecurityType getType();
	public int getId() {
		return getType().getId();
	}
	public String getName() {
		return getType().name();
	}

    /**
     * Check Security Result received from server
     * May be:
     * * 0 - OK
     * * 1 - Failed
     *
     * Do not check on NoneAuthentication
     */
    public void checkSecurityResult(Transport transport) throws TransportException,
            AuthenticationFailedException {
        final int securityResult = transport.readInt32();
        logger().fine("Security result: " + securityResult + (AUTH_RESULT_OK == securityResult ? " (OK)" : " (Failed)"));
        if (securityResult != AUTH_RESULT_OK) {
            try {
                String reason = transport.readString();
                logger().fine("Security result reason: " + reason);
                throw new AuthenticationFailedException(reason);
            } catch (ClosedConnectionException e) {
                // protocol version 3.3 and 3.7 does not send reason string,
                // but silently closes the connection
                throw new AuthenticationFailedException("Authentication failed");
            }
        }
    }

    public void initProcedure(Transport transport, Protocol protocol) throws TransportException {
        sendClientInitMessage(transport, protocol.getSettings().getSharedFlag());
        ServerInitMessage serverInitMessage = readServerInitMessage(transport);
		completeContextData(serverInitMessage, protocol);
        protocol.registerRfbEncodings();
    }

    protected ServerInitMessage readServerInitMessage(Transport transport) throws TransportException {
        final ServerInitMessage serverInitMessage = new ServerInitMessage().readFrom(transport);
        logger().fine("Read: " + serverInitMessage);
        return serverInitMessage;
	}

    protected void sendClientInitMessage(Transport transport, byte sharedFlag) throws TransportException {
        logger().fine("Sent client-init-message: " + sharedFlag);
        transport.writeByte(sharedFlag).flush();
    }

    protected void completeContextData(ServerInitMessage serverInitMessage, Protocol protocol) {
		protocol.setServerPixelFormat(serverInitMessage.getPixelFormat());
		protocol.setFbWidth(serverInitMessage.getFramebufferWidth());
		protocol.setFbHeight(serverInitMessage.getFramebufferHeight());
		protocol.setRemoteDesktopName(serverInitMessage.getName());
	}
}
