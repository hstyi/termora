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
package com.glavsoft.viewer.swing.gui;

import com.glavsoft.viewer.swing.ConnectionPresenter;
import com.glavsoft.viewer.swing.Utils;
import com.glavsoft.viewer.swing.ViewerEventsListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * @author dime at tightvnc.com
 */
public class ConnectionInfoView extends JFrame implements ConnectionView {

    private static final String CANCEL = "Cancel";
    private static final int PAD = 8;
    private static final String CLOSE = "Close";
    private ViewerEventsListener onCloseListener;
    private final ConnectionPresenter presenter;
    private final JLabel messageLabel;
    private final JLabel infoLabel;
    private final JButton cancelOrCloseButton;

    @SuppressWarnings("UnusedDeclaration")
    public ConnectionInfoView(final ViewerEventsListener onCloseListener,
                              final ConnectionPresenter presenter) {
        super("Connection");
        this.onCloseListener = onCloseListener;
        this.presenter = presenter;

        JPanel outerPane = new JPanel(new BorderLayout(PAD, PAD));
        outerPane.setBorder(new EmptyBorder(PAD, 2*PAD, 2*PAD, 2*PAD));
        final java.util.List<Image> applicationIcons = Utils.getApplicationIcons();
        if ( ! applicationIcons.isEmpty()) {
            final JLabel iconLabel = new JLabel(
                    new ImageIcon(applicationIcons.get(applicationIcons.size()-1).getScaledInstance(64, 64, Image.SCALE_SMOOTH)));
            outerPane.add(iconLabel, BorderLayout.WEST);
            iconLabel.setBorder(new EmptyBorder(PAD, 2*PAD, PAD, 2*PAD));
        }
        JPanel listPane = new JPanel();
        outerPane.add(listPane, BorderLayout.CENTER);
        listPane.setLayout(new BoxLayout(listPane, BoxLayout.PAGE_AXIS));
        listPane.add(Box.createVerticalStrut(PAD));


        infoLabel = new JLabel("Connecting...");
        listPane.add(infoLabel);
        listPane.add(Box.createVerticalStrut(PAD));

        messageLabel = new JLabel(" ");
        listPane.add(messageLabel, BorderLayout.CENTER);
        listPane.add(Box.createVerticalStrut(2*PAD));

        cancelOrCloseButton = new JButton(CANCEL);
        cancelOrCloseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ConnectionInfoView.this.dispatchEvent(new WindowEvent(
                        ConnectionInfoView.this, WindowEvent.WINDOW_CLOSING));
            }
        });
        JPanel buttonPane = new JPanel();
        buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.LINE_AXIS));
        buttonPane.add(Box.createHorizontalGlue());
        buttonPane.add(cancelOrCloseButton);
        listPane.add(buttonPane);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                super.windowClosing(windowEvent);
                presenter.cancelConnection();
                closeView();
                closeApp();
            }
        });

        add(outerPane);
        getRootPane().setDefaultButton(cancelOrCloseButton);
        setMinimumSize(new Dimension(300, 150));
        Utils.decorateDialog(this);
        Utils.centerWindow(this);
    }

    @Override
    public void showView() {
        setVisible(true);
        toFront();
        repaint();
    }

    @Override
    public void closeView() {
        setVisible(false);
    }

    @Override
    public void showReconnectDialog(String title, String message) {
        int val = JOptionPane.showConfirmDialog(this,
                message + "\nTry another connection?",
                title,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (JOptionPane.NO_OPTION == val) {
            presenter.setNeedReconnection(false);
            closeView();
            dispose();
            closeApp();
        }
    }

    @Override
    public void showConnectionErrorDialog(String message) {
        JOptionPane.showMessageDialog(this, message, "Connection error", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void closeApp() {
        if (onCloseListener != null) {
            onCloseListener.onViewerComponentClosing();
        }
    }

    @Override
    public JFrame getFrame() {
        return this;
    }

    /*
     * Implicit View interface
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setMessage(String message) {
        if (message.isEmpty()) {
            cancelOrCloseButton.setText(CANCEL);
        }
        if ("Cancelled".equals(message)) {
            cancelOrCloseButton.setText(CLOSE);
        }
        messageLabel.setText(message);
        pack();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setHostName(String hostName) {
        infoLabel.setText("Connecting to host '" + hostName + "'");
        pack();
    }
    /*
     * /Implicit View interface
     */

}
