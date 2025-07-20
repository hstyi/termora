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

import com.glavsoft.utils.Strings;
import com.glavsoft.viewer.settings.ConnectionParams;
import com.glavsoft.viewer.swing.CancelConnectionException;
import com.glavsoft.viewer.swing.CancelConnectionQuietlyException;
import com.glavsoft.viewer.swing.ConnectionErrorException;
import com.glavsoft.viewer.swing.gui.RequestSomethingDialog;

import javax.swing.JFrame;
import java.awt.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class SshConnectionManager {

    static final String SSH_NODE = "com/glavsoft/viewer/ssh";
    static final String KNOWN_HOSTS = "known_hosts"; // both the key of property node and the name of known hosts file as in openssh agreement
    private static final String[] PRIV_KEY_FILE_NAMES = new String[]{"id_rsa", "id_dsa", "identity" };
    static final String OPENSSH_CONFIG_DIR_NAME = System.getProperty("user.home") + File.separator + ".ssh";
    private static final String SSH_CONNECTION_MANAGER_IMPLEMENTATION_CLASS_NAME = "com.glavsoft.viewer.swing.ssh.TrileadSsh2ConnectionManager";
    private static final String SSH_LIB_SOME_CLASS_NAME_FOR_CHECKING = "com.trilead.ssh2.Connection";
    String errorMessage = "";
	Component parent;
    protected Logger logger;

    SshConnectionManager(Component parent) {
        this.parent = parent;
    }

    public static SshConnectionManager createManager(Component parentWindow) throws ConnectionErrorException {
//        return new TrileadSsh2ConnectionManager(parent);
        Throwable ex;
        try {
            @SuppressWarnings("unchecked")
            final Class<SshConnectionManager> managerClass =
                    (Class<SshConnectionManager>) Class.forName(SSH_CONNECTION_MANAGER_IMPLEMENTATION_CLASS_NAME);
            final Constructor<SshConnectionManager> constructor = managerClass.getConstructor(JFrame.class);
            return constructor.newInstance(parentWindow);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | InvocationTargetException | IllegalAccessException e) {
            ex = e;
        }
        Logger.getLogger(SshConnectionManager.class.getName())
            .log(Level.WARNING, "Could not instantiate SshConnectionManager, internal error : " + ex.getMessage(), ex);
        throw new ConnectionErrorException("Could not create SSH tunnel: internal error.");
    }

    public int connect(ConnectionParams connectionParams) throws CancelConnectionException, ConnectionErrorException {
        if (Strings.isTrimmedEmpty(connectionParams.sshUserName)) {
            RequestSomethingDialog dialog = new RequestSomethingDialog(parent,
                    "SSH User Name", false, "Please enter the user name for SSH connection:");
            if (dialog.askResult()) {
                connectionParams.sshUserName = dialog.getResult();
            } else {
                throw new CancelConnectionQuietlyException("Login interrupted by user");
            }
            if (Strings.isTrimmedEmpty(connectionParams.sshUserName)) {
                throw new CancelConnectionException("No Ssh User Name entered");
            }
        }
        initSshEngine();
        addIdentityFiles();
        return makeConnectionAndGetPort(connectionParams);
	}

    protected abstract void initSshEngine();

    protected abstract int makeConnectionAndGetPort(ConnectionParams connectionParams) throws CancelConnectionException, ConnectionErrorException;

    boolean isKeyFileEncrypted(File keyFile) {
        FileReader fileReader = null;
        BufferedReader reader = null;
        try {
            fileReader = new FileReader(keyFile);
            reader = new BufferedReader(fileReader);
            String line = reader.readLine();
            if (line.startsWith("-----BEGIN ENCRYPTED PRIVATE KEY-----")) {//PKCS #8
                return true;
            }
            while ((line = reader.readLine()).indexOf(':') >= 0) {
                if (line.indexOf("ENCRYPTED") >0) {
                    return true; // Proc-Type: 4,ENCRYPTED
                }
            }
        } catch (IOException e) {
            logger.warning("Cannot read key file '" + keyFile.getName() + "': " + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignore) {
                    // nop
                }
            }
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException ignore) {
                    // nop
                }
            }
        }
        return false;
    }

    private void addIdentityFiles() {
        for (String fileName : PRIV_KEY_FILE_NAMES) {
            File keyFile = new File(OPENSSH_CONFIG_DIR_NAME, fileName);
            if (keyFile.exists() && keyFile.isFile()) {
                addIdentityFile(keyFile);
            }
        }
    }

    protected abstract void addIdentityFile(File keyFile);

    public String getErrorMessage() {
		return errorMessage;
	}

    public static boolean checkForSshSupport() {
        try {
            Class.forName(SSH_CONNECTION_MANAGER_IMPLEMENTATION_CLASS_NAME);
            Class.forName(SSH_LIB_SOME_CLASS_NAME_FOR_CHECKING);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    public abstract boolean isConnected();

    public abstract void closeConnection();
}