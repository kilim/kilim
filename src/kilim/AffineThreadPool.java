// copyright 2016 nqzero - offered under the terms of the MIT License

package kilim;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import kilim.timerservice.TimerService;

/*
    fixme:vestigial - release note for pre-2.0

    __Caveat Emptor__
    this is a from-scratch reimplementation of hedvig's ATP based on external linkage:
        Scheduler usage
        backed by ThreadPoolExecutor
    with the non-hedvig additions (nilang, nqzero) replayed on it
    it appears to be a drop-in-replacement for known usages:
        ant clean testjit test
        TimerBlast2
    however, a number of methods were removed that were not used internally
    so any external usages will now be broken
*/
public class AffineThreadPool {
    Executor [] exes;
    AtomicInteger index = new AtomicInteger(-1);
    private AtomicInteger count = new AtomicInteger(0);
    

    public AffineThreadPool(int numThreads,int queueSize,TimerService ts) {
        exes = new Executor[numThreads];
        for (int ii=0; ii < numThreads; ii++)
            exes[ii] = new Executor(new LinkedBlockingQueue(queueSize),ts);
        ts.defaultExec = exes[0];
    }


    // fixme:denial-of-service
    // seems vulnerable to periodic cost in task scheduling
    // ie, if an expensive task occurs every numThreads,
    // then the same TPE would get the expensive task each time
    //
    // fixme:context-switch
    // if the threads are not saturated, this round robbin approach still triggers each thread
    // seems more efficient to saturate 1 thread before triggering the others
    // ie, to prevent context switching
    private int next() {
        int value = 0, newValue = 0;
        do {
            value = index.get();
            newValue = (value==exes.length-1) ? 0:value+1;
        } while (!index.compareAndSet(value,newValue));
        return newValue;
    }
    
    void publish(int index,Task task) {
        if (index < 0)
            index = next();
        count.incrementAndGet();
        task.setTid(index);
        exes[index].publish(task);
    }

    public static void publish(ThreadPoolExecutor executor,Runnable payload) {
        Executor exe = (Executor) executor;
        exe.count().incrementAndGet();
        // warning - adding directly to the queue is "strongly discouraged"
        // original motivation for this technique was to bypass the TPE wrapping of the task
        // not sure if this is still a consideration
        // fixme:verify - is it possible that executor has not yet started ???
        executor.getQueue().add(payload);
    }

    void shutdown() {
        for (int ii=0; ii < exes.length; ii++)
            exes[ii].shutdown();
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
        if (count.get() > 0) return false;
        return ts.isEmptyLazy(exes[0]);
    }

    
    /**
     * are the queues empty allows false positives, but not false negatives ie, if this method returns false, then
     * at some moment during the call at least one queue was non-empty if it returns true then for each queue there
     * was a moment during the call when it was empty
     */
    public boolean isEmptyish() {
        for (Executor exe : exes)
            if (!exe.que.isEmpty()) return false;
        return true;
    }

    public static boolean isEmptyProxy(ThreadPoolExecutor executor) {
        Executor exe = (Executor) executor;
        return exe.count().get()==0;
    }

    class Executor extends ThreadPoolExecutor {
        LinkedBlockingQueue<Task> que;
        AtomicInteger pending = new AtomicInteger();
        private TimerService timerService;
        
        private AtomicInteger count() { return count; }

        void publish(Task task) {
            pending.incrementAndGet();
            submit(task);
        }
        
        public Executor(LinkedBlockingQueue que,TimerService ts) {
            super(1,1,Integer.MAX_VALUE,TimeUnit.DAYS,que);
            this.que = que;
            timerService = ts;
        }

        protected void afterExecute(Runnable r,Throwable t) {
            pending.decrementAndGet();
            timerService.trigger(this);
            count.decrementAndGet();
        }
    }

}


