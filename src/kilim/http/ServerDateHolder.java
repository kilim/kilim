/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.http;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Simply holds a date byte array
 */
public class ServerDateHolder {

	private final SimpleDateFormat df;
	private static final Charset UTF8 = Charset.forName("utf-8");

	private volatile byte[] dateBytes;
	
	public ServerDateHolder() {
		df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("GMT:00"));
		update();//initialize dateBytes
	}
	
	/**
	 * @return the cached date byte array. DO NOT modify array data!
	 */
	public byte[] get() {
		return dateBytes;
	}
	
	/**
	 * update cached date array. DO NOT invoke this method concurrently!
	 */
	final void update() {
		this.dateBytes = df.format(new Date()).getBytes(UTF8);
	}
}
