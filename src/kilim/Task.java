/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A base class for tasks. A task is a lightweight thread (it contains its 
 * own stack in the form of a fiber). A concrete subclass of Task must
 * provide a pausable execute method. 
 *
 */
public abstract class Task implements EventSubscriber {
    public volatile Thread currentThread = null;

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
    volatile WorkerThread    preferredResumeThread;

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

    public synchronized Task setScheduler(Scheduler s) {
//        if (running) {
//            throw new AssertionError("Attempt to change task's scheduler while it is running");
//        }
        scheduler = s;
        return this;
    }
    
    public synchronized Scheduler getScheduler() {
      return scheduler;
    }
    
    public void resumeOnScheduler(Scheduler s) throws Pausable {
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
        resume();
    }
    /**
     * Add itself to scheduler if it is neither already running nor done.
     * @return True if it scheduled itself.
     */
    public boolean resume() {
        if (scheduler == null) return false;
        
        boolean doSchedule = false;
        // We don't check pauseReason while resuming (to verify whether
        // it is worth returning to a pause state. The code at the top of stack 
        // will be doing that anyway.
        synchronized(this) {
            if (done || running) return false;
            running = doSchedule = true;
        }
        if (doSchedule) {
            scheduler.schedule(this);
        }
        return doSchedule;
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
        assert f.pc == 0 : "f.pc != 0";
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
        assert f.pc == 0 : "fc.pc != 0";
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

    static class ArgState extends kilim.State {
      Object mthd;
      Object obj;
      Object[] fargs;
    }

    /**
     * Invoke a pausable method via reflection. Equivalent to Method.invoke(). 
     * 
     * @param mthd:   The method to be invoked. (Implementation note: the corresponding woven method is invoked instead). 
     * @param target:  The object on which the method is invoked. Can be null if the method is static.
     * @param args:    Arguments to the method
     * @return
     * @throws Pausable
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     */
    public static Object invoke(Method mthd, Object target, Object ... args)
    throws Pausable, IllegalAccessException, IllegalArgumentException,
    InvocationTargetException
    {
      Fiber f = getCurrentTask().fiber;
      Object[] fargs;
      if (f.pc == 0) {
        mthd = getWovenMethod(mthd);
        // Normal invocation.
        if (args == null) {
          fargs = new Object[1];
        } else {
          fargs = new Object[args.length + 1]; // for fiber
          System.arraycopy(args, 0, fargs, 0, args.length);
        }
        fargs[fargs.length-1] = f;
      } else {
        // Resuming from a previous yield
        ArgState as = (ArgState)f.getState();
        mthd = (Method)as.mthd;
        target = as.obj;
        fargs = as.fargs;
      }
      f.down();
      Object ret = mthd.invoke(target, fargs);
      switch (f.up()) {
        case   Fiber.NOT_PAUSING__NO_STATE:
        case   Fiber.NOT_PAUSING__HAS_STATE: 
          return ret;
        case Fiber.PAUSING__NO_STATE : 
          ArgState as = new ArgState();
          as.obj = target;
          as.fargs = fargs;
          as.pc = 1;
          as.mthd = mthd;
          f.setState(as);
          return null;
        case   Fiber.PAUSING__HAS_STATE: 
          return null;
      }
      throw new IllegalAccessException("Internal Error");
    }

    // Given a method corresp. to "f(int)", return the equivalent woven method for "f(int, kilim.Fiber)" 
    private static Method getWovenMethod(Method m) {
      Class<?>[] ptypes = m.getParameterTypes();
      if (!(ptypes.length > 0 && ptypes[ptypes.length-1].getName().equals("kilim.Fiber"))) {
        // The last param is not "Fiber", so m is not woven.  
        // Get the woven method corresponding to m(..., Fiber)
        boolean found = false;
        LOOP:
          for (Method wm: m.getDeclaringClass().getDeclaredMethods()) {
            if (wm != m && wm.getName().equals(m.getName()) ) {
              // names match. Check if the wm has the exact parameter types as m, plus a fiber.
              Class<?>[] wptypes = wm.getParameterTypes();
              if (wptypes.length != ptypes.length + 1 || 
                  !(wptypes[wptypes.length-1].getName().equals("kilim.Fiber"))) continue LOOP;
              for (int i = 0; i < ptypes.length; i++) {
                  if (ptypes[i] != wptypes[i]) continue LOOP;
              }
              m = wm;
              found = true;
              break;
            }
          }
        if (!found) {
          throw new IllegalArgumentException("Found no pausable method corresponding to supplied method: " +m);
        }
      }
      return m;
    }
    /**
     * @param millis
     * to sleep. Like thread.sleep, except it doesn't throw an interrupt, and it
     * doesn't hog the java thread.
     */
    public static void sleep(final long millis) throws Pausable {
        // create a temp mailbox, and wait on it.
        final Mailbox<Integer> sleepmb = new Mailbox<Integer>(1); // TODO: will need a better mechanism for monitoring later on.
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
        } else {
            f.task.setPauseReason(null);
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
            currentThread = Thread.currentThread();
            assert (preferredResumeThread == null || preferredResumeThread == thread) : "Resumed " + id + " in incorrect thread. ";
            // start execute. fiber is wound to the beginning.
            execute(f.begin());
        
            // execute() done. Check fiber if it is pausing and reset it.
            isDone = f.end() || (pauseReason instanceof TaskDoneReason);
            assert (pauseReason == null && isDone) || (pauseReason != null && !isDone) : "pauseReason:" + pauseReason + ",isDone =" + isDone;
        } catch (Throwable th) {
            th.printStackTrace();
            // Definitely done
            setPauseReason(new TaskDoneReason(th));
            isDone = true;
        }

        if (isDone) {
            done = true;
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
            
            PauseReason pr = this.pauseReason;
            synchronized (this) {
                running = false;
                currentThread = null;
            }

            // The task has been in "running" mode until now, and may have missed
            // notifications to the pauseReason object (that is, it would have
            // resisted calls to resume(). If the pauseReason is not valid any
            // more, we'll resume. 
            if (!pr.isValid(this)) {
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

