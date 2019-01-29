/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.rsp.server.wildfly.servertype.capabilities;

import org.jboss.tools.rsp.server.spi.servertype.IServer;

public class Wildfly150ExtendedProperties extends Wildfly130ExtendedProperties {

	public Wildfly150ExtendedProperties(IServer obj) {
		super(obj);
	}

	@Override
	public String getRuntimeTypeVersionString() {
		return "15.0"; //$NON-NLS-1$
	}
}