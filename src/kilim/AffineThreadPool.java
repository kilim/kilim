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
	private AtomicInteger currentIndex_ = new AtomicInteger(0);	
	private List<BlockingQueue<Runnable>> queues_ = new ArrayList<BlockingQueue<Runnable>>();
	private List<ExecutorService> executorService_ = new ArrayList<ExecutorService>();
	
	public AffineThreadPool(int nThreads, String name)
	{
		this(nThreads, MAX_QUEUE_SIZE, name);
	}
	
	public AffineThreadPool(int nThreads, int queueSize, String name)
	{	
		nThreads_ = nThreads;				
		for (int i = 0; i < nThreads; ++i)
		{			
			String threadName = name + colon_ + i;
			BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(queueSize);
			queues_.add(queue);
			
			ExecutorService executorService = new KilimThreadPoolExecutor(1, queue, new ThreadFactoryImpl(threadName));			
			executorService_.add(executorService);						
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
		ExecutorService executorService = executorService_.get(index);
		executorService.submit(task);
		return index;
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
	KilimThreadPoolExecutor(int nThreads, BlockingQueue<Runnable> queue, ThreadFactory tFactory)
	{
		super(nThreads, nThreads, Integer.MAX_VALUE, TimeUnit.MILLISECONDS, queue, tFactory);
	}
	
	protected void afterExecute(Runnable r, Throwable th)
	{
		super.afterExecute(r, th);		
	}
}