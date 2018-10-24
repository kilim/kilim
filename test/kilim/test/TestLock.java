package kilim.test;

import junit.framework.TestCase;
import kilim.ExitMsg;
import kilim.ForkJoinScheduler;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.ReentrantLock;
import kilim.Scheduler;
import kilim.Task;

public class TestLock extends TestCase{
    static int numThreads = 4;
    static int maxDelay = 30;
    static int numTasks = 20;
    static int numIters = 20;
    static boolean force = false;
    static boolean preLock = true;
    static int ratio = 10;
    static int timeout = maxDelay/ratio*numIters*numTasks/numThreads + maxDelay*numIters;
    
    public void testLocks() {
        Scheduler scheduler = force ? new ForkJoinScheduler(numThreads) : Scheduler.getDefaultScheduler();
        Mailbox<ExitMsg> mb = new Mailbox<ExitMsg>();
        for (int i = 0; i < numTasks; i++) {
            Task t = new LockTask();
            t.informOnExit(mb);
            t.setScheduler(scheduler);
            t.start();
        }
        boolean ok = true;
        for (int i = 0; i < numTasks; i++) {
            ExitMsg em = mb.getb(timeout);
            assertNotNull("Timed out. #tasks finished = " + i + " of " + numTasks, em);
            if (em.result instanceof Exception) {
                ok = false; break;
            }
        }
        scheduler.shutdown();
        assertTrue(ok);
    }

    static void sleep(int delay) {
        try { Thread.sleep(delay); }
        catch (InterruptedException ex) {}
    }
    
    static int delay() {
        return (int) (maxDelay*Math.random());
    }
    
    static class LockTask extends Task {
        ReentrantLock syncLock = new ReentrantLock();
        @Override
        public void execute() throws Pausable, Exception {
            Task.sleep(delay());
            if (preLock) syncLock.preLock();
            try {
            for (int i = 0; i < numIters; i++) {
                syncLock.lock();
                Task.sleep(delay());
                syncLock.unlock();
                TestLock.sleep(delay()/ratio);
            }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
