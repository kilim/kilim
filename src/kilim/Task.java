/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.util.LinkedList;
import java.util.TimerTask;
import java.util.Timer;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A base class for tasks. A task is a lightweight thread (it contains its 
 * own stack in the form of a fiber). A concrete subclass of Task must
 * provide a pausable execute method. 
 *
 */
public abstract class Task implements EventSubscriber {
    static PauseReason         yieldReason = new YieldReason();
    /**
     * Task id, automatically generated
     */
    public final int           id;
    static final AtomicInteger idSource = new AtomicInteger();

    /**
     * The stack manager in charge of rewinding and unwinding
     * the stack when Task.pause() is called.
     */
    protected Fiber            fiber;

    /**
     * The reason for pausing (duh) and performs the role of a await
     * condition in CCS. This object is responsible for resuming
     * the task. 
     * @see kilim.PauseReason
     */
    protected PauseReason      pauseReason;
    
    /**
     * running = true when it is put on the schdulers run Q (by Task.resume()). 
     * The Task.runExecute() method is called at some point; 'running' remains 
     * true until the end of runExecute (where it is reset), at which point a 
     * fresh decision is made whether the task needs to continue running.
     */
    protected boolean  running = false;
    protected boolean  done = false;


    /**
     * The thread in which to resume this task. Ideally, we shouldn't have any
     * preferences, but using locks in pausable methods will require the task
     * to be pinned to a thread.
     * @see kilim.ReentrantLock
     */
    WorkerThread    preferredResumeThread;

    /**
     * @see Task#preferredResumeThread
     */
    int numActivePins;




    /**
     * @see #informOnExit(Mailbox)
     */
    private LinkedList<Mailbox<ExitMsg>>  exitMBs;
    
    /** 
     * The object responsible for handing this task to a thread
     * when the task is runnable. 
     */
    protected Scheduler        scheduler;
    

    public    Object           exitResult = "OK";

    // TODO: move into a separate timer service or into the schduler.
    public final static Timer timer = new Timer(true);

    public Task() {
        id = idSource.incrementAndGet();
        fiber = new Fiber(this);
    }
    
    public int id() {
        return id;
    }

    public synchronized void setScheduler(Scheduler s) {
        if (running) {
            throw new AssertionError("Attempt to change task's scheduler while it is running");
        }
        scheduler = s;
    }
    
    protected void resumeOnScheduler(Scheduler s) throws Pausable {
        if (scheduler == s) return; 
        scheduler = s;
        Task.yield();
    }

    /**
     * Used to start the task; the task doesn't resume on its own. Custom
     * schedulers must be set (@see #setScheduler(Scheduler)) before
     * start() is called.
     * @return
     */
    public Task start() {
        if (scheduler == null) {
            setScheduler(Scheduler.getDefaultScheduler());
        }
        resume();
        return this;
    }
    
    /**
     * The generated code calls Fiber.upEx, which in turn calls
     * this to find out out where the current method is w.r.t
     * the closest _runExecute method. 
     * @return the number of stack frames above _runExecute(), not including
     * this method
     */
    public int getStackDepth() {
        StackTraceElement[] stes;
        stes = new Exception().getStackTrace();
        int len = stes.length;
        for (int i = 0; i < len; i++) {
            StackTraceElement ste = stes[i];
            if (ste.getMethodName().equals("_runExecute")){
                // discounting WorkerThread.run, Task._runExecute, and Scheduler.getStackDepth
                return i - 1;
            }
        }
        throw new AssertionError("Expected task to be run by WorkerThread");
    }
    
    public void onEvent(EventPublisher ep, Event e) {
        // This is sneaky. We _know_ that the only time a task will get registered 
        // is mailbox.put or get(), and that it'll be the pausereason as well. 
        if (ep == pauseReason) {
            resume();
        } 
    }
    /**
     * This is typically called by a pauseReason to resume the task.
     */
    public void resume() {
        if (scheduler == null) return;
        
        boolean doSchedule = false;
        // We don't check pauseReason while resuming (to verify whether
        // it is worth returning to a pause state. The code at the top of stack 
        // will be doing that anyway.
        synchronized(this) {
            if (done || running) return;
            running = doSchedule = true;
        }
        if (doSchedule) {
            scheduler.schedule(this);
        }
    }
    
    public void informOnExit(Mailbox<ExitMsg> exit) {
        if (isDone()) {
            exit.putnb(new ExitMsg(this, exitResult));
            return;
        }
        synchronized (this) {
            if (exitMBs == null) exitMBs = new LinkedList<Mailbox<ExitMsg>>();
            exitMBs.add(exit);
        }
    }
    
    /**
     * This is a placeholder that doesn't do anything useful.
     * Weave replaces the call in the bytecode from
     *     invokestateic Task.getCurrentTask
     * to
     *     load fiber
     *     getfield task
     */
    public static Task getCurrentTask() throws Pausable {return null;}

    /**
     * Analogous to System.exit, except an Object can 
     * be used as the exit value
     */

    public static void exit(Object aExitValue) throws Pausable {    }
    public static void exit(Object aExitValue, Fiber f) {
        assert f.pc == 0;
        f.task.setPauseReason(new TaskDoneReason(aExitValue));
        f.togglePause();
    }

    /**
     * Exit the task with a throwable indicating an error condition. The value
     * is conveyed through the exit mailslot (see informOnExit).
     * All exceptions trapped by the task scheduler also set the error result.
     */
    public static void errorExit(Throwable ex) throws Pausable {  }
    public static void errorExit(Throwable ex, Fiber f)  {
        assert f.pc == 0;
        f.task.setPauseReason(new TaskDoneReason(ex));
        f.togglePause();
    }

    public static void errNotWoven() {
        System.err.println("############################################################");
        System.err.println("Task has either not been woven or the classpath is incorrect");
        System.err.println("############################################################");
        Thread.dumpStack();
        System.exit(0);
    }
    
    public static void errNotWoven(Task t) {
        System.err.println("############################################################");
        System.err.println("Task " + t.getClass() + " has either not been woven or the classpath is incorrect");
        System.err.println("############################################################");
        Thread.dumpStack();
        System.exit(0);
    }

    /**
     * @param millis
     * to sleep. Like thread.sleep, except it doesn't throw an interrupt, and it
     * doesn't hog the java thread.
     */
    public static void sleep(final long millis) throws Pausable {
        // create a temp mailbox, and wait on it.
        final Mailbox<Integer> sleepmb = new Mailbox<Integer>();
        timer.schedule(new TimerTask() {
            public void run() {
                sleepmb.putnb(0);
            }
        }, millis);
        sleepmb.get(); // block until a message posted
    }

    /**
     * Yield cooperatively to the next task waiting to use the thread.
     */
    public static void yield() throws Pausable {errNotWoven();}
    public static void yield(Fiber f) {
        if (f.pc == 0) {
            f.task.setPauseReason(yieldReason);
        }
        f.togglePause();
    }

    /**
     * Ask the current task to pause with a reason object, that is 
     * responsible for resuming the task when the reason (for pausing)
     * is not valid any more.
     * @param pauseReason the reason
     */
    public static void pause(PauseReason pauseReason) throws Pausable {errNotWoven();}
    public static void pause(PauseReason pauseReason, Fiber f) {
        if (f.pc == 0) {
            f.task.setPauseReason(pauseReason);
        } else {
            f.task.setPauseReason(null);
        }
        f.togglePause();
    }

    /*
     * This is the fiber counterpart to the execute() method
     * that allows us to detec when a subclass has not been woven.
     * 
     * If the subclass has not been woven, it won't have an
     * execute method of the following form, and this method 
     * will be called instead. 
     */
    public  void execute() throws Pausable, Exception {
        errNotWoven(this);
    }

    public void execute(Fiber f) throws Exception {
        errNotWoven(this);
    }

    public String toString() {
        return "" + id + "(running=" + running + ",pr=" + pauseReason+")";
    }
    
    public String dump() {
        synchronized(this) {
            return "" + id + 
            "(running=" + running + 
            ", pr=" + pauseReason +
            ")";
        }
    }

    public void pinToThread() {
        numActivePins++;
    }

    public void unpinFromThread() {
        numActivePins--;
    }


    final protected void setPauseReason(PauseReason pr) {
        pauseReason = pr;
    }

    public final PauseReason getPauseReason() {
        return pauseReason;
    }

    
    public synchronized boolean isDone() {
        return done;
    }
    
    /**
     * Called by WorkerThread, it is the wrapper that performs pre and post
     * execute processing (in addition to calling the execute(fiber) method
     * of the task.
     */
    public void _runExecute(WorkerThread thread) throws NotPausable {
        Fiber f = fiber;
        boolean isDone = false; 
        try {
            assert (preferredResumeThread == null || preferredResumeThread == thread) : "Resumed " + id + " in incorrect thread. ";
            // start execute. fiber is wound to the beginning.
            execute(f.begin());
        
            // execute() done. Check fiber if it is pausing and reset it.
            isDone = f.end() || (pauseReason instanceof TaskDoneReason);

        } catch (Throwable th) {
            th.printStackTrace();
            // Definitely done
            setPauseReason(new TaskDoneReason(th));
            isDone = true;
        }

        if (isDone) {
            // inform on exit
            if (numActivePins > 0) {
                throw new AssertionError("Task ended but has active locks");
            }
            if (exitMBs != null) {
                if (pauseReason instanceof TaskDoneReason) {
                    exitResult = ((TaskDoneReason)pauseReason).exitObj;
                }
                ExitMsg msg = new ExitMsg(this, exitResult);
                for (Mailbox<ExitMsg> exitMB: exitMBs) {
                    exitMB.putnb(msg);
                }
            }
            preferredResumeThread = null;
        } else {
            if (thread != null) { // it is null for generators
                if (numActivePins > 0) {
                    preferredResumeThread = thread;
                } else {
                    assert numActivePins == 0: "numActivePins == " + numActivePins;
                    preferredResumeThread = null;
                }
            }
            synchronized (this) {
                running = false;
            }
            
            // The task has been in "running" mode until now, and may have missed
            // notifications to the pauseReason object (that is, it would have
            // resisted calls to resume(). If the pauseReason is not valid any
            // more, we'll resume. 
            if (!pauseReason.isValid(this)) {
                resume();
            }
        }
    }
        
    public ExitMsg joinb() {
        Mailbox<ExitMsg> mb = new Mailbox<ExitMsg>();
        informOnExit(mb);
        return mb.getb();
    }
    
    public ExitMsg join() throws Pausable {
        Mailbox<ExitMsg> mb = new Mailbox<ExitMsg>();
        informOnExit(mb);
        return mb.get();
    }

    @Override
    public boolean equals(Object obj) {
                return obj == this;
    }

    @Override
    public int hashCode() {
        return id;
    }
}

