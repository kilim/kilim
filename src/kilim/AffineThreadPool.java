package kilim;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import kilim.queuehelper.TaskQueue;
import kilim.timerhelper.Timer;

public class AffineThreadPool
{
	private static final int MAX_QUEUE_SIZE = 4096;
	private static final String colon_ = ":";

	protected static int getCurrentThreadId()
	{
		String name = Thread.currentThread().getName();
		int sIndex = name.indexOf(colon_);		
		return Integer.parseInt(name.substring(sIndex + 1, name.length()));
	}
		
	private int nThreads_;		
	private String poolName_;
	private AtomicInteger currentIndex_ = new AtomicInteger(0);	
	private List<BlockingQueue<Runnable>> queues_ = new ArrayList<BlockingQueue<Runnable>>();
	private List<KilimStats> queueStats_ = new ArrayList<KilimStats>();
	private List<KilimThreadPoolExecutor> executorService_ = new ArrayList<KilimThreadPoolExecutor>();
	
	public AffineThreadPool(int nThreads, String name, TaskQueue taskQueue)
	{
		this(nThreads, MAX_QUEUE_SIZE, name,taskQueue);
	}
	
	public AffineThreadPool(int nThreads, int queueSize, String name, TaskQueue taskQueue)
	{	
		nThreads_ = nThreads;
		poolName_ = name;
		for (int i = 0; i < nThreads; ++i)
		{			
			String threadName = name + colon_ + i;
			BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(queueSize);
			queues_.add(queue);
			
			KilimThreadPoolExecutor executorService = new KilimThreadPoolExecutor(1, queue, new ThreadFactoryImpl(threadName), taskQueue);			
			executorService_.add(executorService);		
			
			queueStats_.add(new KilimStats(12, "num"));
		}							
	}
	
	public long getTaskCount()
	{
		long totalRemainingCapacity = 0L;
		for (BlockingQueue<Runnable> queue : queues_)
		{
			totalRemainingCapacity += queue.size();
		}
		return totalRemainingCapacity;
	}
	
	private int getNextIndex()
	{
		currentIndex_.compareAndSet(Integer.MAX_VALUE, 0);
		int index = currentIndex_.getAndIncrement() % nThreads_;					
		return index;
	}
	
	public int publish(Task task)
	{		
		int index = getNextIndex();
		task.setTid(index);			
		return publish(index, task);
	}
	
	public int publish(int index, Task task)
	{				
	    KilimThreadPoolExecutor executorService = executorService_.get(index);
		executorService.submit(task);
		queueStats_.get(index).record(executorService.getQueueSize());
		return index;
	}
	public String getQueueStats()
	{
	    String statsStr = "";
	    for (int i = 0; i < queueStats_.size(); ++i)
	    {
	        statsStr += queueStats_.get(i).dumpStatistics(poolName_ + ":QUEUE-SZ-" + i);
	    }
	    return statsStr;
	}
    public void shutdown()
	{
		for (ExecutorService executorService : executorService_)
		{
			executorService.shutdown();
		}
	}
}

class KilimThreadPoolExecutor extends ThreadPoolExecutor
{
    TaskQueue taskQueue;
    
	KilimThreadPoolExecutor(int nThreads, BlockingQueue<Runnable> queue, ThreadFactory tFactory, TaskQueue taskQueue)
	{
		super(nThreads, nThreads, Integer.MAX_VALUE, TimeUnit.MILLISECONDS, queue, tFactory);
		this.taskQueue = taskQueue;
	}
	
	protected void afterExecute(Runnable r, Throwable th)
	{
		super.afterExecute(r, th);	
		boolean taskFired = false;
        Timer t = null;
        do {
            taskFired = false;
            synchronized (taskQueue) {

                if (!taskQueue.isEmpty()) {
                    t = taskQueue.peek();

                    if (t.state == Timer.CANCELLED) {
                        taskQueue.poll();
                        continue; // No action required, poll queue again
                    }
                    long currentTime = System.currentTimeMillis();
                    long executionTime = t.nextExecutionTime;
                    if (taskFired = (executionTime <= currentTime)) {
                        taskQueue.poll();
                        t.state = Timer.EXECUTED;
                    }
                }
            }
            if (taskFired) {
                t.es.onEvent(t.o, Cell.timedOut);
            }
        } while (taskFired);
	}
	protected int getQueueSize()
	{
	    return super.getQueue().size();
	}
}