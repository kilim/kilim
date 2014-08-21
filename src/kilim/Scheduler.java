/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import kilim.queuehelper.TaskQueue;
import kilim.timerhelper.Timer;

/**
 * This is a basic FIFO Executor. It maintains a list of runnable tasks and
 * hands them out to WorkerThreads. Note that we don't maintain a list of all
 * tasks, but we will at some point when we introduce monitoring/watchdog
 * services. Paused tasks are not GC'd because their PauseReasons ought to be
 * registered with some other live object.
 * 
 */
public class Scheduler
{
	private static final String defaultName_ = "KilimWorker";
	private static final int defaultQueueSize_ = 65536;
	public static volatile Scheduler defaultScheduler = null;
	public static int defaultNumberThreads;
	private static final String dash_ = "-";
	private static final ThreadLocal<Task> taskMgr_ = new ThreadLocal<Task>();
	private static ConcurrentMap<String, AtomicInteger> nameGenerator_ = new ConcurrentHashMap<String, AtomicInteger>();

	private String name_;
	private AffineThreadPool affinePool_;
	protected AtomicBoolean shutdown = new AtomicBoolean(false);
	//public final kilim.queuehelper.TaskQueue taskQueue = new kilim.queuehelper.TaskQueue(); 
	//public final PriorityBlockingQueue<Timer> taskQueue = new PriorityBlockingQueue<Timer>();
	public final MailboxMPSC<Timer> producertaskQueue = new MailboxMPSC<Timer>(1000*1000);
	public final TaskQueue taskQueue = new TaskQueue();
	static
	{
		String s = System.getProperty("kilim.Scheduler.numThreads");
		if (s != null)
		{
			try
			{
				defaultNumberThreads = Integer.parseInt(s);
			}
			catch (Exception e)
			{
			}
		}
		if (defaultNumberThreads == 0)
		{
			defaultNumberThreads = Runtime.getRuntime().availableProcessors();
		}
	}
	
	protected static Task getCurrentTask()
	{
		return taskMgr_.get();
	}
	
	protected static void setCurrentTask(Task t)
	{
		taskMgr_.set(t);
	}

	protected Scheduler()
	{
	}

	public Scheduler(int numThreads)
	{
		this(numThreads, defaultQueueSize_, defaultName_);
	}

	public Scheduler(int numThreads, int queueSize, String name)
	{
		name_ = name;
		nameGenerator_.putIfAbsent(name_, new AtomicInteger());
		affinePool_ = new AffineThreadPool(numThreads, queueSize, name,taskQueue,producertaskQueue);
	}
	
	public long getTaskCount()
	{
		return affinePool_.getTaskCount();
	}

	protected String getName()
	{
		return name_;
	}

	protected String getNextName()
	{
		AtomicInteger counter = nameGenerator_.get(name_);
		return name_ + dash_ + counter.incrementAndGet();
	}

	/**
	 * Schedule a task to run. It is the task's job to ensure that it is not
	 * scheduled when it is runnable.
	 */
	public void schedule(Task t)
	{
		affinePool_.publish(t);
	}

	public void schedule(int index, Task t)
	{		
		affinePool_.publish(index, t);
	}

	public void shutdown()
	{
		shutdown.set(true);
		if (defaultScheduler == this)
		{
			defaultScheduler = null;
		}
		affinePool_.shutdown();
	}

	public boolean isShutdown()
	{
		return shutdown.get();
	}
	
	public String getSchedulerStats()
	{
	    return affinePool_.getQueueStats();
	}

	public synchronized static Scheduler getDefaultScheduler()
	{
		if (defaultScheduler == null)
		{
			defaultScheduler = new Scheduler(defaultNumberThreads);
		}
		return defaultScheduler;
	}

	public static void setDefaultScheduler(Scheduler s)
	{
		defaultScheduler = s;
	}
}
