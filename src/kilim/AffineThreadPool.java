// Copyright 2014 by Avinash Lakshman (hedviginc.com)

package kilim;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import kilim.timerservice.TimerService;

public class AffineThreadPool {
    private int nThreads_;
    private AtomicInteger currentIndex_ = new AtomicInteger(0);
    private List<BlockingQueue<Runnable>> queues_ = new ArrayList<BlockingQueue<Runnable>>();
    private List<KilimThreadPoolExecutor> executorService_ = new ArrayList<KilimThreadPoolExecutor>();
    private AtomicInteger count = new AtomicInteger();


    public AffineThreadPool(int nThreads,int queueSize,TimerService timerservice) {
        nThreads_ = nThreads;

        for (int i = 0; i<nThreads; ++i) {
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(queueSize);
            queues_.add(queue);
            KilimThreadPoolExecutor executorService = new KilimThreadPoolExecutor(queue,timerservice);
            executorService_.add(executorService);

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
        return index;
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


    class KilimThreadPoolExecutor extends ThreadPoolExecutor {
        private TimerService timerService;

        private AtomicInteger count() { return count; }

        KilimThreadPoolExecutor(BlockingQueue<Runnable> queue,TimerService timerService) {
            super(1,1,Integer.MAX_VALUE,TimeUnit.MILLISECONDS,queue);
            this.timerService = timerService;
        }

        protected void afterExecute(Runnable r,Throwable th) {
            super.afterExecute(r,th);
            timerService.trigger(this);
            count.decrementAndGet();
        }
    }
}
