// Copyright 2014 by Avinash Lakshman (hedviginc.com)

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

import kilim.timerservice.TimerService;

public class AffineThreadPool {
    private static final int MAX_QUEUE_SIZE = 4096;
    private static final String colon_ = ":";

    private int nThreads_;
    private String poolName_;
    private AtomicInteger currentIndex_ = new AtomicInteger(0);
    private List<BlockingQueue<Runnable>> queues_ = new ArrayList<BlockingQueue<Runnable>>();
    private List<KilimStats> queueStats_ = new ArrayList<KilimStats>();
    private List<KilimThreadPoolExecutor> executorService_ = new ArrayList<KilimThreadPoolExecutor>();
    private AtomicInteger count = new AtomicInteger();

    public AffineThreadPool(int nThreads,String name,TimerService timerService) {
        this(nThreads,MAX_QUEUE_SIZE,name,timerService);
    }

    public AffineThreadPool(int nThreads,int queueSize,String name,
            TimerService timerservice) {
        nThreads_ = nThreads;
        poolName_ = name;

        for (int i = 0; i<nThreads; ++i) {
            String threadName = name+colon_+i;
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(
                    queueSize);
            queues_.add(queue);

            KilimThreadPoolExecutor executorService = new KilimThreadPoolExecutor(
                    i,1,queue,new ThreadFactoryImpl(threadName),
                    timerservice);
            executorService_.add(executorService);

            queueStats_.add(new KilimStats(12,"num"));
        }
        timerservice.defaultExec = executorService_.get(0);
    }

    /**
     * are the queues empty allows false positives, but not false negatives ie, if this method returns false, then
     * at some moment during the call at least one queue was non-empty if it returns true then for each queue there
     * was a moment during the call when it was empty
     */
    public boolean isEmptyish() {
        for (BlockingQueue<Runnable> queue : queues_)
            if (!queue.isEmpty()) return false;
        return true;
    }

    private int getNextIndex() {
        int value = 0, newValue = 0;
        do {
            value = currentIndex_.get();
            newValue = ((value!=Integer.MAX_VALUE) ? (value+1) : 0);
        } while (!currentIndex_.compareAndSet(value,newValue));
        return (newValue)%nThreads_;
    }

    public int publish(Task task) {
        int index = getNextIndex();
        task.setTid(index);
        return publish(index,task);
    }

    public int publish(int index,Task task) {
        KilimThreadPoolExecutor executorService = executorService_.get(index);
        count.incrementAndGet();
        executorService.submit(task);
        queueStats_.get(index).record(executorService.getQueueSize());
        return index;
    }

    public String getQueueStats() {
        String statsStr = "";
        for (int i = 0; i<queueStats_.size(); ++i)
            statsStr += queueStats_.get(i).dumpStatistics(
                    poolName_+":QUEUE-SZ-"+i);
        return statsStr;
    }

    /*
        
     wait till there are no pending timers
     no running tasks
     no tasks waiting to be run
        
     */
    public boolean waitIdle(TimerService ts,int delay) {
        while (!Thread.interrupted()) {
            if (resolved(ts))
                return true;
            try { Thread.sleep(delay); } catch (InterruptedException ex) { break; }
        }
        return false;
    }

    private boolean resolved(TimerService ts) {
        if (count.get()>0) return false;
        KilimThreadPoolExecutor exe = executorService_.get(0);
        return ts.isEmptyLazy(exe);
    }

    public void shutdown() {
        for (ExecutorService executorService : executorService_)
            executorService.shutdown();
    }

    public static void publish(ThreadPoolExecutor executor,Runnable payload) {
        KilimThreadPoolExecutor exe = (KilimThreadPoolExecutor) executor;
        exe.count().incrementAndGet();
        executor.getQueue().add(payload);
    }

    public static boolean isEmptyProxy(ThreadPoolExecutor executor) {
        KilimThreadPoolExecutor exe = (KilimThreadPoolExecutor) executor;
        return exe.count().get()==0;
    }

    static class ThreadFactoryImpl implements ThreadFactory
    {
        protected String id_;
        protected ThreadGroup threadGroup_;    

        public ThreadFactoryImpl(String id)
        {
            SecurityManager sm = System.getSecurityManager();
            threadGroup_ = ( sm != null ) ? sm.getThreadGroup() : Thread.currentThread().getThreadGroup();
            id_ = id;
        }    

        public Thread newThread(Runnable runnable)
        {                      
            Thread thread = new Thread(threadGroup_, runnable, id_);        
            return thread;
        }
    }

    class KilimThreadPoolExecutor extends ThreadPoolExecutor {
        int id = 0;
        private TimerService timerService;
        private BlockingQueue<Runnable> queue;

        private AtomicInteger count() {
            return count;
        }

        KilimThreadPoolExecutor(int id,int nThreads,
                BlockingQueue<Runnable> queue,ThreadFactory tFactory,
                TimerService timerService) {
            super(nThreads,nThreads,Integer.MAX_VALUE,TimeUnit.MILLISECONDS,
                    queue,tFactory);
            this.id = id;
            this.queue = queue;
            this.timerService = timerService;
        }

        protected void afterExecute(Runnable r,Throwable th) {
            super.afterExecute(r,th);
            timerService.trigger(this);
            count.decrementAndGet();

        }

        protected int getQueueSize() {
            return super.getQueue().size();
        }

    }
}
