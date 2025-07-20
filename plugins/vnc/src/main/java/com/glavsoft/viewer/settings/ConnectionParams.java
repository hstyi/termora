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
package com.glavsoft.viewer.settings;

import com.glavsoft.utils.Strings;
import com.glavsoft.viewer.mvp.Model;

/**
 * Object that represents parameters needed for establishing network connection to remote host.
 * This is used to pass a number of parameters into connection establishing module and
 * to provide a Connection History interface feature.
 *
 * @author dime at tightvnc.com
 */
public class ConnectionParams implements Model {
	public static final int DEFAULT_SSH_PORT = 22;
	private static final int DEFAULT_RFB_PORT = 5900;

    /**
     * A name of remote host.
     * Rather symbolic (dns) name or ip address
     * Ex. remote.host.mydomain.com or localhost or 192.168.0.2 etc.
     */
	public String hostName;
    /**
     * A port number of remote host.
     * Default is 5900
     */
	private int portNumber;

	public String sshUserName;
	public String sshHostName;
	private int sshPortNumber;

	private boolean useSsh;

	public ConnectionParams(String hostName, int portNumber, boolean useSsh, String sshHostName, int sshPortNumber, String sshUserName) {
		this.hostName = hostName;
		this.portNumber = portNumber;
		this.sshUserName = sshUserName;
		this.sshHostName = sshHostName;
		this.sshPortNumber = sshPortNumber;
		this.useSsh = useSsh;
	}

	public ConnectionParams(ConnectionParams cp) {
		this.hostName = cp.hostName != null? cp.hostName: "";
		this.portNumber = cp.portNumber;
		this.sshUserName = cp.sshUserName;
		this.sshHostName = cp.sshHostName;
		this.sshPortNumber = cp.sshPortNumber;
		this.useSsh = cp.useSsh;
	}

	public ConnectionParams() {
		hostName = "";
        sshUserName = "";
        sshHostName = "";
	}

    public ConnectionParams(String hostName, String portNumber) {
        this.hostName = hostName;
        try {
            setPortNumber(portNumber);
        } catch (WrongParameterException ignore) {
            // use default
            this.portNumber = 0;
        }
    }

    /**
     * Check host name empty
     * @return true if host name is empty
     */
	public boolean isHostNameEmpty() {
		return Strings.isTrimmedEmpty(hostName);
	}

    /**
     * Parse port number from string specified.
     * Thrown WrongParameterException on error.
     *
     * @param port string representation of port number
     * @throws WrongParameterException when parsing error occurs or port number is out of range
     * @return portNubmer parsed
     */
	private int parsePortNumber(String port) throws WrongParameterException {
        int portNumber;
        if (null == port) return 0;
		try {
			portNumber = Integer.parseInt(port);
		} catch (NumberFormatException e) {
            portNumber = 0;
            if ( ! Strings.isTrimmedEmpty(port)) {
                throw new WrongParameterException("Wrong port number: " + port + "\nMust be in 0..65535");
            }
        }
        if (portNumber > 65535 || portNumber < 0) {
            throw new WrongParameterException("Port number is out of range: " + port + "\nMust be in 0..65535");
        }
        return  portNumber;
	}

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }
    public String getHostName() {
        return this.hostName;
    }

    /**
     * Parse port number from string specified.
     * Thrown WrongParameterException on error.
     * Set portNumber property when port on success.
     * throws WrongParameterException when parsing error occurs or port number is out of range
     *
     * @param port string representation of port number
     */
    public void setPortNumber(String port) throws WrongParameterException {
        portNumber = this.parsePortNumber(port);
    }

    public void setPortNumber(int port) {
        this.portNumber = port;
    }

    public int getPortNumber() {
        return 0 == portNumber ? DEFAULT_RFB_PORT : portNumber;
    }

    public void setSshPortNumber(String port) throws WrongParameterException {
        try {
            sshPortNumber = this.parsePortNumber(port);
        } catch (WrongParameterException e) {
            throw new WrongParameterException("SSH port number error. " + e.getMessage());
        }
    }

    public void setSshPortNumber(int port) {
        this.sshPortNumber = port;
    }

    public int getSshPortNumber() {
        return 0 == sshPortNumber ? DEFAULT_SSH_PORT: sshPortNumber;
    }

    /**
     * Set flag to use SSH port forwarding when connect to host
     * @param useSsh
     */
    public void setUseSsh(boolean useSsh) {
        this.useSsh = useSsh;
    }

    /**
     * Check to use SSH port forwarding when connect to host
     * @return true if user wants to use SSH
     */
    public boolean useSsh() {
        return useSsh && ! Strings.isTrimmedEmpty(sshHostName);
    }

    public boolean getUseSsh() {
        return this.useSsh();
    }

    public String getSshUserName() {
        return this.sshUserName;
    }

    public void setSshUserName(String sshUserName) {
        this.sshUserName = sshUserName;
    }

    public String getSshHostName() {
        return this.sshHostName;
    }

    public void setSshHostName(String sshHostName) {
        this.sshHostName = sshHostName;
    }

    /**
     * Copy and complete only field that are empty (null, zerro or empty string) in `this' object from the other one
     *
     * @param other ConnectionParams object to copy fields from
     */
    public void completeEmptyFieldsFrom(ConnectionParams other) {
        if (null == other) return;
		if (Strings.isTrimmedEmpty(hostName) && ! Strings.isTrimmedEmpty(other.hostName)) {
			hostName = other.hostName;
		}
        if ( 0 == portNumber && other.portNumber != 0) {
			portNumber = other.portNumber;
		}
		if (Strings.isTrimmedEmpty(sshUserName) && ! Strings.isTrimmedEmpty(other.sshUserName)) {
			sshUserName = other.sshUserName;
		}
		if (Strings.isTrimmedEmpty(sshHostName) && ! Strings.isTrimmedEmpty(other.sshHostName)) {
			sshHostName = other.sshHostName;
		}
		if ( 0 == sshPortNumber && other.sshPortNumber != 0) {
			sshPortNumber = other.sshPortNumber;
		}
		useSsh |= other.useSsh;
	}

	@Override
	public String toString() {
		return hostName != null ? hostName : "";
//        return (hostName != null ? hostName : "") + ":" + portNumber + " " + useSsh + " " + sshUserName + "@" + sshHostName + ":" + sshPortNumber;
    }

    /**
     * For logging purpose
     *
     * @return string representation of object
     */
    public String toPrint() {
        return "ConnectionParams{" +
                "hostName='" + hostName + '\'' +
                ", portNumber=" + portNumber +
                ", sshUserName='" + sshUserName + '\'' +
                ", sshHostName='" + sshHostName + '\'' +
                ", sshPortNumber=" + sshPortNumber +
                ", useSsh=" + useSsh +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (null == obj || ! (obj instanceof ConnectionParams)) return false;
        if (this == obj) return true;
        ConnectionParams o = (ConnectionParams) obj;
        return isEqualsNullable(hostName, o.hostName) && getPortNumber() == o.getPortNumber() &&
                useSsh == o.useSsh && isEqualsNullable(sshHostName, o.sshHostName) &&
                getSshPortNumber() == o.getSshPortNumber() && isEqualsNullable(sshUserName, o.sshUserName);
    }

    private boolean isEqualsNullable(String one, String another) {
        return (null == one? "" : one).equals(null == another? "" : another);
    }

    @Override
    public int hashCode() {
        long hash = (hostName != null? hostName.hashCode() : 0) +
                portNumber * 17 +
                (useSsh ? 781 : 693) +
                (sshHostName != null? sshHostName.hashCode() : 0) * 23 +
                (sshUserName != null? sshUserName.hashCode() : 0) * 37 +
                sshPortNumber * 41;
        return (int)hash;
    }

    public void clearFields() {
        hostName = "";
        portNumber = 0;
        useSsh = false;
        sshHostName = null;
        sshUserName = null;
        sshPortNumber = 0;
    }
}
