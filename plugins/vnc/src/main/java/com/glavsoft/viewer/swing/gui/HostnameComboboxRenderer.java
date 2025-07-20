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

import com.glavsoft.viewer.settings.ConnectionParams;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;
import java.awt.Font;

/**
 * @author dime at tightvnc.com
 */
public class HostnameComboboxRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        String stringValue = renderListItem((ConnectionParams)value);
        setText(stringValue);
        setFont(getFont().deriveFont(Font.PLAIN));
        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }
        return this;
    }

    public String renderListItem(ConnectionParams cp) {
        String s = "<html><b>" +cp.hostName + "</b>:" + cp.getPortNumber();
        if (cp.useSsh()) {
            s += " <i>(via com.glavsoft.viewer.swing.ssh://" + cp.sshUserName + "@" + cp.sshHostName + ":" + cp.getSshPortNumber() + ")</i>";
        }
        return s + "</html>";
    }
}
