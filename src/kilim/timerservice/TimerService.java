package kilim.timerservice;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import kilim.AffineThreadPool;

import kilim.Cell;
import kilim.Event;
import kilim.EventPublisher;
import kilim.EventSubscriber;
import kilim.Scheduler;
import kilim.concurrent.MPSCQueue;

public class TimerService {
    private final MPSCQueue<Timer> timerQueue;
    private final TimerPriorityHeap timerHeap;
    private ScheduledExecutorService timerProxy;
    final private Lock lock;
    private static boolean debugStats = false;
    /** a recent, but not necessarily the most recent, watchdog */
    private volatile WatchdogTask argos = new WatchdogTask(0);
    { argos.done = true; }
    // the number of watchdogs set immediately (ie, max retry), scheduled, and scheduled+run
    private static volatile int c1, c2, c3;

    public TimerService() {
        timerHeap = new TimerPriorityHeap();
        timerQueue = new MPSCQueue<Timer>(Integer.getInteger("kilim.maxpendingtimers",100000));
        timerProxy = Executors.newSingleThreadScheduledExecutor();
        lock = new java.util.concurrent.locks.ReentrantLock();
    }

    public void shutdown() {
        timerProxy.shutdown();
        if (debugStats)
            System.out.format("timerservice: %d %d %d\n",c1,c2,c3);
    }

    // todo: verify that timer rechedule is thread safe
    // ie, under heavy load, can moving a timer cause it to be missed ?
    public void submit(Timer t) {
        if (t.onQueue.compareAndSet(false, true)) {
            if (!timerQueue.offer(t)) {
                new Exception(
                        "Maximum pending timers limit:"
                        + Integer.getInteger("kilim.maxpendingtimers", 100000)
                        + " exceeded, set kilim.maxpendingtimers property"
                ).printStackTrace();
            }
        }
    }

    public void trigger(final ThreadPoolExecutor executor) {
        int maxtry = 5;

        long clock = System.currentTimeMillis(), sched = 0;
        int retry = -1;
        while ((retry < 0 || !timerQueue.isEmpty() || (sched > 0 && sched <= clock))
                && ++retry < maxtry
                && lock.tryLock()) {
            try { 
                sched = doTrigger(clock);
            } finally { lock.unlock(); }
            clock = System.currentTimeMillis();
        }
        if (! Scheduler.getDefaultScheduler().isEmptyish()) return;

        WatchdogTask dragon = argos;

        if (retry==maxtry) {
            AffineThreadPool.publish(executor,argos = new WatchdogTask(0));
            c1++;
        }
        else if (sched > 0 & (dragon.done | sched < dragon.time)) {
            Watcher watcher = new Watcher(executor,sched);
            argos = watcher.dog;
            timerProxy.schedule(watcher,sched-clock,TimeUnit.MILLISECONDS);
            c2++;
        }
    }
    
    private long doTrigger(long currentTime) {
        Timer[] buf = new Timer[100];
        for (Timer t; (t = timerHeap.peek())!=null && t.getExecutionTime()==-1;)
            timerHeap.poll();
        int i = 0;
        timerQueue.fill(buf);
        do {
            for (i = 0; i<buf.length; i++) {
                Timer t = buf[i];
                if (t==null)
                    break;
                t.onQueue.set(false);
                long executionTime = t.getExecutionTime();
                if (executionTime<0)
                    t = null;
                else if (executionTime > 0 && executionTime<=currentTime)
                    t.es.onEvent(null,Cell.timedOut);
                else if (!t.onHeap) {
                    timerHeap.add(t);
                    t.onHeap = true;
                }
                else 
                    timerHeap.reschedule(t.index);
                buf[i] = null;
            }
        } while (i==100);
        while (!timerHeap.isEmpty()) {
            Timer t = timerHeap.peek();
            long executionTime = t.getExecutionTime();
            if (executionTime > currentTime)
                return executionTime;
            t.onHeap = false;
            timerHeap.poll();
            if (executionTime >= 0)
                t.es.onEvent(null,Cell.timedOut);
        }
        return 0L;
    }
    private class Watcher implements Runnable {
        ThreadPoolExecutor executor;
        WatchdogTask dog;
        Watcher(ThreadPoolExecutor $executor,long time) { executor = $executor; dog = new WatchdogTask(time); }
        @Override
        public void run() {
            if (! launch()) { dog.done = true; launch(); }
        }
        private boolean launch() {
            WatchdogTask hund = argos;
            if ((dog.time <= hund.time | hund.done) && Scheduler.getDefaultScheduler().isEmptyish()) {
                AffineThreadPool.publish(executor,dog);
                return true;
            }
            return false;
        }
    }
    static class WatchdogTask implements Runnable {
        volatile boolean done;
        final long time;
        public WatchdogTask(long $time) { time = $time; }
        @Override
        public void run() { done = true; c3++; }
    }

}
