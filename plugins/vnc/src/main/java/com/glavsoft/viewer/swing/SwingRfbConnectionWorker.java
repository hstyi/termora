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
package com.glavsoft.viewer.swing;

import com.glavsoft.exceptions.AuthenticationFailedException;
import com.glavsoft.exceptions.FatalException;
import com.glavsoft.exceptions.TransportException;
import com.glavsoft.exceptions.UnsupportedProtocolVersionException;
import com.glavsoft.exceptions.UnsupportedSecurityTypeException;
import com.glavsoft.rfb.IRequestString;
import com.glavsoft.rfb.IRfbSessionListener;
import com.glavsoft.rfb.protocol.Protocol;
import com.glavsoft.rfb.protocol.ProtocolSettings;
import com.glavsoft.transport.BaudrateMeter;
import com.glavsoft.transport.Transport;
import com.glavsoft.utils.Strings;
import com.glavsoft.utils.ViewerControlApi;
import com.glavsoft.viewer.settings.UiSettings;
import com.glavsoft.viewer.swing.gui.RequestSomethingDialog;
import com.glavsoft.viewer.workers.ConnectionWorker;
import com.glavsoft.viewer.workers.RfbConnectionWorker;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

/**
* @author dime at tightvnc.com
*/
public class SwingRfbConnectionWorker extends SwingWorker<Void, String> implements RfbConnectionWorker, IRfbSessionListener {

    private String predefinedPassword;
    private ConnectionPresenter presenter;
    private Component parent;
    private SwingViewerWindowFactory viewerWindowFactory;
    private Logger logger;
    private volatile boolean isStoppingProcess;
    private SwingViewerWindow viewerWindow;
    private String connectionString;
    private Protocol workingProtocol;
    private Socket workingSocket;
    private ProtocolSettings rfbSettings;
    private UiSettings uiSettings;
    private ViewerControlApi viewerControlApi;

    @Override
    public Void doInBackground() throws Exception {
        if (null == workingSocket) throw new ConnectionErrorException("Null socket");
        workingSocket.setTcpNoDelay(true); // disable Nagle algorithm
        Transport transport = new Transport(workingSocket);
        final BaudrateMeter baudrateMeter = new BaudrateMeter();
        transport.setBaudrateMeter(baudrateMeter);
        workingProtocol = new Protocol(transport,
                new PasswordChooser(connectionString, parent, this),
                rfbSettings);
        workingProtocol.setConnectionIdRetriever(new ConnectionIdChooser(parent, this));
        viewerControlApi = new ViewerControlApi(workingProtocol, baudrateMeter);
        String message = "Handshaking with remote host";
        logger.info(message);
        publish(message);

        workingProtocol.handshake();
        return null;
    }

    public SwingRfbConnectionWorker(String predefinedPassword, ConnectionPresenter presenter, Component parent,
                                    SwingViewerWindowFactory viewerWindowFactory) {
        this.predefinedPassword = predefinedPassword;
        this.presenter = presenter;
        this.parent = parent;
        this.viewerWindowFactory = viewerWindowFactory;
        logger = Logger.getLogger(getClass().getName());
    }


    @Override
    protected void process(List<String> strings) { // EDT
        String message = strings.get(strings.size() - 1); // get last
        presenter.showMessage(message);
    }

    @Override
    protected void done() { // EDT
        try {
            get();
            presenter.showMessage("Handshake established");
            ClipboardControllerImpl clipboardController =
                    new ClipboardControllerImpl(workingProtocol, rfbSettings.getRemoteCharsetName());
            clipboardController.setEnabled(rfbSettings.isAllowClipboardTransfer());
            rfbSettings.addListener(clipboardController);
            viewerWindow = viewerWindowFactory.createViewerWindow(
                    workingProtocol, rfbSettings, uiSettings, connectionString, presenter);

            workingProtocol.startNormalHandling(this, viewerWindow.getRepaintController(), clipboardController);
            presenter.showMessage("Started");

            presenter.successfulRfbConnection();
        } catch (CancellationException e) {
            logger.info("Cancelled");
            presenter.showMessage("Cancelled");
            presenter.connectionCancelled();
        } catch (InterruptedException e) {
            logger.info("Interrupted");
            presenter.showMessage("Interrupted");
            presenter.connectionFailed();
        } catch (ExecutionException ee) {
            String errorTitle;
            String errorMessage;
            try {
                throw ee.getCause();
            } catch (UnsupportedProtocolVersionException e) {
                errorTitle = "Unsupported Protocol Version";
                errorMessage = e.getMessage();
                logger.severe(errorTitle + ": " + errorMessage);
            } catch (UnsupportedSecurityTypeException e) {
                errorTitle = "Unsupported Security Type";
                errorMessage = e.getMessage();
                logger.severe(errorTitle + ": " + errorMessage);
            } catch (AuthenticationFailedException e) {
                errorTitle = "Authentication Failed";
                errorMessage = e.getMessage();
                logger.severe(errorTitle + ": " + errorMessage);
                presenter.clearPredefinedPassword();
            } catch (TransportException e) {
                errorTitle = "Connection Error";
                final Throwable cause = e.getCause();
                errorMessage = errorTitle + " : " + e.getMessage();
                if (cause != null) {
                    if (cause instanceof EOFException)
                        errorMessage += ", possible reason: remote host not responding.";
                    logger.throwing("", "", cause);
                }
                logger.severe(errorMessage);
            } catch (EOFException e) {
                errorTitle = "Connection Error";
                errorMessage = errorTitle + ": " + e.getMessage();
                logger.severe(errorMessage);
            } catch (IOException e) {
                errorTitle = "Connection Error";
                errorMessage = errorTitle + ":  " + e.getMessage();
                logger.severe(errorMessage);
            } catch (FatalException e) {
                errorTitle = "Connection Error";
                errorMessage = errorTitle + ":    " + e.getMessage();
                logger.severe(errorMessage);
            } catch (Throwable e) {
                errorTitle = "Error";
                errorMessage = errorTitle + ": " + e.getMessage();
                logger.severe(errorMessage);
            }
            presenter.showReconnectDialog(errorTitle, errorMessage);
            presenter.clearMessage();
            presenter.connectionFailed();
        }
    }

    @Override
	public void rfbSessionStopped(final String reason) {
        if (workingProtocol != null) {
			workingProtocol.cleanUpSession();
		}
		if (isStoppingProcess) return;
		cleanUpUISessionAndConnection();
        logger.info("Rfb session stopped: " + reason);
        if (presenter.needReconnection()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    presenter.showReconnectDialog("Connection error", reason);
                    presenter.reconnect(predefinedPassword);
                }
            });
        }
	}

    @Override
    public boolean cancel() {
        boolean res = super.cancel(true);
        if (res && workingProtocol != null) {
            workingProtocol.cleanUpSession();
        }
        cleanUpUISessionAndConnection();
        return res;
    }

    private void cleanUpUISessionAndConnection() {
        synchronized (this) {
		    isStoppingProcess = true;
        }
		if (workingSocket != null && workingSocket.isConnected()) {
			try {
				workingSocket.close();
			} catch (IOException e) { /*nop*/ }
		}
		if (viewerWindow != null) {
            viewerWindow.close();
		}
        synchronized (this) {
		    isStoppingProcess = false;
        }
	}

    @Override
    public void setWorkingSocket(Socket workingSocket) {
        this.workingSocket = workingSocket;
    }

    @Override
    public void setRfbSettings(ProtocolSettings rfbSettings) {
        this.rfbSettings = rfbSettings;
    }

    @Override
    public void setUiSettings(UiSettings uiSettings) {
        this.uiSettings = uiSettings;
    }

    @Override
    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    /**
     * Ask user for password if needed
     */
    private class PasswordChooser implements IRequestString {
        private String connectionString;
        private final Component parent;
        private final ConnectionWorker onCancel;

        private PasswordChooser(String connectionString, Component parent, ConnectionWorker onCancel) {
            this.connectionString = connectionString;
            this.parent = parent;
            this.onCancel = onCancel;
        }

        @Override
        public String getResult() {
            return Strings.isTrimmedEmpty(predefinedPassword) ?
                    askPassword() :
                    predefinedPassword;
        }

        private String askPassword() {
            RequestSomethingDialog dialog =
                    new RequestSomethingDialog(parent, "VNC Authentication", true,
                            "Server '" + connectionString + "' requires VNC authentication", "Password:")
                        .setOkLabel("Login")
                        .setInputFieldLength(12);
            if (!dialog.askResult()) {
                onCancel.cancel();
            }
            return dialog.getResult();
        }
    }

    @Override
    public ViewerControlApi getViewerControlApi() {
        return viewerControlApi;
    }

    private class ConnectionIdChooser implements IRequestString {
        private final Component parent;
        private final ConnectionWorker<Void> onCancel;

        public ConnectionIdChooser(Component parent, ConnectionWorker<Void> onCancel) {
            this.parent = parent;
            this.onCancel = onCancel;
        }

        @Override
        public String getResult() {
            RequestSomethingDialog dialog =
                    new RequestSomethingDialog(parent, "TcpDispatcher ConnectionId", false,
                            "TcpDispatcher requires Connection Id.",
                            "Please get the Connection Id from you peer by any other communication channel\n(ex. phone call or IM) and insert it into the form field below.",
                            "Connection Id:")
                            .setInputFieldLength(18);
            if (!dialog.askResult()) {
                onCancel.cancel();
            }
            return dialog.getResult();
        }
    }
}
