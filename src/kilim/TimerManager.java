// Copyright 2014 nilangshah - offered under the terms of the MIT License

package kilim;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TimerManager
{	
	private static TimerManager instance_;
	private static Lock createLock_ = new ReentrantLock();
	
	public static TimerManager instance()
	{
		if (instance_ == null)
		{
			createLock_.lock();
			try
			{
				if (instance_ == null)
				{
					instance_ = new TimerManager();
				}
			}
			finally
			{
				createLock_.unlock();
			}
		}
		return instance_;
	}
	
	private ConcurrentMap<String, ScheduledExecutorService> timerMap_ = new ConcurrentHashMap<String, ScheduledExecutorService>();
	
	private TimerManager()
	{
		
	}
	
	public ScheduledExecutorService getTimer(String name)
	{
		ScheduledExecutorService scheduledExecutor = timerMap_.get(name);
		if (scheduledExecutor == null)
		{
			createLock_.lock();
			try
			{
				scheduledExecutor = timerMap_.get(name);
				if ( scheduledExecutor == null )
				{
					scheduledExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryImpl(name));
					timerMap_.put(name, scheduledExecutor);
				}
			}
			finally
			{
				createLock_.unlock();
			}
		}
		return scheduledExecutor;
	}
}
