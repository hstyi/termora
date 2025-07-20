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

import com.glavsoft.rfb.protocol.Protocol;
import com.glavsoft.rfb.protocol.ProtocolSettings;
import com.glavsoft.viewer.settings.UiSettings;

import java.awt.Container;

/**
 * Factory that creates SwingViewerWindow with a number of params
 *
 * @author dime at tightvnc.com
 */
public class SwingViewerWindowFactory {

    private final boolean isSeparateFrame;
    private boolean isApplet = false;
    private final ViewerEventsListener viewerEventsListener;
    private Container externalContainer;
    private String appName;

    /**
     * Construct factory for creating SwingViewerWindow at separate frame
     *
     * @param viewerEventsListener the listener for closing app events
     */
    public SwingViewerWindowFactory(ViewerEventsListener viewerEventsListener) {
        this(true, viewerEventsListener);
    }

    /**
     * Construct factory for creating SwingViewerWindow
     *
     * @param isSeparateFrame if true, creates at the separate frame, else use external container
     *  @see #setExternalContainer(Container) and put viewer window into the container
     *
     * @param viewerEventsListener the listener for closing app events
     */
    public SwingViewerWindowFactory(boolean isSeparateFrame,
                                    ViewerEventsListener viewerEventsListener) {
        this.isSeparateFrame = isSeparateFrame;
        this.viewerEventsListener = viewerEventsListener;
    }

    /**
     * Creates SwingViewerWindow
     * @see SwingViewerWindow
     *
     * @param workingProtocol Protocol object, represents network session that is 'connected' to remote host
     * @param rfbSettings rfb protocol settings currently used at the session
     * @param uiSettings gui settings currently used to for displaying viewer window
     * @param connectionString used to show window title string for displaying remote host name
     * @param presenter ConnectionPresenter that response for reconnection and connection history manipulation
     * @return the SwingViewerWindow
     */
    public SwingViewerWindow createViewerWindow(Protocol workingProtocol,
                                                ProtocolSettings rfbSettings, UiSettings uiSettings,
                                                String connectionString, ConnectionPresenter presenter) {
        // TODO do we need in presenter here? or split to to history handling and reconnection ability
        Surface surface = new Surface(workingProtocol, uiSettings.getScaleFactor(), uiSettings.getMouseCursorShape());
        final SwingViewerWindow viewerWindow = new SwingViewerWindow(workingProtocol, rfbSettings, uiSettings,
                surface, isSeparateFrame, isApplet, viewerEventsListener, appName, connectionString, presenter, externalContainer);
        surface.setViewerWindow(viewerWindow);
        viewerWindow.setRemoteDesktopName(workingProtocol.getRemoteDesktopName());
        rfbSettings.addListener(viewerWindow);
        uiSettings.addListener(surface);
        return viewerWindow;
    }

    /**
     * Sets external container that will be used to contain viewer window
     *
     * @param externalContainer
     */
    public void setExternalContainer(Container externalContainer) {
        this.externalContainer = externalContainer;
    }

    /**
     * Sets whether the application starts as browser applet
     *
     * @param isApplet
     */
    public void setIsApplet(boolean isApplet) {
        this.isApplet = isApplet;
    }

    /**
     * Sets the application name string to show it at info dialog
     * @param appName application name
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }
}
