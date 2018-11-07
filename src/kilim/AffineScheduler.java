// copyright 2016 nqzero - offered under the terms of the MIT License

package kilim;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kilim.timerservice.Timer;

import kilim.timerservice.TimerService;
import kilim.timerservice.TimerService.WatchdogContext;
import kilim.timerservice.TimerService.WatchdogTask;

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
public class AffineScheduler extends Scheduler implements ThreadFactory {
    protected Executor [] exes;
    protected AtomicInteger index = new AtomicInteger(-1);
    protected AtomicInteger count = new AtomicInteger(0);
    protected TimerService timerService;
    

    protected AffineScheduler() {}
    
    /**
     * create the scheduler
     * @param numThreads the number of threads to use, or use the default if less than one
     * @param queueSize the queue size to use, or use the default if less than one
     */
    public AffineScheduler(int numThreads,int queueSize) {
        if (numThreads <= 0)
            numThreads = defaultNumberThreads;
        if (queueSize <= 0)
            queueSize = Integer.MAX_VALUE;
        exes = new Executor[numThreads];
        for (int ii=0; ii < numThreads; ii++)
            exes[ii] = new Executor(new LinkedBlockingQueue(queueSize));
        timerService = new TimerService(exes[0]);
    }

    public void schedule(int index,Task t) {
        publish(index,t);
    }
    public void idledown() {
        waitIdle(100);
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
    protected int next() {
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

    public void scheduleTimer(Timer t) {
        timerService.submit(t);
    }

    public void shutdown() {
        super.shutdown();
        for (int ii=0; ii < exes.length; ii++)
            exes[ii].shutdown();
        timerService.shutdown();
    }

    public int numThreads() { return exes.length; }


    /*
        
     wait till there are no pending timers
     no running tasks
     no tasks waiting to be run
        
     */
    public boolean waitIdle(int delay) {
        while (!Thread.interrupted()) {
            if (resolved(timerService))
                return true;
            try { Thread.sleep(delay); } catch (InterruptedException ex) { break; }
        }
        return false;
    }

    protected boolean resolved(TimerService ts) {
        if (count.get() > 0) return false;
        return ts.isEmptyLazy(exes[0]);
    }

    
    public boolean isEmptyish() {
        for (Executor exe : exes)
            if (!exe.que.isEmpty()) return false;
        return true;
    }

    public Thread newThread(Runnable r) {
        return TimerService.factory.newThread(r);
    }

    protected class Executor extends ThreadPoolExecutor implements WatchdogContext {
        protected LinkedBlockingQueue<Task> que;
        protected AtomicInteger pending = new AtomicInteger();
        
        protected void publish(Task task) {
            pending.incrementAndGet();
            submit(task);
        }
        
        public Executor(LinkedBlockingQueue que) {
            super(1,1,Integer.MAX_VALUE,TimeUnit.DAYS,que,AffineScheduler.this);
            this.que = que;
        }

        protected void afterExecute(Runnable r,Throwable t) {
            pending.decrementAndGet();
            timerService.trigger(this);
            count.decrementAndGet();
        }

        public boolean isEmpty() {
            return count.get()==0;
        }

        public boolean isEmptyish() {
            return AffineScheduler.this.isEmptyish();
        }

        public void publish(WatchdogTask dog) {
            count.incrementAndGet();
            // warning - adding directly to the queue is "strongly discouraged"
            // original motivation for this technique was to bypass the TPE wrapping of the task
            // not sure if this is still a consideration
            // fixme:verify - is it possible that executor has not yet started ???
            getQueue().add(dog);
        }
    }

}


