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

import com.glavsoft.rfb.protocol.ProtocolSettings;
import com.glavsoft.utils.Strings;
import com.glavsoft.utils.ViewerControlApi;
import com.glavsoft.viewer.mvp.Presenter;
import com.glavsoft.viewer.mvp.PropertyNotFoundException;
import com.glavsoft.viewer.settings.ConnectionParams;
import com.glavsoft.viewer.settings.UiSettings;
import com.glavsoft.viewer.settings.WrongParameterException;
import com.glavsoft.viewer.swing.gui.ConnectionView;
import com.glavsoft.viewer.swing.gui.ConnectionsHistory;
import com.glavsoft.viewer.swing.ssh.SshConnectionManager;
import com.glavsoft.viewer.workers.AbstractConnectionWorkerFactory;
import com.glavsoft.viewer.workers.NetworkConnectionWorker;
import com.glavsoft.viewer.workers.RfbConnectionWorker;

import java.net.Socket;
import java.util.logging.Logger;

/**
 * A Presenter (controller) that presents business logic for connection establishing interactions
 *
 * Before starting connection process you have to add View(s) and Model(s), and need to set @see ConnectionWorkerFactory
 *
 * @author dime at tightvnc.com
 */
public class ConnectionPresenter extends Presenter {
    public static final String PROPERTY_HOST_NAME = "HostName";
    public static final String PROPERTY_RFB_PORT_NUMBER = "PortNumber";
    public static final String PROPERTY_USE_SSH = "UseSsh";
    private static final String PROPERTY_SSH_USER_NAME = "SshUserName";
    private static final String PROPERTY_SSH_HOST_NAME = "SshHostName";
    private static final String PROPERTY_SSH_PORT_NUMBER = "SshPortNumber";
    private static final String PROPERTY_STATUS_BAR_MESSAGE = "Message";
    private static final String PROPERTY_CONNECTION_IN_PROGRESS = "ConnectionInProgress";
    public static final String CONNECTION_PARAMS_MODEL = "ConnectionParamsModel";
    public static final String CONNECTIONS_HISTORY_MODEL = "ConnectionsHistoryModel";
    public static final String CONNECTION_VIEW = "ConnectionView";

    private final boolean hasSshSupport;
    private ConnectionsHistory connectionsHistory;
    private ProtocolSettings rfbSettings;
    private UiSettings uiSettings;
    private final Logger logger;
    private RfbConnectionWorker rfbConnectionWorker;
    private AbstractConnectionWorkerFactory connectionWorkerFactory;
    private NetworkConnectionWorker networkConnectionWorker;
    private boolean needReconnection = true;
    private ViewerControlApi viewerControlApi;

    public ConnectionPresenter() {
        this.hasSshSupport = SshConnectionManager.checkForSshSupport();

        logger = Logger.getLogger(getClass().getName());
    }

    public void startConnection(ProtocolSettings rfbSettings, UiSettings uiSettings)
            throws IllegalStateException {
        startConnection(rfbSettings, uiSettings, 0);
    }

    public void startConnection(ProtocolSettings rfbSettings, UiSettings uiSettings, int paramSettingsMask)
            throws IllegalStateException {
        this.rfbSettings = rfbSettings;
        this.uiSettings = uiSettings;
        if (!isModelRegisteredByName(CONNECTION_PARAMS_MODEL)) {
            throw new IllegalStateException("No Connection Params model added.");
        }
        connectionsHistory = new ConnectionsHistory();
        addModel(CONNECTIONS_HISTORY_MODEL, connectionsHistory);
        syncModels(paramSettingsMask);
        show();
        populate();
        if ( ! uiSettings.showConnectionDialog) {
            connect();
        }
    }

    public void setUseSsh(boolean useSsh) {
        setModelProperty(PROPERTY_USE_SSH, useSsh, boolean.class);
    }

    /**
     * Initiate connection process from View
     * ex. by press "Connect" button
     *
     * @param hostName name of host to connect
     */
    public void submitConnection(String hostName) throws WrongParameterException {
        if (Strings.isTrimmedEmpty(hostName)) {
            throw new WrongParameterException("Host name is empty", PROPERTY_HOST_NAME);
        }
        setModelProperty(PROPERTY_HOST_NAME, hostName);

        final String rfbPort = (String) getViewPropertyOrNull(PROPERTY_RFB_PORT_NUMBER);
        setModelProperty(PROPERTY_RFB_PORT_NUMBER, rfbPort);
        try {
            throwPossiblyHappenedException();
        } catch (Throwable e) {
            throw new WrongParameterException("Wrong Port", PROPERTY_HOST_NAME);
        }
        setSshOptions();

        saveHistory();
        populateFrom(CONNECTIONS_HISTORY_MODEL);

        connect();
    }

    /**
     * Cause ConnectionHistory prop to be saved
     */
    void saveHistory() {
        final ConnectionParams cp = (ConnectionParams) getModel(CONNECTION_PARAMS_MODEL);
        connectionsHistory.reorder(cp, rfbSettings, uiSettings);
        connectionsHistory.save();
    }

    /**
     * Prepares async network connection worker and starts to execute it
     * Network connection worker tries to establish tcp connection with remote host
     */
    public void connect() {
        final ConnectionParams connectionParams = (ConnectionParams) getModel(CONNECTION_PARAMS_MODEL);
        if (null == connectionWorkerFactory) {
            throw new IllegalStateException("connectionWorkerFactory is not set");
        }
        networkConnectionWorker = connectionWorkerFactory.createNetworkConnectionWorker();
        networkConnectionWorker.setConnectionParams(connectionParams);
        networkConnectionWorker.setPresenter(this);
        networkConnectionWorker.setHasSshSupport(hasSshSupport);
        networkConnectionWorker.execute();
    }

    /**
     * Callback for connection worker, invoked on failed connection attempt.
     * Both for tcp network connection worker and for rfb connection worker.
     */
    void connectionFailed() {
        cancelConnection();
        reconnect(null);
    }

    /**
     * Callback for connection worker, invoked when connection is cancelled
     */
    void connectionCancelled() {
        cancelConnection();
        enableConnectionDialog();
    }

    private void enableConnectionDialog() {
        setViewProperty(PROPERTY_CONNECTION_IN_PROGRESS, false, boolean.class);
    }

    /**
     * Callback for tcp network connection worker.
     * Invoked on successful connection.
     * Invoked in EDT
     *
     * @param workingSocket a socket binded with established connection
     */
    void successfulNetworkConnection(Socket workingSocket) { // EDT
        logger.info("Connected");
        showMessage("Connected");
        rfbConnectionWorker = connectionWorkerFactory.createRfbConnectionWorker();
        rfbConnectionWorker.setWorkingSocket(workingSocket);
        rfbConnectionWorker.setRfbSettings(rfbSettings);
        rfbConnectionWorker.setUiSettings(uiSettings);
        rfbConnectionWorker.setConnectionString(
                getModelProperty(PROPERTY_HOST_NAME) + ":" + getModelProperty(PROPERTY_RFB_PORT_NUMBER));
        rfbConnectionWorker.execute();
        viewerControlApi = rfbConnectionWorker.getViewerControlApi();
    }

    /**
     * Callback for rfb connection worker
     * Invoked on successful connection
     */
    void successfulRfbConnection() {
        enableConnectionDialog();
        getView(CONNECTION_VIEW).closeView();
    }

    /**
     * Gracefully cancel active connection workers
     */
    public void cancelConnection() {
        logger.finer("Cancel connection");
        if (networkConnectionWorker != null) {
            networkConnectionWorker.cancel();
        }
        if (rfbConnectionWorker != null) {
            rfbConnectionWorker.cancel();
        }
    }

    /**
     * Ask ConnectionView to show dialog
     *
     * @param errorMessage message to show
     */
    void showConnectionErrorDialog(String errorMessage) {
        final ConnectionView connectionView = (ConnectionView) getView(CONNECTION_VIEW);
        if (connectionView != null) {
            connectionView.showConnectionErrorDialog(errorMessage);
        }
    }

    /**
     * Ask ConnectionView to show dialog whether to reconnect or close app
     *
     * @param errorTitle dialog title to show
     * @param errorMessage message to show
     */
    void showReconnectDialog(String errorTitle, String errorMessage) {
        final ConnectionView connectionView = (ConnectionView) getView(CONNECTION_VIEW);
        if (connectionView != null) {
            connectionView.showReconnectDialog(errorTitle, errorMessage);
        }
    }

    private void setSshOptions() {
        if (hasSshSupport) {
            try {
                final boolean useSsh = (Boolean) getViewProperty(PROPERTY_USE_SSH);
                setModelProperty(PROPERTY_USE_SSH, useSsh, boolean.class);
            } catch (PropertyNotFoundException e) {
                //nop
            }
            setModelProperty(PROPERTY_SSH_USER_NAME, getViewPropertyOrNull(PROPERTY_SSH_USER_NAME), String.class);
            setModelProperty(PROPERTY_SSH_HOST_NAME, getViewPropertyOrNull(PROPERTY_SSH_HOST_NAME), String.class);
            setModelProperty(PROPERTY_SSH_PORT_NUMBER, getViewPropertyOrNull(PROPERTY_SSH_PORT_NUMBER));
            setViewProperty(PROPERTY_SSH_PORT_NUMBER, getModelProperty(PROPERTY_SSH_PORT_NUMBER));
        }
    }

    private void syncModels(int paramSettingsMask) {
        final ConnectionParams cp = (ConnectionParams) getModel(CONNECTION_PARAMS_MODEL);
        final ConnectionParams mostSuitableConnection = connectionsHistory.getMostSuitableConnection(cp);
        cp.completeEmptyFieldsFrom(mostSuitableConnection);
        rfbSettings.copyDataFrom(connectionsHistory.getProtocolSettings(mostSuitableConnection), paramSettingsMask & 0xffff);
        uiSettings.copyDataFrom(connectionsHistory.getUiSettingsData(mostSuitableConnection), (paramSettingsMask >> 16) & 0xffff);
        if (!cp.isHostNameEmpty()) {
            connectionsHistory.reorder(cp, rfbSettings, uiSettings);
        }

//        protocolSettings.addListener(connectionsHistory);
//        uiSettings.addListener(connectionsHistory);
    }

    /**
     * populate Model(s) props with data from param passed (network connection related props)
     * populate View(s) props from model(s)
     * get rfb and ui settings from History
     *
     * @param connectionParams connection parameters
     */
    public void populateFromHistoryItem(ConnectionParams connectionParams) {
        setModelProperty(PROPERTY_HOST_NAME, connectionParams.hostName, String.class);
        setModelProperty(PROPERTY_RFB_PORT_NUMBER, connectionParams.getPortNumber(), int.class);
        setModelProperty(PROPERTY_USE_SSH, connectionParams.useSsh(), boolean.class);
        setModelProperty(PROPERTY_SSH_HOST_NAME, connectionParams.sshHostName, String.class);
        setModelProperty(PROPERTY_SSH_PORT_NUMBER, connectionParams.getSshPortNumber(), int.class);
        setModelProperty(PROPERTY_SSH_USER_NAME, connectionParams.sshUserName, String.class);
        populateFrom(CONNECTION_PARAMS_MODEL);
        rfbSettings.copyDataFrom(connectionsHistory.getProtocolSettings(connectionParams));
        uiSettings.copyDataFrom(connectionsHistory.getUiSettingsData(connectionParams));
    }

    /**
     * Forget history records at all
     */
    public void clearHistory() {
        connectionsHistory.clear();
        connectionsHistory.reorder((ConnectionParams) getModel(CONNECTION_PARAMS_MODEL), rfbSettings, uiSettings);
        populateFrom(CONNECTIONS_HISTORY_MODEL);
        clearMessage();
    }

    /**
     * Show status info about currently executed operation
     *
     * @param message status message
     */
    void showMessage(String message) {
        setViewProperty(PROPERTY_STATUS_BAR_MESSAGE, message);
    }

    /**
     * Show empty status bar
     */
    void clearMessage() {
        showMessage("");
    }

    /**
     * Set connection worker factory
     *
     * @param connectionWorkerFactory factory
     */
    public void setConnectionWorkerFactory(AbstractConnectionWorkerFactory connectionWorkerFactory) {
        this.connectionWorkerFactory = connectionWorkerFactory;
    }

    /**
     * Reset presenter and try to start new connection establishing process
     *
     * @param predefinedPassword password
     */
    void reconnect(String predefinedPassword) {
        if (predefinedPassword != null && ! predefinedPassword.isEmpty())
            connectionWorkerFactory.setPredefinedPassword(predefinedPassword);
        clearMessage();
        enableConnectionDialog();
        show();
        populate();
        if ( ! uiSettings.showConnectionDialog) {
            connect();
        }
    }

    void clearPredefinedPassword() {
        connectionWorkerFactory.setPredefinedPassword(null);
    }

    public UiSettings getUiSettings() {
        return uiSettings;
    }

    public ProtocolSettings getRfbSettings() {
        return rfbSettings;
    }

    boolean needReconnection() {
        return needReconnection;
    }

    public void setNeedReconnection(boolean need) {
        needReconnection = need;
    }

    public ViewerControlApi getViewerControlApi() {
        return viewerControlApi;
    }
}
