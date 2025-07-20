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

import com.glavsoft.exceptions.FatalException;
import com.glavsoft.exceptions.TransportException;
import com.glavsoft.exceptions.UnsupportedSecurityTypeException;
import com.glavsoft.rfb.RfbCapabilityInfo;
import com.glavsoft.rfb.encoding.ServerInitMessage;
import com.glavsoft.rfb.protocol.Protocol;
import com.glavsoft.rfb.protocol.tunnel.TunnelHandler;
import com.glavsoft.rfb.protocol.tunnel.TunnelType;
import com.glavsoft.transport.Transport;
import com.glavsoft.utils.Strings;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class TightAuthentication extends AuthHandler {
    private final Map<Integer, AuthHandler> registeredAuthHandlers = new HashMap<Integer, AuthHandler>();
    private final Map<Integer, TunnelHandler> registeredTunnelHandlers = new HashMap<Integer, TunnelHandler>();

    public TightAuthentication() {
    }

    public void registerTunnelingHandler(TunnelHandler handler) {
        registeredTunnelHandlers.put(handler.getId(), handler);
    }

    public void registerAuthHandler(AuthHandler handler) {
        registeredAuthHandlers.put(handler.getId(), handler);
    }

    @Override
	public SecurityType getType() {
		return SecurityType.TIGHT_AUTHENTICATION;
	}

    @Override
    public Transport authenticate(Transport transport, Protocol protocol)
            throws TransportException, FatalException, UnsupportedSecurityTypeException {
        transport = tunnelingNegotiation(transport, protocol);
        authorizationNegotiation(transport, protocol);
        protocol.setTight(true);
        return transport;
    }

    @Override
    public void initProcedure(Transport transport, Protocol protocol) throws TransportException {
        capabilitiesNegotiation(transport, protocol);
		protocol.registerRfbEncodings();
    }

    /**
     * Capabilities negotiation consists of server-to-client message, where server introduces its capabilities,
     * and client-to-server message, which introduces only those client capabilities which are supported by server
     * and encodings supported by server.
     *
     * This data immediately follows the server initialisation message.
     *
     * typedef struct _rfbInteractionCapsMsg {
	 * 		CARD16 nServerMessageTypes;
	 * 		CARD16 nClientMessageTypes;
	 * 		CARD16 nEncodingTypes;
	 * 		CARD16 pad;><------><------>// reserved, must be 0
	 * 		// followed by nServerMessageTypes * rfbCapabilityInfo structures
	 * 		// followed by nClientMessageTypes * rfbCapabilityInfo structures
	 * } rfbInteractionCapsMsg;
	 * #define sz_rfbInteractionCapsMsg 8
     *
     * nServerMessageTypes | UINT16                              | Number of server message types server announces.
     * nClientMessageTypes | UINT16                              | Number of client message types server announces.
     * nEncodingTypes      | UINT16                              | Number of encoding types server announces.
     * ServerMessageTypes  | RFBCAPABILITY x nServerMessageTypes | Server side messages which server supports.
     * ClientMessageTypes  | RFBCAPABILITY x nClientMessageTypes | Client side messages which server supports.
     * Encodings           | RFBCAPABILITY x nEncodingTypes      | Encoding types which server supports.
     *
     * Client replies with message in exactly the same format, listing only those capabilities which are supported
     * both by server and the client.
     * Once all three initialization stages are successfully finished, client and server switch to normal protocol flow.
     *
     */
    void capabilitiesNegotiation(Transport transport, Protocol protocol) throws TransportException {
        sendClientInitMessage(transport, protocol.getSettings().getSharedFlag());
        ServerInitMessage serverInitMessage = readServerInitMessage(transport);

		int nServerMessageTypes = transport.readUInt16();
		int nClientMessageTypes = transport.readUInt16();
		int nEncodingTypes = transport.readUInt16();
        transport.readUInt16(); //padding

        logger().fine("nServerMessageTypes: " + nServerMessageTypes + ", nClientMessageTypes: " + nClientMessageTypes +
            ", nEncodingTypes: " + nEncodingTypes);

        registerServerMessagesTypes(transport, protocol, nServerMessageTypes);
        registerClientMessagesTypes(transport, protocol, nClientMessageTypes);
        registerEncodings(transport, protocol, nEncodingTypes);
		completeContextData(serverInitMessage, protocol);
    }

    private void registerServerMessagesTypes(Transport transport, Protocol protocol, int count) throws TransportException {
        while (count-- > 0) {
            RfbCapabilityInfo capInfoReceived = new RfbCapabilityInfo().readFrom(transport);
            logger().fine("Server message type: " + capInfoReceived.toString());
        }
    }

    private void registerClientMessagesTypes(Transport transport, Protocol protocol, int count) throws TransportException {
        while (count-- > 0) {
            RfbCapabilityInfo capInfoReceived = new RfbCapabilityInfo().readFrom(transport);
            logger().fine("Client message type: " + capInfoReceived.toString());
            protocol.registerClientMessageType(capInfoReceived);
        }
    }

    private void registerEncodings(Transport transport, Protocol protocol, int count) throws TransportException {
		while (count-- > 0) {
			RfbCapabilityInfo capInfoReceived = new RfbCapabilityInfo().readFrom(transport);
			logger().fine("Encoding: " + capInfoReceived.toString());
            protocol.registerEncoding(capInfoReceived);
		}
	}

    /**
	 * Negotiation of Tunneling Capabilities (protocol versions 3.7t, 3.8t)
	 *
	 * If the chosen security type is rfbSecTypeTight, the server sends a list of
	 * supported tunneling methods ("tunneling" refers to any additional layer of
	 * data transformation, such as encryption or external compression.)
	 *
	 * nTunnelTypes specifies the number of following rfbCapabilityInfo structures
	 * that list all supported tunneling methods in the order of preference.
	 *
	 * NOTE: If nTunnelTypes is 0, that tells the client that no tunneling can be
	 * used, and the client should not send a response requesting a tunneling
	 * method.
	 *
	 * typedef struct _rfbTunnelingCapsMsg {
	 *     CARD32 nTunnelTypes;
	 *     //followed by nTunnelTypes * rfbCapabilityInfo structures
	 *  } rfbTunnelingCapsMsg;
	 * #define sz_rfbTunnelingCapsMsg 4
	 * ----------------------------------------------------------------------------
	 * Tunneling Method Request (protocol versions 3.7t, 3.8t)
	 *
	 * If the list of tunneling capabilities sent by the server was not empty, the
	 * client should reply with a 32-bit code specifying a particular tunneling
	 * method.  The following code should be used for no tunneling.
	 *
	 * #define rfbNoTunneling 0
	 * #define sig_rfbNoTunneling "NOTUNNEL"
     */
	Transport tunnelingNegotiation(Transport transport, Protocol protocol)
			throws TransportException {
        Transport newTransport = transport;
		int tunnelsCount;
		tunnelsCount = (int) transport.readUInt32();
        logger().fine("Tunneling capabilities: " + tunnelsCount);
        int [] tunnelCodes = new int[tunnelsCount];
		if (tunnelsCount > 0) {
			for (int i = 0; i < tunnelsCount; ++i) {
				RfbCapabilityInfo rfbCapabilityInfo = new RfbCapabilityInfo().readFrom(transport);
                tunnelCodes[i] = rfbCapabilityInfo.getCode();
                logger().fine(rfbCapabilityInfo.toString());
            }
            int selectedTunnelCode;
            if (tunnelsCount > 0) {
                for (int i = 0; i < tunnelsCount; ++i) {
                    final TunnelHandler tunnelHandler = registeredTunnelHandlers.get(tunnelCodes[i]);
                    if (tunnelHandler != null) {
                        selectedTunnelCode = tunnelCodes[i];
                        transport.writeInt32(selectedTunnelCode).flush();
                        logger().fine("Accepted tunneling type: " + selectedTunnelCode);
                        newTransport = tunnelHandler.createTunnel(transport);
                        logger().fine("Tunnel created: " + TunnelType.byCode(selectedTunnelCode));
                        protocol.setTunnelType(TunnelType.byCode(selectedTunnelCode));
                        break;
                    }
                }
            }
		}
        if (protocol.getTunnelType() == null) {
            protocol.setTunnelType(TunnelType.NOTUNNEL);
            if (tunnelsCount > 0) {
                transport.writeInt32(TunnelType.NOTUNNEL.code).flush();
            }
            logger().fine("Accepted tunneling type: " + TunnelType.NOTUNNEL);
        }
        return newTransport;
	}

	/**
	 * Negotiation of Authentication Capabilities (protocol versions 3.7t, 3.8t)
	 *
	 * After setting up tunneling, the server sends a list of supported
	 * authentication schemes.
	 *
	 * nAuthTypes specifies the number of following rfbCapabilityInfo structures
	 * that list all supported authentication schemes in the order of preference.
	 *
	 * NOTE: If nAuthTypes is 0, that tells the client that no authentication is
	 * necessary, and the client should not send a response requesting an
	 * authentication scheme.
	 *
	 * typedef struct _rfbAuthenticationCapsMsg {
	 *     CARD32 nAuthTypes;
	 *     // followed by nAuthTypes * rfbCapabilityInfo structures
	 * } rfbAuthenticationCapsMsg;
	 * #define sz_rfbAuthenticationCapsMsg 4
	 *
	 */
	void authorizationNegotiation(Transport transport, Protocol protocol)
	throws UnsupportedSecurityTypeException, TransportException, FatalException {
		int authCount;
		authCount = transport.readInt32();
        logger().fine("Auth capabilities: " + authCount);
		byte[] cap = new byte[authCount];
		for (int i = 0; i < authCount; ++i) {
			RfbCapabilityInfo rfbCapabilityInfo = new RfbCapabilityInfo().readFrom(transport);
			cap[i] = (byte) rfbCapabilityInfo.getCode();
			logger().fine(rfbCapabilityInfo.toString());
		}
		AuthHandler authHandler = null;
		if (authCount > 0) {
			for (int i = 0; i < authCount; ++i) {
                authHandler = registeredAuthHandlers.get((int) cap[i]);
                if (authHandler != null) {
					//sending back RFB capability code
					transport.writeInt32(authHandler.getId()).flush();
					break;
				}
			}
    	} else {
    		authHandler = registeredAuthHandlers.get(SecurityType.NONE_AUTHENTICATION.getId());
    	}
        if (null == authHandler) {
            throw new UnsupportedSecurityTypeException("Server auth types: " + Strings.toString(cap) +
                    ", supported auth types: " + registeredAuthHandlers.values());
        }
		logger().fine("Auth capability accepted: " + authHandler.getName());
		authHandler.authenticate(transport, protocol);
	}

}
