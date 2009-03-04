package kilim.test;

import junit.framework.TestCase;
import kilim.ExitMsg;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.ReentrantLock;
import kilim.Scheduler;
import kilim.Task;

public class TestLock extends TestCase{
    public void testLocks() {
        Scheduler scheduler = new Scheduler(4);
        Mailbox<ExitMsg> mb = new Mailbox<ExitMsg>();
        for (int i = 0; i < 100; i++) {
            Task t = new LockTask();
            t.informOnExit(mb);
            t.setScheduler(scheduler);
            t.start();
        }
        boolean ok = true;
        for (int i = 0; i < 100; i++) {
            ExitMsg em = mb.getb(5000);
            assertNotNull("Timed out. #tasks finished = " + i + "/100", em);
            if (em.result instanceof Exception) {
                ok = false; break;
            }
        }
        scheduler.shutdown();
        assertTrue(ok);
    }
    
    static class LockTask extends Task {
        ReentrantLock syncLock = new ReentrantLock();
        @Override
        public void execute() throws Pausable, Exception {
//            System.out.println("Start #" + id);
            try {
            for (int i = 0; i < 1000; i++) {
                syncLock.lock();
                Task.yield();
                syncLock.unlock();
            }
            } catch (Exception e) {
                e.printStackTrace();
            }
//            System.out.println("Done #" + id);
        }
    }
}
