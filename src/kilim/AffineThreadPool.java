package kilim;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import kilim.timerhelper.Timer;
import kilim.timerhelper.TimerPriorityHeap;

public class AffineThreadPool {
    private static final int MAX_QUEUE_SIZE = 4096;
    private static final String colon_ = ":";

    protected static int getCurrentThreadId() {
        String name = Thread.currentThread().getName();
        int sIndex = name.indexOf(colon_);
        return Integer.parseInt(name.substring(sIndex + 1, name.length()));
    }

    private int nThreads_;
    private String poolName_;
    private AtomicInteger currentIndex_ = new AtomicInteger(0);
    private List<BlockingQueue<Runnable>> queues_ = new ArrayList<BlockingQueue<Runnable>>();
    private List<KilimStats> queueStats_ = new ArrayList<KilimStats>();
    private List<KilimThreadPoolExecutor> executorService_ = new ArrayList<KilimThreadPoolExecutor>();
    ScheduledExecutorService timer;

    public AffineThreadPool(int nThreads, String name,
            TimerPriorityHeap timerHeap, MailboxMPSC<Timer> timerQueue) {
        this(nThreads, MAX_QUEUE_SIZE, name, timerHeap, timerQueue);
    }

    public AffineThreadPool(int nThreads, int queueSize, String name,
            TimerPriorityHeap timerHeap, MailboxMPSC<Timer> timerQueue) {
        nThreads_ = nThreads;
        poolName_ = name;
        timer = Executors.newSingleThreadScheduledExecutor();
        for (int i = 0; i < nThreads; ++i) {
            String threadName = name + colon_ + i;
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(
                    queueSize);
            queues_.add(queue);

            KilimThreadPoolExecutor executorService = new KilimThreadPoolExecutor(
                    i, 1, queue, new ThreadFactoryImpl(threadName), timerHeap,
                    timerQueue, timer);
            executorService_.add(executorService);

            queueStats_.add(new KilimStats(12, "num"));
        }
    }

    public long getTaskCount() {
        long totalRemainingCapacity = 0L;
        for (BlockingQueue<Runnable> queue : queues_) {
            totalRemainingCapacity += queue.size();
        }
        return totalRemainingCapacity;
    }

    private int getNextIndex() {
        int value = 0, newValue = 0;
        do {
            value = currentIndex_.get();
            newValue = ((value != Integer.MAX_VALUE) ? (value + 1) : 0);
        } while (!currentIndex_.compareAndSet(value, newValue));
        return (newValue) % nThreads_;
    }

    public int publish(Task task) {
        int index = getNextIndex();
        task.setTid(index);
        return publish(index, task);
    }

    public int publish(int index, Task task) {
        KilimThreadPoolExecutor executorService = executorService_.get(index);
        executorService.submit(task);
        queueStats_.get(index).record(executorService.getQueueSize());
        return index;
    }

    public String getQueueStats() {
        String statsStr = "";
        for (int i = 0; i < queueStats_.size(); ++i) {
            statsStr += queueStats_.get(i).dumpStatistics(
                    poolName_ + ":QUEUE-SZ-" + i);
        }
        return statsStr;
    }

    public void shutdown() {
        for (ExecutorService executorService : executorService_) {
            executorService.shutdown();
        }
    }
}

class KilimThreadPoolExecutor extends ThreadPoolExecutor {
    TimerPriorityHeap timerHeap;
    MailboxMPSC<Timer> timerQueue;
    int id = 0;
    BlockingQueue<Runnable> queue;
    ScheduledExecutorService timer;
    Timer[] buf = new Timer[10];

    KilimThreadPoolExecutor(int id, int nThreads,
            BlockingQueue<Runnable> queue, ThreadFactory tFactory,
            TimerPriorityHeap timerHeap, MailboxMPSC<Timer> timerQueue,
            ScheduledExecutorService timer) {
        super(nThreads, nThreads, Integer.MAX_VALUE, TimeUnit.MILLISECONDS,
                queue, tFactory);
        this.id = id;
        this.timerHeap = timerHeap;
        this.queue = queue;
        this.timerQueue = timerQueue;
        this.timer = timer;

    }

    protected void afterExecute(Runnable r, Throwable th) {
        super.afterExecute(r, th);
        long max = 0;
        boolean taskFired = false;
        Timer t = null;
        if (timerHeap.lock.tryLock()) {
            int i = 0;
            do {
                timerQueue.fill(buf);

                for (i = 0; i < buf.length; i++) {
                    if (buf[i] != null) {
                        buf[i].onQueue.set(false);
                        if (buf[i].nextExecutionTime < 0) {
                            buf[i] = null;
                            continue;
                        }
                        long currentTime = System.currentTimeMillis();
                        long executionTime = buf[i].nextExecutionTime;
                        if (executionTime <= currentTime) {
                            buf[i].es.onEvent(null, Cell.timedOut);
                        } else {
                            if (!buf[i].onHeap) {
                                timerHeap.add(buf[i]);
                                buf[i].onHeap = true;
                            } else {
                                timerHeap.heapifyUp(buf[i].index);
                                timerHeap.heapifyDown(buf[i].index);
                            }
                        }
                        buf[i] = null;
                    } else {
                        break;
                    }
                }
            } while (i == 100);

            retry: do {

                taskFired = false;

                if (!timerHeap.isEmpty()) {

                    t = timerHeap.peek();
                    if (t.nextExecutionTime < 0) {
                        t.onHeap = false;
                        timerHeap.poll();
                        continue retry; // No action required, poll queue
                                        // again
                    }
                    long currentTime = System.currentTimeMillis();
                    long executionTime = t.nextExecutionTime;
                    if (taskFired = (executionTime <= currentTime)) {
                        t.onHeap = false;
                        timerHeap.poll();
                    } else {
                        max = executionTime - currentTime;
                    }
                }
                if (taskFired) {
                    t.es.onEvent(null, Cell.timedOut);
                }

            } while (taskFired);
            timerHeap.lock.unlock();
        }
        if (Scheduler.getDefaultScheduler().getTaskCount() == 0
                && this.getActiveCount() == 1
                && (timerHeap.size() != 0 || timerQueue.size() != 0)) {
            Runnable tt = new Runnable() {
                @Override
                public void run() {
                    if (queue.size() == 0) {
                        queue.add(new WatchdogTask());
                    }
                }
            };

            timer.schedule(tt, max, TimeUnit.MILLISECONDS);
        }
    }

    protected int getQueueSize() {
        return super.getQueue().size();
    }

    private class WatchdogTask implements Runnable {

        @Override
        public void run() {
        }

    }

}
