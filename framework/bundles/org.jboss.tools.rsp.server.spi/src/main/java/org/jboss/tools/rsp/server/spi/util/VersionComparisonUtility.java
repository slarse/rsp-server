/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/

package org.jboss.tools.rsp.server.spi.util;

public class VersionComparisonUtility {
	private VersionComparisonUtility() {
		
	}
	
	public static boolean isJavaCompatible(String vmiVersion, String min, String max) {
		return isGreaterThanOrEqualTo(vmiVersion, min) && 
				isLessThanOrEqualTo(vmiVersion, max);
	}
	
	public static boolean isGreaterThanOrEqualTo(String vmi, String test) {
		if( test == null )
			return true;
		String[] splitVmi = vmi.split("\\.");
		String[] splitTest = test.split("\\.");
		int vmiMajor = Integer.parseInt(splitVmi[0]);
		int testMajor = Integer.parseInt(splitTest[0]);
		if( vmiMajor < testMajor ) return false;
		if( vmiMajor > testMajor ) return true;
		
		// Majors are equal. 
		if( splitTest.length <= 1 || splitTest[1].isEmpty())
			return true;
		int vmiMinor = Integer.parseInt(splitVmi[1]);
		int testMinor = Integer.parseInt(splitTest[1]);
		if( vmiMinor < testMinor ) return false;
		return true;
	}

	public static boolean isLessThanOrEqualTo(String vmi, String test) {
		if( test == null )
			return true;

		String[] splitVmi = vmi.split("\\.");
		String[] splitTest = test.split("\\.");
		int vmiMajor = Integer.parseInt(splitVmi[0]);
		int testMajor = Integer.parseInt(splitTest[0]);
		if( vmiMajor > testMajor ) return false;
		if( vmiMajor < testMajor ) return true;
		
		if( splitTest.length <= 1 || splitTest[1].isEmpty())
			return true;

		// Majors are equal. 
		int vmiMinor = Integer.parseInt(splitVmi[1]);
		int testMinor = Integer.parseInt(splitTest[1]);
		if( vmiMinor > testMinor ) return false;
		return true;
	}
}
