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
package com.glavsoft.viewer.swing.mac;

import com.glavsoft.utils.LazyLoaded;

import java.awt.Image;
import java.awt.Toolkit;
import java.net.URL;

/**
 * @author dime at tightvnc.com
 */
public class MacUtils {
    private static LazyLoaded<Boolean> isMac = new LazyLoaded<Boolean>(new LazyLoaded.Loader<Boolean>() {
        @Override
        public Boolean load() {
            try {
                Class.forName("com.apple.eawt.Application");
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
    });

    public static boolean isMac() {
        return isMac.get();
    }

    public static Image getIconImage() {
        URL resource = MacUtils.class.getResource("/com/glavsoft/viewer/images/tightvnc-logo-128x128.png");
        return resource != null ?
                Toolkit.getDefaultToolkit().createImage(resource) :
                null;
    }

    public static void setName(String name) {
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", name);
    }
}
