/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim;

import java.util.concurrent.atomic.AtomicBoolean;

import kilim.nio.NioSelectorScheduler.RegistrationTask;
import kilim.timerservice.Timer;
import kilim.timerservice.TimerService;

/**
 * This is a basic FIFO Executor. It maintains a list of runnable tasks and hands them out to WorkerThreads. Note
 * that we don't maintain a list of all tasks, but we will at some point when we introduce monitoring/watchdog
 * services. Paused tasks are not GC'd because their PauseReasons ought to be registered with some other live
 * object.
 *
 */
public class Scheduler {
    private static final int defaultQueueSize_ = Integer.MAX_VALUE;
    public static volatile Scheduler defaultScheduler = null;
    public static int defaultNumberThreads;
    private static final ThreadLocal<Task> taskMgr_ = new ThreadLocal<Task>();

    private int numThreads;
    private AffineThreadPool affinePool_;
    protected AtomicBoolean shutdown = new AtomicBoolean(false);
    
    /** print exceptions to standard out, default:true */
    public boolean enableExceptionLog = true;

    // Added for new Timer service
    private TimerService timerService;

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

    protected Scheduler() {
    }

    /**
     * create the scheduler with a default queue size
     * @param numThreads the number of threads to use, or use the default if less than zero 
     */
    public Scheduler(int numThreads) {
        this(numThreads,defaultQueueSize_);
    }

    /**
     * create the scheduler
     * @param numThreads the number of threads to use, or use the default if less than zero 
     * @param queueSize the queue size to use
     */
    public Scheduler(int numThreads,int queueSize) {
        if (numThreads < 0)
            numThreads = defaultNumberThreads;
        timerService = new TimerService();
        affinePool_ = new AffineThreadPool(numThreads,queueSize,timerService);
        this.numThreads = numThreads;
    }

    public boolean isEmptyish() {
        return affinePool_.isEmptyish();
    }

    public int numThreads() { return numThreads; }
        
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
    public void schedule(int index,Task t) {
        if (t instanceof RegistrationTask)
            assert (false);
        else
            affinePool_.publish(index,t);
    }

    public void scheduleTimer(Timer t) {
        timerService.submit(t);
    }

    /**
     * block the thread till a moment at which all scheduled tasks have completed and then shutdown the scheduler
     * does not prevent scheduling new tasks (from other threads) until the shutdown is complete so such a task
     * could be partially executed
     */
    public void idledown() {
        if (affinePool_!=null&&affinePool_.waitIdle(timerService,100))
            shutdown();
    }

    public void shutdown() {
        shutdown.set(true);
        if (defaultScheduler==this)
            defaultScheduler = null;
        if (affinePool_!=null) affinePool_.shutdown();
        timerService.shutdown();
    }

    public boolean isShutdown() {
        return shutdown.get();
    }

    /** a static accessor to allow log to be protected */
    static protected void logRelay(Scheduler sched,Object obj) { sched.log(obj); }
    
    /**
     * write to the log
     * @param obj the obj to log
     */
    protected void log(Object obj) {
        if (!enableExceptionLog)
            return;
        if (obj instanceof Throwable)
            ((Throwable) obj).printStackTrace();
        else
            System.out.println(obj);
    }    

    public synchronized static Scheduler getDefaultScheduler() {
        if (defaultScheduler==null)
            defaultScheduler = new Scheduler(defaultNumberThreads);
        return defaultScheduler;
    }

    public static void setDefaultScheduler(Scheduler s) {
        defaultScheduler = s;
    }

}



