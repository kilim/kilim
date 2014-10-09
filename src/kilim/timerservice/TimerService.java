package kilim.timerservice;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import kilim.Cell;
import kilim.Scheduler;
import kilim.concurrent.MPSCQueue;

public class TimerService {
	private final MPSCQueue<Timer> timerQueue;
	private final TimerPriorityHeap timerHeap;
	private ScheduledExecutorService timer;
	final private Lock lock ;
	public TimerService() {
		timerHeap = new TimerPriorityHeap();
		timerQueue = new MPSCQueue<Timer>(Integer.getInteger(
				"kilim.maxpendingtimers", 100000));
		timer = Executors.newSingleThreadScheduledExecutor();
		lock = new java.util.concurrent.locks.ReentrantLock();
	}

	public void submit(Timer t) {
		if (t.onQueue.compareAndSet(false, true)) {
			if (!timerQueue.offer(t)) {
				try {
					throw new Exception("Maximum pending timers limit:"
							+ Integer.getInteger("kilim.maxpendingtimers",
									100000)
							+ " exceeded, set kilim.maxpendingtimers property");
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

	}
	
	public void trigger(final ThreadPoolExecutor executor) {
		
		Timer[] buf = new Timer[100];
		long max = -1;
		Timer t = null;
		
		if (lock.tryLock()) {
			try {
				while ((t = timerHeap.peek()) != null
						&& t.getExecutionTime() == -1) {
					timerHeap.poll();
				}
				t = null;
				int i = 0;
				timerQueue.fill(buf);
				do {
					for (i = 0; i < buf.length; i++) {
						if (buf[i] != null) {
							buf[i].onQueue.set(false);
							long executionTime = buf[i].getExecutionTime();
							if (executionTime < 0) {
								buf[i] = null;
								continue;
							}
							long currentTime = System.currentTimeMillis();
							if (executionTime <= currentTime) {
								buf[i].es.onEvent(null, Cell.timedOut);
							} else {
								if (!buf[i].onHeap) {
									timerHeap.add(buf[i]);
									buf[i].onHeap = true;
								} else {
									timerHeap.reschedule(buf[i].index);

								}
							}
							buf[i] = null;
						} else {
							break;
						}
					}
				} while (i == 100);
				while (!timerHeap.isEmpty()) {
					t = timerHeap.peek();
					long executionTime = t.getExecutionTime();
					if (executionTime < 0) {
						t.onHeap = false;
						timerHeap.poll();
						continue; // No action required, poll queue
						// again
					}
					long currentTime = System.currentTimeMillis();
					if (executionTime <= currentTime) {
						t.onHeap = false;
						timerHeap.poll();
						t.es.onEvent(null, Cell.timedOut);
					} else {
						max = executionTime - currentTime;
						break;
					}
				}

				if ((max > 0)
						&& (Scheduler.getDefaultScheduler().getTaskCount() == 0)
						&& (timerHeap.size() != 0 || timerQueue.size() != 0)) {
					Runnable tt = new Runnable() {
						@Override
						public void run() {

							if (executor.getQueue().size() == 0) {
								executor.getQueue().add(new WatchdogTask());
							}
						}
					};

					timer.schedule(tt, max, TimeUnit.MILLISECONDS);
				}
			} finally {
				lock.unlock();
			}
		}
	}

	private class WatchdogTask implements Runnable {

		@Override
		public void run() {
		}

	}

}
