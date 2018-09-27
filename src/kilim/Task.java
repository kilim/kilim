/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A base class for tasks. A task is a lightweight thread (it contains its own
 * stack in the form of a fiber). A concrete subclass of Task must provide a
 * pausable execute method.
 * 
 */
public abstract class Task<TT> implements Runnable, EventSubscriber, Fiber.Worker {
    static PauseReason                   yieldReason           = new YieldReason();
    /**
     * Task id, automatically generated
     */
    public final int                     id;
    static final AtomicInteger           idSource              = new AtomicInteger();

    /**
     * The stack manager in charge of rewinding and unwinding the stack when
     * Task.pause() is called.
     */
    protected Fiber                      fiber;

    /**
     * The reason for pausing (duh) and performs the role of a await condition
     * in CCS. This object is responsible for resuming the task.
     * 
     * @see kilim.PauseReason
     */
    protected PauseReason                pauseReason;

    /**
     * running = true when it is put on the schdulers run Q (by Task.resume()).
     * The Task.runExecute() method is called at some point; 'running' remains
     * true until the end of runExecute (where it is reset), at which point a
     * fresh decision is made whether the task needs to continue running.
     */
    protected AtomicBoolean              running               = new AtomicBoolean(false);
    protected volatile boolean           done                  = false;

    /**
     * The thread in which to resume this task. Ideally, we shouldn't have any
     * preferences, but using locks in pausable methods will require the task to
     * be pinned to a thread.
     * 
     * @see kilim.ReentrantLock
     */
    volatile int                         preferredResumeThread = -1;

    private int                          tid;
    /**
     * @see Task#preferredResumeThread
     */
    int                                  numActivePins;

    /**
     * @see #informOnExit(Mailbox)
     */
    private LinkedList<Mailbox<ExitMsg<TT>>> exitMBs;

    /**
     * The object responsible for handing this task to a thread when the task is
     * runnable.
     */
    protected Scheduler                  scheduler;

    public    volatile Object           exitResult = "OK";


    // new timer service
    public kilim.timerservice.Timer       timer;
    
    // for debugging Task.resume race conditions
    private static boolean debugRunning = false;

    public Task() {
        id = idSource.incrementAndGet();
        fiber = new Fiber(this);
        timer = new kilim.timerservice.Timer(this);
    }
    Task(boolean dummy) { id = idSource.incrementAndGet(); }

    public int id() {
        return id;
    }

    public synchronized Task<TT> setScheduler(Scheduler s) {
        // if (running) {
        // throw new
        // AssertionError("Attempt to change task's scheduler while it is running");
        // }
        scheduler = s;
        return this;
    }

    public synchronized Scheduler getScheduler() {
        return scheduler;
    }

    public void resumeOnScheduler(Scheduler s) throws Pausable {
        if (scheduler == s)
            return;
        scheduler = s;
        Task.yield();
    }

    /**
     * Used to start the task; the task doesn't resume on its own. Custom
     * schedulers must be set (@see #setScheduler(Scheduler)) before start() is
     * called.
     * 
     * @return
     */
    public Task<TT> start() {
        if (scheduler == null) {
            setScheduler(Scheduler.getDefaultScheduler());
        }
        resume();
        return this;
    }

    private static Fiber.MethodRef runnerInfo = new Fiber.MethodRef("kilim.Task","run");
    Fiber.MethodRef getRunnerInfo() {
        return runnerInfo;
    }


    /* 
     * fix https://github.com/kilim/kilim/issues/40
     * ie, merge https://github.com/hyleeon/kilim/tree/fix-invoke
     * specifically https://github.com/hyleeon/kilim/commit/3e14940a59c1df1e07a6a56f060b012866f20b57
     * which is also https://github.com/kilim/kilim/pull/42
     *
     * When we called a Pausable-Method
     *   such as ExCatch.pausableInvokeCatch() -> ExCatch.pausableInvokeCatch0()) by Task.invoke()
     * The stack will like: 
     *      at kilim.test.ex.ExCatch.pausableInvokeCatch0(ExCatch.java:178)
     *      at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
     *      at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
     *      at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
     *      at java.lang.reflect.Method.invoke(Method.java:606)at kilim.Task.invoke(Task.java:285)
     *      at kilim.test.ex.ExCatch.pausableInvokeCatch(ExCatch.java:166)
     *      at kilim.test.ex.ExCatch.test(ExCatch.java:36)
     *      at kilim.test.ex.ExCatch.execute(ExCatch.java:26)
     *      at kilim.Task._runExecute(Task.java:442)
     *      at kilim.WorkerThread.run(WorkerThread.java:32)
     * If method pausableInvokeCatch0 try-catch a exception, we will call Fiber.upEx() to re-calculate
     * the stack size by this method. But we should discount "sun.reflect.*" and "java.lang.reflect.Method.invoke"
     */
    private static boolean skipInvoke(String klass,String name) {
        return klass.startsWith("sun.reflect.")
                | klass.startsWith("jdk.internal.reflect.")
                | ("java.lang.reflect.Method".equals(klass) & "invoke".equals(name));
    }
    
    /**
     * The generated code calls Fiber.upEx, which in turn calls this to find out
     * out where the current method is w.r.t the closest _runExecute method.
     * 
     * @return the number of stack frames above _runExecute(), not including
     * this method
     */
    public static int getStackDepth(Task task) {
        Fiber.MethodRef mr = task.getRunnerInfo();
        StackTraceElement[] stes;
        stes = new Exception().getStackTrace();
        int len = stes.length;
        int num = 0;
        for (int i = 0; i < len; i++) {
            StackTraceElement ste = stes[i];
            String name = ste.getMethodName();
            String klass = ste.getClassName();
            // ignore synthetic shim methods from SAM weaver - they don't get stack state allocated
            // fixme: should any other synthetic methods be skipped ?
            // fixme: could other vendors be using the same name for synthetic methods that shouldn't be skipped ?
            if (ste.getLineNumber() < 0 & Constants.Util.isSamShim(name))
                continue;
            if (skipInvoke(klass,name))
                continue;
            num++;
            if (name.equals(mr.methodname) & klass.equals(mr.classname)) {
                // discounting WorkerThread.run, Task._runExecute, and
                // Scheduler.getStackDepth
                // and convert count to index
                return num-2;
            }
        }
        throw new AssertionError("Expected task to be run by WorkerThread");
    }

    boolean checkTimeout() {
        return timer.getExecutionTime()==-2;
    }
    public void onEvent(EventPublisher ep, Event e) {
        if (e==kilim.timerservice.Timer.timedOut)
            timer.setLiteral(-2);
        boolean sched = resume();
    }

    public Thread getExecutionThread() {
        return Thread.currentThread();
    }

    /**
     * Add itself to scheduler if it is neither already running nor done.
     * 
     * @return True if it scheduled itself.
     */
    public boolean resume() {
        if (scheduler == null)
            return false;

        boolean doSchedule = false;
        // We don't check pauseReason while resuming (to verify whether
        // it is worth returning to a pause state. The code at the top of stack
        // will be doing that anyway.

        if (!done)
            if (running.compareAndSet(/* expected */false, /* update */true))
                doSchedule = true;
            else
                if (debugRunning) System.out.println("Task.pause.running: " + this);
                


        if (doSchedule) {
            if (preferredResumeThread == -1)
                scheduler.schedule(this);
            else
                scheduler.schedule(preferredResumeThread, this);
        }
        return doSchedule;
    }
    
    public synchronized void informOnExit(Mailbox<ExitMsg<TT>> exit) {
        if (done) {
            exit.putnb(new ExitMsg(this, exitResult));
            return;
        }
        if (exitMBs == null) {
            exitMBs = new LinkedList();
        }
        exitMBs.add(exit);
    }

    /**
     * This is a placeholder that doesn't do anything useful. Weave replaces the
     * call in the bytecode from invokestateic Task.getCurrentTask to load fiber
     * getfield task
     */
    public static Task getCurrentTask() throws Pausable {
        return null;
    }

    /**
     * Analogous to System.exit, except an Object can be used as the exit value
     */

    public static void exit(Object aExitValue) throws Pausable {
    }

    public static void exit(Object aExitValue, Fiber f) {
        assert f.pc == 0 : "f.pc != 0";
        f.task.setPauseReason(new TaskDoneReason(aExitValue));
        f.togglePause();
    }

    /**
     * Exit the task with a throwable indicating an error condition. The value
     * is conveyed through the exit mailslot (see informOnExit). All exceptions
     * trapped by the task scheduler also set the error result.
     */
    public static void errorExit(Throwable ex) throws Pausable {
    }

    public static void errorExit(Throwable ex, Fiber f) {
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
        System.err.println("Task " + t.getClass()
                + " has either not been woven or the classpath is incorrect");
        System.err.println("############################################################");
        Thread.dumpStack();
        System.exit(0);
    }

    static class ArgState extends kilim.State {
        Object   mthd;
        Object   obj;
        Object[] fargs;
    }

    /**
     * Invoke a pausable method via reflection. Equivalent to Method.invoke().
     * 
     * @param method
     * : The method to be invoked. (Implementation note: the corresponding woven
     * method is invoked instead).
     * @param obj
     * : The object on which the method is invoked. Can be null if the method is
     * static.
     * @param args
     * : Arguments to the method
     * @return
     * @throws Pausable
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     */
    public static Object invoke(Method method, Object obj, Object... args)
                                                                           throws Pausable,
                                                                           IllegalAccessException,
                                                                           IllegalArgumentException,
                                                                           InvocationTargetException {
        Fiber f = getCurrentTask().fiber;
        Object[] fargs;
        if (f.pc == 0) {
            method = getWovenMethod(method);
            // Normal invocation.
            if (args == null) {
                fargs = new Object[1];
            } else {
                fargs = new Object[args.length + 1]; // for fiber
                System.arraycopy(args, 0, fargs, 0, args.length);
            }
            fargs[fargs.length - 1] = f;
        } else {
            // Resuming from a previous yield
            ArgState as = (ArgState) f.getState();
            method = (Method) as.mthd;
            obj = as.obj;
            fargs = as.fargs;
        }
        f.down();
        Object ret = method.invoke(obj, fargs);
        switch (f.up()) {
            case Fiber.NOT_PAUSING__NO_STATE:
            case Fiber.NOT_PAUSING__HAS_STATE:
                return ret;
            case Fiber.PAUSING__NO_STATE:
                ArgState as = new ArgState();
                as.obj = obj;
                as.fargs = fargs;
                as.pc = 1;
                as.mthd = method;
                f.setState(as);
                return null;
            case Fiber.PAUSING__HAS_STATE:
                return null;
        }
        throw new IllegalAccessException("Internal Error");
    }

    // Given a method corresp. to "f(int)", return the equivalent woven method
    // for "f(int, kilim.Fiber)"
    private static Method getWovenMethod(Method m) {
        Class<?>[] ptypes = m.getParameterTypes();
        if (!(ptypes.length > 0 && ptypes[ptypes.length - 1].getName().equals("kilim.Fiber"))) {
            // The last param is not "Fiber", so m is not woven.
            // Get the woven method corresponding to m(..., Fiber)
            boolean found = false;
            LOOP: for (Method wm : m.getDeclaringClass().getDeclaredMethods()) {
                if (wm != m && wm.getName().equals(m.getName())) {
                    // names match. Check if the wm has the exact parameter
                    // types as m, plus a fiber.
                    Class<?>[] wptypes = wm.getParameterTypes();
                    if (wptypes.length != ptypes.length + 1
                            || !(wptypes[wptypes.length - 1].getName().equals("kilim.Fiber")))
                        continue LOOP;
                    for (int i = 0; i < ptypes.length; i++) {
                        if (ptypes[i] != wptypes[i])
                            continue LOOP;
                    }
                    m = wm;
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Found no pausable method corresponding to supplied method: "
                        + m);
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
        final Mailbox<Integer> sleepmb = new Mailbox<Integer>(1); // TODO: will
        // need a
        // better
        // mechanism
        // for
        // monitoring
        // later on.
        
        sleepmb.get(millis);
    }

    public static void shutdown() {
    }
    
    /**
     * Yield cooperatively to the next task waiting to use the thread.
     */
    public static void yield() throws Pausable {
        errNotWoven();
    }

    public static void yield(Fiber f) {
        if (f.task instanceof Continuation.FakeTask) { f.togglePause(); return; }
        if (f.pc == 0) {
            f.task.setPauseReason(yieldReason);
        } else {
            f.task.setPauseReason(null);
        }
        f.togglePause();
        f.task.checkKill();
    }

    /**
     * Ask the current task to pause with a reason object, that is responsible
     * for resuming the task when the reason (for pausing) is not valid any
     * more.
     * 
     * @param pauseReason
     * the reason
     */
    public static void pause(PauseReason pauseReason) throws Pausable {
        errNotWoven();
    }

    public static void pause(PauseReason pauseReason, Fiber f) {
        if (f.pc == 0) {
            f.task.setPauseReason(pauseReason);
        } else {
            f.task.setPauseReason(null);
        }
        f.togglePause();
        f.task.checkKill();
    }

    /*
     * This is the fiber counterpart to the execute() method that allows us to
     * detec when a subclass has not been woven.
     * 
     * If the subclass has not been woven, it won't have an execute method of
     * the following form, and this method will be called instead.
     */
    public void execute() throws Pausable, Exception {
        errNotWoven(this);
    }

    public void execute(Fiber f) throws Exception {
        errNotWoven(this);
    }

    public String toString() {
        return "" + id + "(running=" + running + ",pr=" + pauseReason + ")";
    }

    public String dump() {
        synchronized (this) {
            return "" + id + "(running=" + running + ", pr=" + pauseReason
                    + ")";
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

    public boolean isDone() {
        return done;
    }

    protected void setTid(int tid) {
        this.tid = tid;
    }
    /** return the thread ID that the task is currently running on, valid only during execute */
    public int getTid() {
        return tid;
    }

    /**
     * Called by WorkerThread, it is the wrapper that performs pre and post
     * execute processing (in addition to calling the execute(fiber) method of
     * the task.
     */
    public void run() throws NotPausable {
        Scheduler.setCurrentTask(this);
        Fiber f = fiber;
        boolean isDone = false;
        try {
            assert (preferredResumeThread == -1 || preferredResumeThread == tid) : "Resumed "
                    + id + " in incorrect thread. ";
            // start execute. fiber is wound to the beginning.
            execute(f.begin());

            // execute() done. Check fiber if it is pausing and reset it.
            isDone = f.end() || (pauseReason instanceof TaskDoneReason);
        } catch (Throwable th) {
            getScheduler().log(th);
            // Definitely done
            setPauseReason(new TaskDoneReason(th));
            isDone = true;
        }

        if (isDone) {
            // inform on exit
            if (numActivePins > 0) {
                throw new AssertionError("Task ended but has active locks");
            }
            if (pauseReason instanceof TaskDoneReason) {
                exitResult = ((TaskDoneReason)pauseReason).exitObj;
            }
            preferredResumeThread = -1;
            synchronized(this){
                done = true;
                if (exitMBs != null) {
                    ExitMsg msg = new ExitMsg(this, exitResult);
                    for (Mailbox<ExitMsg<TT>> exitMB: exitMBs) {
                        exitMB.putnb(msg);
                    }
                }
            }
        } else {
            if (tid >= 0) { // it is null for generators
                if (numActivePins > 0) {
                    preferredResumeThread = tid;
                } else {
                    assert numActivePins == 0 : "numActivePins == "
                            + numActivePins;
                    preferredResumeThread = -1;
                }
            }

            PauseReason pr = this.pauseReason;
            running.set(false);
            // The task has been in "running" mode until now, and may have
            // missed
            // notifications to the pauseReason object (that is, it would have
            // resisted calls to resume(). If the pauseReason is not valid any
            // more, we'll resume.
            if (!pr.isValid(this)) {
                // NOTE: At this point, another event could trigger resumption
                // before the following resume() can kick in. Additionally,
                // it is possible that the task could process all pending
                // events, so the following call to resume() may be spurious.
                // Cell/Mailbox's get/put watch out for spurious resumptions.
                resume();
            }
        }
    }

    public ExitMsg<TT> joinb() {
        Mailbox<ExitMsg<TT>> mb = new Mailbox();
        informOnExit(mb);
        return mb.getb();
    }

    public ExitMsg<TT> join() throws Pausable {
        Mailbox<ExitMsg<TT>> mb = new Mailbox();
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

    public void checkKill() {
    }

    public boolean getState() {
        return running.get();
    }
    
    public static class Spawn<TT> extends Task<TT> {
        Pausable.Spawn<TT> body;
        public Spawn() {}
        public Spawn(Pausable.Spawn<TT> body) { this.body = body; }
        public void execute() throws Pausable, Exception {
            TT val = body.execute();
            exit(val);
        }
    }
    public static class Fork extends Task {
        Pausable.Fork body;
        public Fork(Pausable.Fork body) { this.body = body; }
        public void execute() throws Pausable, Exception {
            body.execute();
        }
    }
    public static class Invoke<TT> extends Task<TT> {
        Method method;
        Object obj;
        Object [] args;
        public Invoke(Method method,Object obj,Object...args) {
            this.method = method;
            this.obj = obj;
            this.args = args;
        }
        public void execute() throws Pausable, Exception {
            Object val = Task.invoke(method,obj,args);
            exit(val);
        }
    }
    
    
    /**
     * Wraps the given object or lambda expression in a Task and starts that task.
     * Beware of inadvertent sharing when multiple lambdas are created in the same context 
     * 
     * @param body the lambda to delegate to
     * @return the spawned task. 
     */
    public static Task fork(final Pausable.Fork body) {
        return new Fork(body).start();
    }
    /**
     * Wraps the given object or lambda expression in a Task and starts that task.
     * Beware of inadvertent sharing when multiple lambdas are created in the same context 
     * 
     * @param body the lambda to delegate to
     * @return the spawned task. 
     */
    public static <TT> Spawn<TT> spawn(final Pausable.Spawn<TT> body) {
        Spawn<TT> spawn = new Spawn(body);
        spawn.start();
        return spawn;
    }

    public static Invoke spawn(Method method,Object obj,Object... args) {
        Invoke spawn = new Invoke(method,obj,args);
        spawn.start();
        return spawn;
    }

    /**
     * idledown the default scheduler
     * @see Scheduler#idledown()
     */
    public static void idledown() {
        if (Scheduler.defaultScheduler != null)
            Scheduler.defaultScheduler.idledown();
    }
}
