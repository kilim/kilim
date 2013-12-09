package kilim;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WorkerPool;

public class AffineThreadPool
{
	private static final int MAX_QUEUE_SIZE = 4096;
	private static final String colon_ = ":";
	
	private static class Pair<K, V>
	{
		K k_;
		V v_;
	}
	
	protected static int getCurrentThreadId()
	{
		String name = Thread.currentThread().getName();
		int sIndex = name.indexOf(colon_);
		int lIndex = name.lastIndexOf(colon_);
		return Integer.parseInt(name.substring(sIndex + 1, lIndex));
	}
		
	private int nThreads_;	
	private AtomicInteger currentIndex_ = new AtomicInteger(0);
	private AtomicInteger idGenerator_ = new AtomicInteger(0);
	private ConcurrentMap<Integer, Pair<ExecutorService, RingBuffer<KilimEvent>>> threadMap_ = new ConcurrentHashMap<Integer, Pair<ExecutorService, RingBuffer<KilimEvent>>>();
	
	public AffineThreadPool(int nThreads, String name, ExceptionHandler exHandler, Scheduler scheduler)
	{
		this(nThreads, MAX_QUEUE_SIZE, name, exHandler, scheduler);
	}
	
	public AffineThreadPool(int nThreads, int queueSize, String name, ExceptionHandler exHandler, Scheduler scheduler)
	{	
		nThreads_ = nThreads;
		queueSize = queueSize / nThreads;
		for (int i = 0; i < nThreads; ++i)
		{
			RingBuffer<KilimEvent> rBuffer = RingBuffer.createMultiProducer(KilimEvent.factory_, queueSize);
			String id = name + colon_ + idGenerator_.incrementAndGet();
	        ExecutorService executorService = Executors.newFixedThreadPool(1, new ThreadFactoryImpl(id));
	        
			KilimWorkHandler[] kWorkHandler = new KilimWorkHandler[1];
			kWorkHandler[0] = new KilimWorkHandler(idGenerator_.get(), scheduler);
			
			WorkerPool<KilimEvent> wPool = new WorkerPool<KilimEvent>(rBuffer, rBuffer.newBarrier(), exHandler, kWorkHandler);
	        rBuffer.addGatingSequences(wPool.getWorkerSequences());	   	        
	        wPool.start(executorService);
	        
	        Pair<ExecutorService, RingBuffer<KilimEvent>> pair = new Pair<ExecutorService, RingBuffer<KilimEvent>>();
	        pair.k_ = executorService;
	        pair.v_ = rBuffer;
	        threadMap_.putIfAbsent(idGenerator_.get(), pair);
		}
	}
	
	public int publish(Task task)
	{
		currentIndex_.compareAndSet(Integer.MAX_VALUE, 0);
		int index = currentIndex_.incrementAndGet() % nThreads_;
		index = (index == 0) ? (index + 1) : index;
		return publish(index, task);
	}
	
	public int publish(int index, Task task)
	{
		RingBuffer<KilimEvent> rBuffer = threadMap_.get(index).v_;
		long sequence = rBuffer.next();
		KilimEvent kEvent = rBuffer.get(sequence);
		kEvent.putTid(index);
		kEvent.putTask(task);
		rBuffer.publish(sequence);		
		return index;
	}
	
	public void shutdown()
	{
		Set<Map.Entry<Integer, Pair<ExecutorService, RingBuffer<KilimEvent>>>> entries = threadMap_.entrySet();
		for (Map.Entry<Integer, Pair<ExecutorService, RingBuffer<KilimEvent>>> entry : entries)
		{
			entry.getValue().k_.shutdown();			
		}
	}
	
	public static void main(String[] args) throws Throwable
	{
		AffineThreadPool aPool = new AffineThreadPool(8, "Avinash", null, null);
		aPool.publish(null);
	}
}
