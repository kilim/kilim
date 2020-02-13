/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.http;

import kilim.Pausable;
import kilim.Scheduler;
import kilim.Task;

/**
 * A kilim task that updates server date per second.
 */
public class ServerDateUpdaterTask extends Task<Void> {
	
	private static final long SLEEP_MILLIS = 1000L;
	private final ServerDateHolder dateHolder;
	private volatile boolean aborted = false;
	
	/**
	 * construct a {@code ServerDateUpdaterTask} instance with default scheduler
	 * @param dateHolder
	 */
	public ServerDateUpdaterTask(ServerDateHolder dateHolder) {
		this(null, dateHolder);
	}

	public ServerDateUpdaterTask(Scheduler scheduler, ServerDateHolder dateHolder) {
		if (dateHolder == null) {
			throw new NullPointerException("dateHolder is null!");
		}
		this.dateHolder = dateHolder;
		setScheduler(scheduler);
	}

	@Override
	public void execute() throws Pausable, Exception {
		while (!aborted) {
			dateHolder.update();
			Task.sleep(SLEEP_MILLIS);
		}
	}
	
	public void abort() {
		aborted = true;
	}
}
