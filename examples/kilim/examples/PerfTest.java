package kilim.examples;

import kilim.ExitMsg;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

public class PerfTest extends Task {
	public static final int QUEUE_CAPACITY = 32 * 1024;
	public static final int REPETITIONS =  32 * 1024*1024 ;
	public static final Integer TEST_VALUE = Integer.valueOf(777);
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

	public static void main(String args[]) {

		for (int i = 0; i < 5; i++) {
			System.gc();
			performanceRun(i);
		}
		System.exit(0);
	}

	public static void performanceRun(int runNumber) {
		final long start = System.nanoTime();
		Mailbox<Integer> mbox = new Mailbox<Integer>(QUEUE_CAPACITY,QUEUE_CAPACITY);
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
		System.out.format("%d - ops/sec=%,d",
				Integer.valueOf(runNumber), Long.valueOf(ops));

	}
}

class Producer1 extends Task {
	public static final int QUEUE_CAPACITY = 32 * 1024;
	public static final int REPETITIONS =32 * 1024 *1024;
	public static final Integer TEST_VALUE = Integer.valueOf(777);

	Mailbox<Integer> mymb;

	public Producer1(Mailbox<Integer> mymb) {
		this.mymb = mymb;
	}

	public void execute() throws Pausable {
		int i = REPETITIONS;
		int value = 888;
		do {

			mymb.put(value);
		} while (0 != --i);
	}
}
