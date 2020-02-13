/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.http;

/**
 * Hold a global {@code ServerDateHolder} instance for easy access
 */
public class GlobalServerDateHolder {

	/**
	 * global instance
	 */
	public static final ServerDateHolder INSTANCE;
	private static final ServerDateUpdaterTask UPDATER;
	static {
		INSTANCE = new ServerDateHolder();
		UPDATER = new ServerDateUpdaterTask(INSTANCE);//use default scheduler
		UPDATER.start();
	}
}
