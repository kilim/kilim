/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.util.LinkedList;

/** 
 * This is a basic FIFO Executor. It maintains a list of
 * runnable tasks and hands them out to WorkerThreads. Note that
 * we don't maintain a list of all tasks, but we will at some point
 * when we introduce monitoring/watchdog services. 
 * Paused tasks are not GC'd because their PauseReasons ought to be 
 * registered with some other live object.
 * 
 */
public class Scheduler {
    public static volatile Scheduler defaultScheduler = null;
    public static int defaultNumberThreads;
    
    public LinkedList<WorkerThread> allThreads = new LinkedList<WorkerThread>();
    public RingQueue<WorkerThread> waitingThreads = new RingQueue<WorkerThread>(10);
    protected volatile boolean shutdown = false;
    public RingQueue<Task> runnableTasks = new RingQueue<Task>(100);

    static {
        String s = System.getProperty("kilim.Scheduler.numThreads");
        if (s != null) {
            try {
                defaultNumberThreads = Integer.parseInt(s);
            } catch(Exception e) {}
        }
        if (defaultNumberThreads == 0) {
            defaultNumberThreads = Runtime.getRuntime().availableProcessors();
        }
    }
    protected Scheduler() {}
    
    public Scheduler(int numThreads) {
        for (int i = 0; i < numThreads; i++) {
            WorkerThread wt = new WorkerThread(this);
            allThreads.add(wt);
            addWaitingThread(wt);
            wt.start();
        }
    }
    
    void addWaitingThread(WorkerThread wt) {
      synchronized (waitingThreads) {
        waitingThreads.put(wt);
      }
    }
    
    WorkerThread getWaitingThread() {
      synchronized(waitingThreads) {
        return waitingThreads.get();
      }
    }

    /**
     * Schedule a task to run. It is the task's job to ensure that
     * it is not scheduled when it is runnable.
     */
    public void schedule(Task t) {
        WorkerThread wt = null;
        
        synchronized(this) {
            assert t.running == true :  "Task " + t + " scheduled even though running is false";
            runnableTasks.put(t);
        }
        wt = getWaitingThread();
        if (wt != null) {
            synchronized(wt) {
                wt.notify(); //TODO: Move to workerthread, because wait has moved.
            }
        }
    }
    
    public void shutdown() {
        shutdown = true;
        if (defaultScheduler == this) {
            defaultScheduler = null;
        }
        for (WorkerThread wt: allThreads) {
            synchronized(wt) {
                wt.notify();
            }
        }
    }
    
    public boolean isShutdown() {
      return shutdown;
    }
    
    /**
     * This is called in the WorkerThread's stack. It transfers a runnable task to the given worker thread's
     * list of runnables. If the task prefers a different worker thread, then the search continues (after notifying
     * the other thread that it has a task to execute).   
     * 
     * @return
     */
    void loadNextTask(WorkerThread wt) throws ShutdownException {
        while (true) {
            Task t = null;
            WorkerThread prefThread = null;
            ///////////////
            synchronized(this) {
                if (shutdown) throw new ShutdownException();

                t = runnableTasks.get();
                if (t == null) {
                  // WorkerThread will add itself to waitingThreads in WorkerThread.getNextTask()
                  break;
                } else {
                    prefThread = t.preferredResumeThread;
                    if (prefThread == null || prefThread == wt) { 
                      wt.addRunnableTask(t);
                      break; // Supplied worker thread has work to do
                    } else {
                      // The task states a preferred thread which is not the supplied worker thread
                      // Enqueue it and continue searching.
                      prefThread.addRunnableTask(t);
                      synchronized(prefThread) {
                        prefThread.notify();
                      }
                    }
                }
            }
            /////////////
        }
    }

    public synchronized static Scheduler getDefaultScheduler() {
        if (defaultScheduler == null) {
            defaultScheduler = new Scheduler(defaultNumberThreads);
        }
        return defaultScheduler;
    }
    
    public static void setDefaultScheduler(Scheduler s) {
        defaultScheduler = s;
    }

    public void dump() {
        System.out.println(runnableTasks);
//      for (WorkerThread w: allThreads) {
//          w.dumpStack();
//      }
    }
/*
    public static boolean isRunnable(Task task) {
        Scheduler s = defaultScheduler;
        synchronized (s) {
            if (s.runnableTasks.contains(task)) {
                return true;
            }
            for (WorkerThread wt: s.allThreads) {
                if (wt.tasks.contains(task)) return true;
            }
        }
        return false;
    }
    */
}
