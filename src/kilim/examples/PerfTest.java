// copyright 2016 nqzero - offered under the terms of the MIT License

package kilim.examples;

import kilim.ExitMsg;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

public class PerfTest extends Task {
    public static int QUEUE_CAPACITY = 32 * 1024;
    public static int REPETITIONS =  32 * 1024*1024 ;
    public static final int TEST_VALUE = 777;
    Mailbox<Integer> mymb;
    static Integer result;

    public PerfTest(Mailbox<Integer> mymb) {
            this.mymb = mymb;
    }

    public void execute() throws Pausable {
            int i = REPETITIONS;

            do {
                    result = mymb.get();
            } while (0 != --i);
    }

    /**
     * 
     * @param args optional arguments: queue capacity (in k) and repetitions (int meg)
     */
    public static void main(String args[]) {
        if (args.length > 0) QUEUE_CAPACITY = Integer.parseInt(args[0]) * 1024;
        if (args.length > 1) REPETITIONS    = Integer.parseInt(args[1]) * 1024*1024;

            for (int i = 0; i < 5; i++) {
                    System.gc();
                    performanceRun(i);
            }
            System.exit(0);
    }

    public static void performanceRun(int runNumber) {
            final long start = System.nanoTime();
            Mailbox<Integer> mbox = QUEUE_CAPACITY==0
                    ? new Mailbox<Integer>()
                    : new Mailbox<Integer>(QUEUE_CAPACITY,QUEUE_CAPACITY);
            Mailbox<ExitMsg> exitmb1 = new Mailbox<ExitMsg>();
            Mailbox<ExitMsg> exitmb2 = new Mailbox<ExitMsg>();
            Producer1 p1 = new Producer1(mbox);
            p1.informOnExit(exitmb1);

            PerfTest t2 = new PerfTest(mbox);
            t2.informOnExit(exitmb2);

            p1.start();
            t2.start();
            exitmb1.getb();
            exitmb2.getb();
            final long duration = System.nanoTime() - start;
            final long ops = (REPETITIONS * 1000L * 1000L * 1000L) / duration;
            if (result != TEST_VALUE) throw new RuntimeException("wrong value: " + result);
            System.out.format("%8d ops/sec\n", ops);

    }

    static class Producer1 extends Task {
            Mailbox<Integer> mymb;

            public Producer1(Mailbox<Integer> mymb) {
                    this.mymb = mymb;
            }

            public void execute() throws Pausable {
                    int i = REPETITIONS;
                    int value = TEST_VALUE;
                    do {
                            mymb.put(value);
                    } while (0 != --i);
            }
    }
}
