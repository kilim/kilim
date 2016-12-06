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

public class AffineThreadPool {
    private int nThreads_;
    private AtomicInteger currentIndex_ = new AtomicInteger(0);
    private List<BlockingQueue<Runnable>> queues_ = new ArrayList<BlockingQueue<Runnable>>();
    private List<KilimThreadPoolExecutor> executorService_ = new ArrayList<KilimThreadPoolExecutor>();


    public AffineThreadPool(int nThreads,int queueSize) {
        nThreads_ = nThreads;

        for (int i = 0; i<nThreads; ++i) {
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(queueSize);
            queues_.add(queue);
            KilimThreadPoolExecutor executorService = new KilimThreadPoolExecutor(queue);
            executorService_.add(executorService);

        }
    }


    private int getNextIndex() {
            currentIndex_.compareAndSet(Integer.MAX_VALUE, 0);
            int index = currentIndex_.getAndIncrement() % nThreads_;                                        
            return index;
    }

    public int publish(Task task) {
        int index = getNextIndex();
        task.setTid(index);
        return publish(index,task);
    }

    public int publish(int index,Task task) {
        KilimThreadPoolExecutor executorService = executorService_.get(index);
        executorService.submit(task);
        return index;
    }


    public void shutdown() {
        for (ExecutorService executorService : executorService_)
            executorService.shutdown();
    }

    class KilimThreadPoolExecutor extends ThreadPoolExecutor {
        KilimThreadPoolExecutor(BlockingQueue<Runnable> queue) {
            super(1,1,Integer.MAX_VALUE,TimeUnit.MILLISECONDS,queue);
        }

        protected void afterExecute(Runnable r,Throwable th) {
            super.afterExecute(r,th);
        }
    }
}
