/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim;

import java.util.concurrent.atomic.AtomicBoolean;

import kilim.nio.NioSelectorScheduler.RegistrationTask;
import kilim.timerservice.Timer;

/**
 * This is a basic FIFO Executor. It maintains a list of runnable tasks and hands them out to WorkerThreads. Note
 * that we don't maintain a list of all tasks, but we will at some point when we introduce monitoring/watchdog
 * services. Paused tasks are not GC'd because their PauseReasons ought to be registered with some other live
 * object.
 *
 */
public abstract class Scheduler {
    public static volatile Scheduler defaultScheduler = null;
    public static volatile Scheduler pinnableScheduler = null;
    public static int defaultNumberThreads;
    private static final ThreadLocal<Task> taskMgr_ = new ThreadLocal<Task>();
    public static Logger defaultLogger = new BasicLogger();

    protected AtomicBoolean shutdown = new AtomicBoolean(false);
    
    private Logger logger = defaultLogger;

    static {
        String s = System.getProperty("kilim.Scheduler.numThreads");
        if (s!=null)
            try {
                defaultNumberThreads = Integer.parseInt(s);
            } catch (Exception e) {
            }
        if (defaultNumberThreads==0)
            defaultNumberThreads = Runtime.getRuntime().availableProcessors();
    }

    protected static Task getCurrentTask() {
        return taskMgr_.get();
    }

    protected static void setCurrentTask(Task t) {
        taskMgr_.set(t);
    }

    /**
     * return a new default Scheduler with default queue length
     * @param numThreads the number of threads to use, or use the default if less than one
     * @return the new Scheduler
     */
    public static Scheduler make(int numThreads) { return new AffineScheduler(numThreads,0); }
    
    /**
     * are the queues empty allows false positives, but not false negatives ie, if this method returns false, then
     * at some moment during the call at least one queue was non-empty if it returns true then for each queue there
     * was a moment during the call when it was empty
     */
    public abstract boolean isEmptyish();

    public abstract int numThreads();
    
    public boolean isPinnable() { return true; }
        
    /**
     * Schedule a task to run.
     * It is the task's job to ensure that it is not scheduled when it is runnable.
     * the default index for assignment to an executor
     */
    public void schedule(Task t) {
        if (t instanceof RegistrationTask)
            ((RegistrationTask) t).wake();
        else
            schedule(-1,t);
    }

    /**
     * schedule a task to run
     * @param index the index of the executor to use, or less than zero to use the default (round robin) assignment
     * @param t the task
     */
    public abstract void schedule(int index,Task t);

    public abstract void scheduleTimer(Timer t);

    /**
     * block the thread till a moment at which all scheduled tasks have completed and then shutdown the scheduler
     * does not prevent scheduling new tasks (from other threads) until the shutdown is complete so such a task
     * could be partially executed
     */
    public abstract void idledown();

    public void shutdown() {
        shutdown.set(true);
        if (defaultScheduler==this)
            defaultScheduler = null;
    }

    public boolean isShutdown() {
        return shutdown.get();
    }

    /** a static accessor to allow log to be protected */
    static protected void logRelay(Scheduler sched,Object src,Object obj) { sched.log(src,obj); }
    
    public static interface Logger {
        public void log(Object source,Object problem);
    }
    static class BasicLogger implements Logger {
        public void log(Object source,Object obj) {
            if (obj instanceof Throwable)
                ((Throwable) obj).printStackTrace();
            else
                System.out.println(obj);
        }
    }
    
    /**
     * write to the log
     * @param src the source of the log object
     * @param obj the object to log
     */
    protected void log(Object src,Object obj) {
        if (logger != null)
            logger.log(src,obj);
    }
    
    /**
     * set a logger
     * @param logger the logger
     */
    public void setLogger(Logger logger) { this.logger = logger; }

    /** get and possibly instantiate a default scheduler */
    public synchronized static Scheduler getDefaultScheduler() {
        if (defaultScheduler==null)
            defaultScheduler = Scheduler.make(defaultNumberThreads);
        return defaultScheduler;
    }
    /** get and possibly instantiate a scheduler that is pinnable */
    public synchronized static Scheduler getDefaultPinnable() {
        if (pinnableScheduler==null) {
            if (defaultScheduler==null || defaultScheduler.isPinnable())
                pinnableScheduler = getDefaultScheduler();
            else
                pinnableScheduler = make(defaultNumberThreads);
        }
        return pinnableScheduler;
    }

    protected Scheduler getPinnable() {
        return isPinnable() ? this : getDefaultPinnable();
    }
    
    public synchronized static void setDefaultScheduler(Scheduler s) {
        defaultScheduler = s;
    }
    public synchronized static void setDefaultPinnable(Scheduler s) {
        pinnableScheduler = s;
    }

}



