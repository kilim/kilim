package kilim.examples;

import kilim.ExitMsg;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.Task;

public class OnePOneC extends Task {
	public static final int QUEUE_CAPACITY = 32 * 1024;
	public static final int REPETITIONS = 50 * 1000 * 1000;
	public static final Integer TEST_VALUE = Integer.valueOf(777);
	Mailbox<Integer> mymb;
	static Integer result;

	public OnePOneC(Mailbox<Integer> mymb) {
		this.mymb = mymb;
		//this.setScheduler(new Scheduler(2));
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
		Mailbox<Integer> mbox = new Mailbox<Integer>();
		Mailbox<ExitMsg> exitmb1 = new Mailbox<ExitMsg>();
		Mailbox<ExitMsg> exitmb2 = new Mailbox<ExitMsg>();
		Producer p1 = new Producer(mbox);
	//	Scheduler s = new Scheduler(2);
	//	p1.setScheduler(s);
		p1.informOnExit(exitmb1);
	
		OnePOneC t2 = new OnePOneC(mbox);
	//	t2.setScheduler(s);
		t2.informOnExit(exitmb2);
	
		p1.start();
		t2.start();
		exitmb1.getb();
		exitmb2.getb();
		final long duration = System.nanoTime() - start;
		final long ops = (REPETITIONS * 1000L * 1000L * 1000L) / duration;
		System.out.format("%d - ops/sec=%,d -result=%d\n",
				Integer.valueOf(runNumber), Long.valueOf(ops), result);

	}
}

class Producer extends Task {
	public static final int QUEUE_CAPACITY = 32 * 1024;
	public static final int REPETITIONS = 50 * 1000 * 1000;
	public static final Integer TEST_VALUE = Integer.valueOf(777);

	Mailbox<Integer> mymb;

	public Producer(Mailbox<Integer> mymb) {
		this.mymb = mymb;
	}

	public void execute() throws Pausable {
		int i = REPETITIONS;
		do {
			mymb.put(TEST_VALUE);
		} while (0 != --i);
	}
}
