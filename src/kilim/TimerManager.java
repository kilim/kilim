package kilim;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TimerManager
{
	private static class ThreadFactoryImpl implements ThreadFactory
	{
	    protected String id_;
	    protected ThreadGroup threadGroup_;
	    protected final AtomicInteger threadNbr_ = new AtomicInteger(1);
	    
	    public ThreadFactoryImpl(String id)
	    {
	        SecurityManager sm = System.getSecurityManager();
	        threadGroup_ = ( sm != null ) ? sm.getThreadGroup() : Thread.currentThread().getThreadGroup();
	        id_ = id;
	    }    
	    
	    public Thread newThread(Runnable runnable)
	    {        
	        String name = id_ + ":" + threadNbr_.getAndIncrement();       
	        Thread thread = new Thread(threadGroup_, runnable, name);        
	        return thread;
	    }
	}
	
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
