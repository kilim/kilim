/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.util.LinkedList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class WorkerThread extends Thread {
    Task runningTask;
    /**
     * A list of tasks that prefer to run only on this thread. This is used
     * by kilim.ReentrantLock and Task to ensure that lock.release() is
     * done on the same thread as lock.acquire()
     */
    RingQueue<Task> tasks = new RingQueue<Task>(10);
    Scheduler scheduler;

    public int numResumes = 0;
    
    WorkerThread(Scheduler ascheduler) {
        scheduler=ascheduler;
    }
    
    public void run() {
        try {
        while (true) {
            Task t = scheduler.getNextTask(this); // blocks until task available
            if (t == null) break; // scheduler shut down
            runningTask = t;
            t._runExecute(this);
        }
        } catch (Throwable ex) {
            ex.printStackTrace();
            System.out.println(runningTask);
        }
    }

    public Task getCurrentTask() {
        return runningTask;
    }
    
    public synchronized void addRunnableTask(Task t) {
        assert t.preferredResumeThread == null || t.preferredResumeThread == this : "Task given to wrong thread";
        tasks.put(t);
        notify();
    }

    public synchronized boolean hasTasks() {
    	return tasks.size() > 0;
    }
    public synchronized Task getNextTask() {
        return tasks.get();
    }
        
    public synchronized void waitForMsgOrSignal() {
        try {
            if (tasks.size() == 0) {
                wait();
            }
        } catch (InterruptedException ignore) {}
    }
}
