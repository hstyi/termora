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

import com.glavsoft.exceptions.CommonException;
import com.glavsoft.utils.LazyLoaded;

import java.awt.Image;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author dime at tightvnc.com
 */
public class MacApplicationWrapper {

    private final Object applicationInstance;
    private static final LazyLoaded<Class<?>> applicationClass = new LazyLoaded<Class<?>>(new LazyLoaded.Loader<Class<?>>() {
        @Override
        public Class<?> load() throws ClassNotFoundException {
            return Class.forName("com.apple.eawt.Application");
        }
    });
    private static final LazyLoaded<Method> getApplicationMethod = new LazyLoaded<Method>(new LazyLoaded.Loader<Method>() {
        @Override
        public Method load() throws NoSuchMethodException {
            return applicationClass.get().getMethod("getApplication");
        }
    });

    private static final LazyLoaded<Method> setDockIconImageMethod = new LazyLoaded<Method>(new LazyLoaded.Loader<Method>() {
        @Override
        public Method load() throws Throwable {
            return applicationClass.get().getMethod("setDockIconImage", java.awt.Image.class);
        }
    });
    private static final LazyLoaded<Method> setEnabledAboutMenuMethod = new LazyLoaded<Method>(new LazyLoaded.Loader<Method>() {
        @Override
        public Method load() throws Throwable {
            return applicationClass.get().getMethod("setEnabledAboutMenu", boolean.class);
        }
    });

    private MacApplicationWrapper(Object applicationInstance) {

        this.applicationInstance = applicationInstance;
    }

    public static MacApplicationWrapper getApplication() throws CommonException {
        try {
            return new MacApplicationWrapper(getApplicationMethod.get().invoke(null));
        } catch (IllegalAccessException e) {
            throw new CommonException("Cannot invoke com.apple.eawt.Application.getApplication: " + e.getMessage());
        } catch (InvocationTargetException e) {
            throw new CommonException("Cannot invoke com.apple.eawt.Application.getApplication: " + e.getMessage());
        }
    }

    public void setDockIconImage(Image icon) throws CommonException {
        if (null == icon) {
            throw new CommonException("Icon null");
        }
        try {
            setDockIconImageMethod.get().invoke(applicationInstance, icon);
        } catch (IllegalAccessException e) {
            throw new CommonException("Cannot invoke com.apple.eawt.Application.setDockIconImage: " + e.getMessage());
        } catch (InvocationTargetException e) {
            throw new CommonException("Cannot invoke com.apple.eawt.Application.setDockIconImage: " + e.getMessage());
        }
    }

    public void setEnabledAboutMenu(boolean enable) throws CommonException {
        try {
            setEnabledAboutMenuMethod.get().invoke(applicationInstance, enable);
        } catch (IllegalAccessException e) {
            throw new CommonException("Cannot invoke com.apple.eawt.Application.setEnabledAboutMenu: " + e.getMessage());
        } catch (InvocationTargetException e) {
            throw new CommonException("Cannot invoke com.apple.eawt.Application.setEnabledAboutMenu: " + e.getMessage());
        }
    }
}
