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
package com.glavsoft.viewer.swing.ssh;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;


public class PrefsHelper {
    private static Logger logger = Logger.getLogger(PrefsHelper.class.getName());

    static void clearNode(Preferences node) {
        try { // clear wrong data
            logger.finer("Clear wrong data from preferences node " + node.name());
            node.clear();
            node.sync();
        } catch (BackingStoreException e) {
            logger.warning("Cannot clear/sync preferences node '"+ node.name() +"': " + e.getMessage());
        }
    }

    static void addRecordTo(Preferences node, String key, String record) throws IOException {
        String out = getStringFrom(node, key);
        if (out.length() > 0 && ! out.endsWith("\n")) {
            out += ('\n');
        }
        out += record + '\n';
        clearNode(node);
        update(node, key, out);
    }

    private static void update(Preferences node, String key, String value) {
        final int length = value.length();
        if (length <= Preferences.MAX_VALUE_LENGTH) {
            node.put(key, value);
        } else {
            for(int idx = 0, cnt = 1 ; idx < length ; ++cnt) {
                if ((length - idx) > Preferences.MAX_VALUE_LENGTH) {
                    node.put(key + "." + cnt, value.substring(idx, idx + Preferences.MAX_VALUE_LENGTH));
                    idx += Preferences.MAX_VALUE_LENGTH;
                } else {
                    node.put(key + "." + cnt, value.substring(idx));
                    idx = length;
                }
            }
        }
        try {
            node.sync();
        } catch (BackingStoreException e) {
            logger.warning("Cannot sync preferences node '"+ node.name() +"': " + e.getMessage());
        }
    }

    static String getStringFrom(Preferences sshNode, String key) {
        StringBuilder out = new StringBuilder();
        try {
            final String str = sshNode.get(key, "");
            out.append(str);
            for (int cnt = 1; ; ++cnt) {
                final String partKey = key + "." + cnt;
                String part = sshNode.get(partKey, "");
                if (part.length() > 0) out.append(part);
                else break;
            }
        } catch (Exception r) {
            logger.warning("Wrong data at '"+ sshNode.absolutePath() + "#" + key +"' prefs: " + r.getMessage());
            clearNode(sshNode);
        }
        logger.finer("KnownHosts: \n" + out.toString());
        return out.toString();
    }
}