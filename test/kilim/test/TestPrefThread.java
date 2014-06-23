package kilim.test;

import junit.framework.TestCase;
import kilim.ExitMsg;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.Task;

public class TestPrefThread extends TestCase {

    public void testPreferredThread() throws Exception {
        int NUM_TASKS = 500 * 1000;
        Scheduler s = new Scheduler(10);
        Mailbox<ExitMsg> exitMB = new Mailbox<ExitMsg>();
        Task t[] = new Task[NUM_TASKS];
        for (int i = 0; i < NUM_TASKS; i++) {
            t[i] = new PinnedTask(i);
            t[i].setScheduler(s);
            t[i].informOnExit(exitMB);
            t[i].start();
        }
        int i = 0;
        while (i != NUM_TASKS) {
            t[i].isDone();
            i++;
        }
        s.shutdown();

    }

    static class PinnedTask extends Task {
        private int taskId;
        private long prefId;

        public PinnedTask(int taskId) {
            this.taskId = taskId;
        }

        @Override
        public void execute() throws Pausable, Exception {
            prefId = Thread.currentThread().getId();
            this.pinToThread();
            for (int i = 0; i < 10000; i++) {
                Task.yield();
                long threadId = Thread.currentThread().getId();

                if (prefId != threadId) {
                    System.out.println("Task " + taskId
                            + "not resumed on preferred thread " + prefId
                            + "but on thread" + threadId);
                }
            }
            Task.exit(0);

        }

    }
}
