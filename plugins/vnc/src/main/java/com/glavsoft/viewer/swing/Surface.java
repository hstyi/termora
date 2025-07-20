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

import com.glavsoft.core.SettingsChangedEvent;
import com.glavsoft.drawing.Renderer;
import com.glavsoft.rfb.IChangeSettingsListener;
import com.glavsoft.rfb.IRepaintController;
import com.glavsoft.rfb.encoding.PixelFormat;
import com.glavsoft.rfb.encoding.decoder.FramebufferUpdateRectangle;
import com.glavsoft.rfb.protocol.Protocol;
import com.glavsoft.rfb.protocol.ProtocolSettings;
import com.glavsoft.transport.Transport;
import com.glavsoft.viewer.settings.LocalMouseCursorShape;
import com.glavsoft.viewer.settings.UiSettings;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;

public class Surface extends JPanel implements IRepaintController, IChangeSettingsListener {

    private int width;
    private int height;
    private SoftCursorImpl cursor;
    private volatile RendererImpl renderer;
    private MouseEventListener mouseEventListener;
    private KeyEventListener keyEventListener;
    private boolean showCursor;
    private ModifierButtonEventListener modifierButtonListener;
    private boolean isUserInputEnabled = false;
    private final Protocol protocol;
    private double scaleFactor;
    public Dimension oldSize;

    @Override
    public boolean isDoubleBuffered() {
        // TODO returning false in some reason may speed ups drawing, but may
        // not. Needed in challenging.
        return false;
    }

    public Surface(Protocol protocol, double scaleFactor, LocalMouseCursorShape mouseCursorShape) {
        this.protocol = protocol;
        this.scaleFactor = scaleFactor;
        this.setFocusable(true);
        init(protocol.getFbWidth(), protocol.getFbHeight());
        oldSize = getPreferredSize();

        if (!protocol.getSettings().isViewOnly()) {
            setUserInputEnabled(true, protocol.getSettings().isConvertToAscii());
        }
        showCursor = protocol.getSettings().isShowRemoteCursor();
        setLocalCursorShape(mouseCursorShape);
    }

    public void setViewerWindow(SwingViewerWindow viewerWindow) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private void setUserInputEnabled(boolean enable, boolean convertToAscii) {
        if (enable == isUserInputEnabled) return;
        isUserInputEnabled = enable;
        if (enable) {
            if (null == mouseEventListener) {
                mouseEventListener = new MouseEventListener(this, protocol, scaleFactor);
            }
            addMouseListener(mouseEventListener);
            addMouseMotionListener(mouseEventListener);
            addMouseWheelListener(mouseEventListener);

            setFocusTraversalKeysEnabled(false);
            if (null == keyEventListener) {
                keyEventListener = new KeyEventListener(protocol);
                if (modifierButtonListener != null) {
                    keyEventListener.addModifierListener(modifierButtonListener);
                }
            }
            keyEventListener.setConvertToAscii(convertToAscii);
            addKeyListener(keyEventListener);
            enableInputMethods(false);
        } else {
            removeMouseListener(mouseEventListener);
            removeMouseMotionListener(mouseEventListener);
            removeMouseWheelListener(mouseEventListener);
            removeKeyListener(keyEventListener);
        }
    }

    @Override
    public Renderer createRenderer(Transport transport, int width, int height, PixelFormat pixelFormat) {
        renderer = new RendererImpl(transport, width, height, pixelFormat);
        cursor = renderer.getCursor();
        if (SwingUtilities.isEventDispatchThread()) {
            init(renderer.getWidth(), renderer.getHeight());
            updateFrameSize();
        } else {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        init(renderer.getWidth(), renderer.getHeight());
                        updateFrameSize();
                    }
                });
            } catch (InterruptedException e) {
                Logger.getLogger(getClass().getName()).severe("Interrupted: " + e.getMessage());
                protocol.cleanUpSession("Interrupted: " + e.getMessage());
            } catch (InvocationTargetException e) {
                Logger.getLogger(getClass().getName()).severe("Fatal error: " + e.getCause().getMessage());
                protocol.cleanUpSession("Fatal error: " + e.getCause().getMessage());
            }
        }
        return renderer;
    }

    private void init(int width, int height) {
        this.width = width;
        this.height = height;
        setSize(getPreferredSize());
    }

    private void updateFrameSize() {
        setSize(getPreferredSize());
//		viewerWindow.pack();
        requestFocus();
    }

    @Override
    public void paintComponent(Graphics g) { // EDT
        if (null == renderer) return;

        if (scaleFactor != 1.0) {
            ((Graphics2D) g).scale(scaleFactor, scaleFactor);
        }


        final Object appleContentScaleFactor = getToolkit().getDesktopProperty("apple.awt.contentScaleFactor");
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_RENDERING,
                (appleContentScaleFactor != null && (Integer) appleContentScaleFactor != 1) ?
                        RenderingHints.VALUE_RENDER_SPEED : // speed for Apple Retina display
                        RenderingHints.VALUE_RENDER_DEFAULT); // quality for others
        renderer.paintImageOn(g, super.getWidth(), super.getHeight(),scaleFactor); // internally locked with renderer.lock
        if (showCursor) {
            renderer.paintCursorOn(g, scaleFactor != 1);// internally locked with cursor.lock
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension((int) (this.width * scaleFactor), (int) (this.height * scaleFactor));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    /**
     * Saves protocol and simply invokes native JPanel repaint method which
     * asyncroniously register repaint request using invokeLater to repaint be
     * runned in Swing event dispatcher thread. So may be called from other
     * threads.
     */
    @Override
    public void repaintBitmap(FramebufferUpdateRectangle rect) {
        repaintBitmap(rect.x, rect.y, rect.width, rect.height);
    }

    @Override
    public void repaintBitmap(int x, int y, int width, int height) {
        repaint((int) (x * scaleFactor), (int) (y * scaleFactor),
                (int) Math.ceil(width * scaleFactor), (int) Math.ceil(height * scaleFactor));
    }

    @Override
    public void repaintCursor() {
        synchronized (cursor.getLock()) {
            repaint((int) (cursor.oldRX * scaleFactor), (int) (cursor.oldRY * scaleFactor),
                    (int) Math.ceil(cursor.oldWidth * scaleFactor) + 1, (int) Math.ceil(cursor.oldHeight * scaleFactor) + 1);
            repaint((int) (cursor.rX * scaleFactor), (int) (cursor.rY * scaleFactor),
                    (int) Math.ceil(cursor.width * scaleFactor) + 1, (int) Math.ceil(cursor.height * scaleFactor) + 1);
        }
    }

    @Override
    public void updateCursorPosition(short x, short y) {
        synchronized (cursor.getLock()) {
            cursor.updatePosition(x, y);
            repaintCursor();
        }
    }

    private void showCursor(boolean show) {
        synchronized (cursor.getLock()) {
            showCursor = show;
        }
    }

    public void addModifierListener(ModifierButtonEventListener modifierButtonListener) {
        this.modifierButtonListener = modifierButtonListener;
        if (keyEventListener != null) {
            keyEventListener.addModifierListener(modifierButtonListener);
        }
    }

    @Override
    public void settingsChanged(SettingsChangedEvent e) {
        if (ProtocolSettings.isRfbSettingsChangedFired(e)) {
            ProtocolSettings settings = (ProtocolSettings) e.getSource();
            setUserInputEnabled(!settings.isViewOnly(), settings.isConvertToAscii());
            showCursor(settings.isShowRemoteCursor());
        } else if (UiSettings.isUiSettingsChangedFired(e)) {
            UiSettings uiSettings = (UiSettings) e.getSource();
            oldSize = getPreferredSize();
            scaleFactor = uiSettings.getScaleFactor();
            if (uiSettings.isChangedMouseCursorShape()) {
                setLocalCursorShape(uiSettings.getMouseCursorShape());
            }
        }
        mouseEventListener.setScaleFactor(scaleFactor);
        updateFrameSize();
    }

    public void setLocalCursorShape(LocalMouseCursorShape cursorShape) {
        if (LocalMouseCursorShape.SYSTEM_DEFAULT == cursorShape) {
            setCursor(Cursor.getDefaultCursor());
        } else {
            setCursor(Utils.getCursor(cursorShape));
        }
    }

    @Override
    public void setPixelFormat(PixelFormat pixelFormat) {
        if (renderer != null) {
            renderer.initColorDecoder(pixelFormat);
        }
    }

}
