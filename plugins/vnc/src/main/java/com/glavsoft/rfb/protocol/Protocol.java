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
package com.glavsoft.rfb.protocol;

import com.glavsoft.core.SettingsChangedEvent;
import com.glavsoft.exceptions.AuthenticationFailedException;
import com.glavsoft.exceptions.FatalException;
import com.glavsoft.exceptions.TransportException;
import com.glavsoft.exceptions.UnsupportedProtocolVersionException;
import com.glavsoft.exceptions.UnsupportedSecurityTypeException;
import com.glavsoft.rfb.ClipboardController;
import com.glavsoft.rfb.IChangeSettingsListener;
import com.glavsoft.rfb.IRepaintController;
import com.glavsoft.rfb.IRequestString;
import com.glavsoft.rfb.IRfbSessionListener;
import com.glavsoft.rfb.RfbCapabilityInfo;
import com.glavsoft.rfb.client.ClientMessageType;
import com.glavsoft.rfb.client.ClientToServerMessage;
import com.glavsoft.rfb.client.FramebufferUpdateRequestMessage;
import com.glavsoft.rfb.client.SetEncodingsMessage;
import com.glavsoft.rfb.client.SetPixelFormatMessage;
import com.glavsoft.rfb.encoding.EncodingType;
import com.glavsoft.rfb.encoding.PixelFormat;
import com.glavsoft.rfb.encoding.decoder.ByteBuffer;
import com.glavsoft.rfb.encoding.decoder.CopyRectDecoder;
import com.glavsoft.rfb.encoding.decoder.CursorPosDecoder;
import com.glavsoft.rfb.encoding.decoder.Decoder;
import com.glavsoft.rfb.encoding.decoder.DesctopSizeDecoder;
import com.glavsoft.rfb.encoding.decoder.HextileDecoder;
import com.glavsoft.rfb.encoding.decoder.RREDecoder;
import com.glavsoft.rfb.encoding.decoder.RawDecoder;
import com.glavsoft.rfb.encoding.decoder.RichCursorDecoder;
import com.glavsoft.rfb.encoding.decoder.TightDecoder;
import com.glavsoft.rfb.encoding.decoder.ZRLEDecoder;
import com.glavsoft.rfb.encoding.decoder.ZlibDecoder;
import com.glavsoft.rfb.protocol.handlers.Handshaker;
import com.glavsoft.rfb.protocol.tunnel.TunnelType;
import com.glavsoft.transport.BaudrateMeter;
import com.glavsoft.transport.Transport;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class Protocol implements IChangeSettingsListener {
    private final ProtocolContext context;
	private final Logger logger;
	private final IRequestString passwordRetriever;
	private MessageQueue messageQueue;
	private SenderTask senderTask;
	private ReceiverTask receiverTask;
	private IRfbSessionListener rfbSessionListener;
	private IRepaintController repaintController;
	private Thread senderThread;
	private Thread receiverThread;
    private PixelFormat serverPixelFormat;

    private final Map<EncodingType, Decoder> decoders = new LinkedHashMap<EncodingType, Decoder>();
    private final Set<ClientMessageType> clientMessageTypes = new HashSet<ClientMessageType>();
    private boolean inCleanUp = false;
    private boolean isMac;
    private BaudrateMeter baudrateMeter;
    private IRequestString connectionIdRetriever;

    public Protocol(Transport transport, IRequestString passwordRetriever, ProtocolSettings settings) {
        context = new ProtocolContext();
        context.transport = transport;
        this.passwordRetriever = passwordRetriever;
        logger = Logger.getLogger(getClass().getName());
        context.settings = settings;
        decoders.put(EncodingType.RAW_ENCODING, RawDecoder.getInstance());
    }

	public void handshake() throws UnsupportedProtocolVersionException, UnsupportedSecurityTypeException,
			AuthenticationFailedException, TransportException, FatalException {
        context.transport = new Handshaker(this).handshake(getTransport());
		messageQueue = new MessageQueue(); // TODO Why here?
	}

    public IRequestString getPasswordRetriever() {
		return passwordRetriever;
	}

    /**
	 * Following the server initialisation message it's up to the client to send
	 * whichever protocol messages it wants.  Typically it will send a
	 * SetPixelFormat message and a SetEncodings message, followed by a
	 * FramebufferUpdateRequest.  From then on the server will send
	 * FramebufferUpdate messages in response to the client's
	 * FramebufferUpdateRequest messages.  The client should send
	 * FramebufferUpdateRequest messages with incremental set to true when it has
	 * finished processing one FramebufferUpdate and is ready to process another.
	 * With a fast client, the rate at which FramebufferUpdateRequests are sent
	 * should be regulated to avoid hogging the network.
	 */
	public void startNormalHandling(IRfbSessionListener rfbSessionListener,
			IRepaintController repaintController, ClipboardController clipboardController) {
		this.rfbSessionListener = rfbSessionListener;
		this.repaintController = repaintController;
//		if (settings.getColorDepth() == 0) {
//			settings.setColorDepth(pixelFormat.depth); // the same the server sent when not initialized yet
//		}
        correctServerPixelFormat();
		context.setPixelFormat(createPixelFormat(context.settings));
		sendMessage(new SetPixelFormatMessage(context.pixelFormat));
		logger.fine("sent: " + context.pixelFormat);

		sendSupportedEncodingsMessage(context.settings);
		context.settings.addListener(Protocol.this); // to support pixel format (color depth), and encodings changes
		context.settings.addListener(repaintController);

		sendRefreshMessage();
        senderTask = new SenderTask(messageQueue, context.transport, Protocol.this);
        senderThread = new Thread(senderTask, "RfbSenderTask");
        senderThread.start();
		resetDecoders();
		receiverTask = new ReceiverTask(
                context.transport, repaintController,
				clipboardController,
                Protocol.this, baudrateMeter);
        receiverThread = new Thread(receiverTask, "RfbReceiverTask");
		receiverThread.start();
	}

    private void correctServerPixelFormat() {
        // correct true color flag
        if (0 == serverPixelFormat.trueColourFlag) {
            //we don't support color maps, so always set true color flag up
            //and select closest convenient value for bpp/depth
            int depth = serverPixelFormat.depth;
            if (0 == depth) depth = serverPixelFormat.bitsPerPixel;
            if (0 == depth) depth = 24;
            if (depth <= 3) serverPixelFormat = PixelFormat.create3bitColorDepthPixelFormat(serverPixelFormat.bigEndianFlag);
            else if (depth <= 6) serverPixelFormat = PixelFormat.create6bitColorDepthPixelFormat(serverPixelFormat.bigEndianFlag);
            else if (depth <= 8) serverPixelFormat = PixelFormat.create8bitColorDepthBGRPixelFormat(serverPixelFormat.bigEndianFlag);
            else if (depth <= 16) serverPixelFormat = PixelFormat.create16bitColorDepthPixelFormat(serverPixelFormat.bigEndianFlag);
            else serverPixelFormat = PixelFormat.create24bitColorDepthPixelFormat(serverPixelFormat.bigEndianFlag);
        }
        // correct .depth to use actual depth 24 instead of incorrect 32, used by ex. UltraVNC server, that cause
        // protocol incompatibility in ZRLE encoding
        final long significant = serverPixelFormat.redMax << serverPixelFormat.redShift |
                serverPixelFormat.greenMax << serverPixelFormat.greenShift |
                serverPixelFormat.blueMax << serverPixelFormat.blueShift;
        if (32 == serverPixelFormat.bitsPerPixel &&
                ((significant & 0x00ff000000L) == 0 || (significant & 0x000000ffL) == 0) &&
                32 == serverPixelFormat.depth) {
            serverPixelFormat.depth = 24;
        }
    }

    public void sendMessage(ClientToServerMessage message) {
		messageQueue.put(message);
	}

    public void sendSupportedEncodingsMessage(ProtocolSettings settings) {
        final LinkedHashSet<EncodingType> encodings = new LinkedHashSet<EncodingType>();
        final EncodingType preferredEncoding = settings.getPreferredEncoding();
        if (preferredEncoding != EncodingType.RAW_ENCODING) {
            encodings.add(preferredEncoding); // preferred first
        }
        for (final EncodingType e : decoders.keySet()) {
            if (e == preferredEncoding) continue;
            switch (e) {
                case RAW_ENCODING: break;
                case COMPRESS_LEVEL_0 :
                    final int compressionLevel = settings.getCompressionLevel();
                    if (compressionLevel > 0 && compressionLevel < 10) {
                        encodings.add(EncodingType.byId(EncodingType.COMPRESS_LEVEL_0.getId() + compressionLevel));
                    }
                    break;
                case JPEG_QUALITY_LEVEL_0 :
                    final int jpegQuality = settings.getJpegQuality();
                    final int colorDepth = settings.getColorDepth();
                    if (jpegQuality > 0 && jpegQuality < 10 &&
                        (colorDepth == ProtocolSettings.COLOR_DEPTH_24 ||
                        colorDepth == ProtocolSettings.COLOR_DEPTH_SERVER_SETTINGS)) {
                            encodings.add(EncodingType.byId(EncodingType.JPEG_QUALITY_LEVEL_0.getId() + jpegQuality));
                    }
                    break;
                case COPY_RECT:
                    if (settings.isAllowCopyRect()) {
                        encodings.add(EncodingType.COPY_RECT);
                    }
                    break;
                case RICH_CURSOR:
                    if (settings.getMouseCursorTrack() == LocalPointer.HIDE ||
                            settings.getMouseCursorTrack() == LocalPointer.ON) {
                        encodings.add(EncodingType.RICH_CURSOR);
                    }
                    break;
                case CURSOR_POS:
                    if (settings.getMouseCursorTrack() == LocalPointer.HIDE ||
                            settings.getMouseCursorTrack() == LocalPointer.ON) {
                        encodings.add(EncodingType.CURSOR_POS);
                    }
                    break;
                default:
                    encodings.add(e);
            }
        }
		SetEncodingsMessage encodingsMessage = new SetEncodingsMessage(encodings);
		sendMessage(encodingsMessage);
		logger.fine("sent: " + encodingsMessage.toString());
	}

	/**
	 * create pixel format by bpp
	 */
	private PixelFormat createPixelFormat(ProtocolSettings settings) {
		int serverBigEndianFlag = serverPixelFormat.bigEndianFlag;
		switch (settings.getColorDepth()) {
		case ProtocolSettings.COLOR_DEPTH_24:
			return PixelFormat.create24bitColorDepthPixelFormat(serverBigEndianFlag);
		case ProtocolSettings.COLOR_DEPTH_16:
			return PixelFormat.create16bitColorDepthPixelFormat(serverBigEndianFlag);
		case ProtocolSettings.COLOR_DEPTH_8:
            return hackForMacOsXScreenSharingServer(PixelFormat.create8bitColorDepthBGRPixelFormat(serverBigEndianFlag));
		case ProtocolSettings.COLOR_DEPTH_6:
			return hackForMacOsXScreenSharingServer(PixelFormat.create6bitColorDepthPixelFormat(serverBigEndianFlag));
		case ProtocolSettings.COLOR_DEPTH_3:
			return hackForMacOsXScreenSharingServer(PixelFormat.create3bitColorDepthPixelFormat(serverBigEndianFlag));
		case ProtocolSettings.COLOR_DEPTH_SERVER_SETTINGS:
			return serverPixelFormat;
		default:
			// unsupported bpp, use default
			return PixelFormat.create24bitColorDepthPixelFormat(serverBigEndianFlag);
		}
	}

    private PixelFormat hackForMacOsXScreenSharingServer(PixelFormat pixelFormat) {
        if (isMac) {
            pixelFormat.bitsPerPixel = pixelFormat.depth = 16;
        }
        return pixelFormat;
    }

    @Override
	public void settingsChanged(SettingsChangedEvent e) {
		ProtocolSettings settings = (ProtocolSettings) e.getSource();
		if (settings.isChangedEncodings()) {
			sendSupportedEncodingsMessage(settings);
		}
		if (settings.isChangedColorDepth() && receiverTask != null) {
			receiverTask.queueUpdatePixelFormat(createPixelFormat(settings));
		}
	}

	public void sendRefreshMessage() {
		sendMessage(new FramebufferUpdateRequestMessage(0, 0, context.fbWidth, context.fbHeight, false));
		logger.fine("sent: full FB Refresh");
	}

    public void sendFbUpdateMessage() {
        sendMessage(receiverTask.fullscreenFbUpdateIncrementalRequest);
    }

    public void cleanUpSession(String message) {
		cleanUpSession();
		rfbSessionListener.rfbSessionStopped(message);
	}

	public void cleanUpSession() {
        synchronized (this) {
            if (inCleanUp) return;
            inCleanUp = true;
        }
		if (senderTask != null && senderThread.isAlive()) { senderThread.interrupt(); }
		if (receiverTask != null && receiverThread.isAlive()) { receiverThread.interrupt(); }
		if (senderTask != null) {
			try {
				senderThread.join(1000);
			} catch (InterruptedException e) {
				// nop
			}
			senderTask = null;
		}
		if (receiverTask != null) {
			try {
				receiverThread.join(1000);
			} catch (InterruptedException e) {
				// nop
			}
			receiverTask = null;
		}
        synchronized (this) {
            inCleanUp = false;
        }
        ByteBuffer.removeInstance();
	}

    public void setServerPixelFormat(PixelFormat serverPixelFormat) {
        this.serverPixelFormat = serverPixelFormat;
    }

    public ProtocolSettings getSettings() {
        return context.getSettings();
    }

    public Transport getTransport() {
        return context.getTransport();
    }

    public int getFbWidth() {
        return context.getFbWidth();
    }

    public void setFbWidth(int frameBufferWidth) {
        context.setFbWidth(frameBufferWidth);
    }

    public int getFbHeight() {
        return context.getFbHeight();
    }

    public void setFbHeight(int frameBufferHeight) {
        context.setFbHeight(frameBufferHeight);
    }

    public PixelFormat getPixelFormat() {
        return context.getPixelFormat();
    }

    public void setPixelFormat(PixelFormat pixelFormat) {
        context.setPixelFormat(pixelFormat);
        if (repaintController != null) {
            repaintController.setPixelFormat(pixelFormat);
        }
    }

    public void setRemoteDesktopName(String name) {
        context.setRemoteDesktopName(name);
    }

    public String getRemoteDesktopName() {
        return context.getRemoteDesktopName();
    }

    public void setTight(boolean isTight) {
        context.setTight(isTight);
    }

    public boolean isTight() {
        return context.isTight();
    }

    public void setProtocolVersion(Handshaker.ProtocolVersion protocolVersion) {
        context.setProtocolVersion(protocolVersion);
    }

    public Handshaker.ProtocolVersion getProtocolVersion() {
        return context.getProtocolVersion();
    }

    public void registerRfbEncodings() {
        decoders.put(EncodingType.TIGHT, new TightDecoder());
        decoders.put(EncodingType.HEXTILE, new HextileDecoder());
        decoders.put(EncodingType.ZRLE, new ZRLEDecoder());
        decoders.put(EncodingType.ZLIB, new ZlibDecoder());
        decoders.put(EncodingType.RRE, new RREDecoder());
        decoders.put(EncodingType.COPY_RECT, new CopyRectDecoder());

        decoders.put(EncodingType.RICH_CURSOR, new RichCursorDecoder());
        decoders.put(EncodingType.DESKTOP_SIZE, new DesctopSizeDecoder());
        decoders.put(EncodingType.CURSOR_POS, new CursorPosDecoder());
    }

    public void resetDecoders() {
        for (Decoder decoder : decoders.values()) {
            if (decoder != null) {
                decoder.reset();
            }
        }
    }

    public Decoder getDecoderByType(EncodingType type) {
        return decoders.get(type);
    }

    public void registerEncoding(RfbCapabilityInfo capInfo) {
        try {
            final EncodingType encodingType = EncodingType.byId(capInfo.getCode());
            if ( ! decoders.containsKey(encodingType)) {
                final Decoder decoder = encodingType.klass.newInstance();
                if (decoder != null) {
                    decoders.put(encodingType, decoder);
                    logger.finer("Register encoding: " + encodingType);
                }
            }
        } catch (IllegalArgumentException e) {
            logger.finer(e.getMessage());
        } catch (InstantiationException e) {
            logger.warning(e.getMessage());
        } catch (IllegalAccessException e) {
            logger.warning(e.getMessage());
        }
    }

    public void registerClientMessageType(RfbCapabilityInfo capInfo) {
        try {
            final ClientMessageType clientMessageType = ClientMessageType.byId(capInfo.getCode());
            clientMessageTypes.add(clientMessageType);
            logger.finer("Register client message type: " + clientMessageType);
        } catch (IllegalArgumentException e) {
            logger.finer(e.getMessage());
        }
    }

    /**
     * Check whether server is supported for given client-to-server message
     *
     * @param type client-to-server message type to check for
     * @return true when supported
     */
    public boolean isSupported(ClientMessageType type) {
        return clientMessageTypes.contains(type) || ClientMessageType.isStandardType(type   );
    }

    public void setTunnelType(TunnelType tunnelType) {
        context.setTunnelType(tunnelType);
    }

    public TunnelType getTunnelType() {
        return context.getTunnelType();
    }

    public void setMac(boolean isMac) {
        this.isMac = isMac;
    }

    public void setBaudrateMeter(BaudrateMeter baudrateMeter) {
        this.baudrateMeter = baudrateMeter;
    }

    public int kBPS() {
    return baudrateMeter == null ? -1 : baudrateMeter.kBPS();
  }

    public boolean isMac() {
      return isMac;
    }

    public void setConnectionIdRetriever(IRequestString connectionIdRetriever) {
        this.connectionIdRetriever = connectionIdRetriever;
    }

    public IRequestString getConnectionIdRetriever() {
        return connectionIdRetriever;
    }
}
