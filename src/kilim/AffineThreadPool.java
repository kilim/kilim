// copyright 2016 nqzero - offered under the terms of the MIT License

package kilim;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AffineThreadPool {
    Executor [] exes;
    AtomicInteger index = new AtomicInteger(0);
    

    AffineThreadPool(int numThreads,int queueSize) {
        exes = new Executor[numThreads];
        for (int ii=0; ii < numThreads; ii++)
            exes[ii] = new Executor(new LinkedBlockingQueue(queueSize));
    }
    

    // fixme:denial-of-service
    // seems vulnerable to periodic cost in task scheduling
    // ie, if an expensive task occurs every numThreads,
    // then the same TPE would get the expensive task each time
    private int next() {
        index.compareAndSet(exes.length,0);
        return index.getAndIncrement();
    }
    
    void publish(Task task) {
        publish(next(),task);
    }

    void publish(int index,Task task) {
        exes[index].publish(task);
    }

    void shutdown() {
        for (int ii=0; ii < exes.length; ii++)
            exes[ii].shutdown();
    }
    

    static class Executor extends ThreadPoolExecutor {
        LinkedBlockingQueue<Task> que;
        AtomicInteger pending = new AtomicInteger();
        
        void publish(Task task) {
            pending.incrementAndGet();
            submit(task);
        }
        
        public Executor(LinkedBlockingQueue que) {
            super(1,1,Integer.MAX_VALUE,TimeUnit.DAYS,que);
            this.que = que;
        }

        protected void afterExecute(Runnable r,Throwable t) {
            pending.decrementAndGet();
        }
        
    }
}


