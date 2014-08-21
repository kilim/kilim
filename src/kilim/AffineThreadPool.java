package kilim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import kilim.queuehelper.TaskQueue;
import kilim.timerhelper.Timer;

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

    public AffineThreadPool(int nThreads, String name, TaskQueue taskQueue/*
                                                                           * PriorityBlockingQueue
                                                                           * <
                                                                           * Timer
                                                                           * >
                                                                           * taskQueue
                                                                           */,
            MailboxMPSC<Timer> producertaskQueue) {
        this(nThreads, MAX_QUEUE_SIZE, name, taskQueue, producertaskQueue);
    }

    public AffineThreadPool(int nThreads, int queueSize, String name,
            TaskQueue taskQueue /*
                                 * PriorityBlockingQueue< Timer> taskQueue
                                 */, MailboxMPSC<Timer> producertaskQueue) {
        nThreads_ = nThreads;
        poolName_ = name;
        for (int i = 0; i < nThreads; ++i) {
            String threadName = name + colon_ + i;
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(
                    queueSize);
            queues_.add(queue);

            KilimThreadPoolExecutor executorService = new KilimThreadPoolExecutor(
                    i, 1, queue, new ThreadFactoryImpl(threadName), taskQueue,
                    producertaskQueue);
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
        currentIndex_.compareAndSet(Integer.MAX_VALUE, 0);
        int index = currentIndex_.getAndIncrement() % nThreads_;
        return index;
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
    TaskQueue taskQueue;
    MailboxMPSC<Timer> producertaskQueue;
    int id = 0;
    BlockingQueue<Runnable> queue;

    KilimThreadPoolExecutor(int id, int nThreads,
            BlockingQueue<Runnable> queue, ThreadFactory tFactory,
            TaskQueue taskQueue/*
                                * PriorityBlockingQueue < Timer> taskQueue
                                */, MailboxMPSC<Timer> producertaskQueue) {
        super(nThreads, nThreads, Integer.MAX_VALUE, TimeUnit.MILLISECONDS,
                queue, tFactory);
        this.id = id;
        this.taskQueue = taskQueue;
        this.queue = queue;
        this.producertaskQueue = producertaskQueue;
    }

    protected void afterExecute(Runnable r, Throwable th) {
        super.afterExecute(r, th);
        long max = 0;
        do {
            boolean taskFired = false;
            Timer t = null;
            if (taskQueue.lock.tryLock()) {

                int i = 0;
                do {
                    Timer[] buf = new Timer[100];
                    producertaskQueue.fill(buf);

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
                                    taskQueue.add(buf[i]);
                                    buf[i].onHeap = true;
                                } else {
                                    taskQueue.heapifyUp(buf[i].index);
                                    taskQueue.heapifyDown(buf[i].index);
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

                    if (!taskQueue.isEmpty()) {

                        t = taskQueue.peek();
                        if (t.nextExecutionTime < 0) {
                            t.onHeap = false;
                            taskQueue.poll();
                            continue retry; // No action required, poll queue
                                            // again
                        }
                        long currentTime = System.currentTimeMillis();
                        long executionTime = t.nextExecutionTime;
                        if (taskFired = (executionTime <= currentTime)) {
                            t.onHeap = false;
                            taskQueue.poll();
                        } else {
                            max = executionTime - currentTime;
                        }
                    }
                    if (taskFired) {
                        t.es.onEvent(null, Cell.timedOut);
                    }

                } while (taskFired);
                taskQueue.lock.unlock();
            }
            if (queue.isEmpty()) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } while (queue.isEmpty());

    }

    protected int getQueueSize() {
        return super.getQueue().size();
    }
}