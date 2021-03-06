/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.server.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.jboss.tools.rsp.server.persistence.DataLocationCore;

public class DataLocationSysProp {

	private String backup = null;
	
	public DataLocationSysProp backup() {
		backup = System.getProperty(DataLocationCore.SYSPROP_DATA_LOCATION);
		return this;
	}

	public DataLocationSysProp set(String tempDirname) {
		try {
			return set(Files.createTempDirectory(tempDirname).toFile());
		} catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
	
	}

	public DataLocationSysProp set(File file) {
		System.setProperty(DataLocationCore.SYSPROP_DATA_LOCATION, file.getAbsolutePath());
		return this;
	}

	public void restore() {
		if (backup == null) {
			System.clearProperty(DataLocationCore.SYSPROP_DATA_LOCATION);
		} else {
			System.setProperty(DataLocationCore.SYSPROP_DATA_LOCATION, backup);
		}
	}
}
