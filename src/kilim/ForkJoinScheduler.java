package kilim;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kilim.timerservice.Timer;
import kilim.timerservice.TimerService;

public class ForkJoinScheduler extends Scheduler
        implements TimerService.WatchdogContext {
    ForkJoinPool pool;
    private TimerService timerService;
    private AtomicInteger count = new AtomicInteger(0);

    public ForkJoinScheduler(int numThreads) {
        numThreads = numThreads < 0 ? numThreads : Scheduler.defaultNumberThreads;
        pool = new ForkJoinPool(numThreads);
        timerService = new TimerService(this);
    }

    public void publish(TimerService.WatchdogTask dog) {
        publish((Runnable) dog);
    }
    
    public boolean isEmpty() {
        return count.get()==0;
    }
    
    public void schedule(int index,Task task) {
        assert(index < 0);
        ForkJoinPool current = ForkJoinTask.getPool();
        ForkedTask fajita = new ForkedTask(task);
        count.incrementAndGet();
        if (current==pool)
            fajita.fork();
        else
            pool.submit(fajita);
    }
    public void publish(Runnable task) {
        ForkJoinPool current = ForkJoinTask.getPool();
        ForkedRunnable fajita = new ForkedRunnable(task);
        count.incrementAndGet();
        if (current==pool)
            fajita.fork();
        else
            pool.submit(fajita);
    }

    public boolean isEmptyish() {
        return ! pool.hasQueuedSubmissions();
    }

    public int numThreads() {
        return pool.getParallelism();
    }

    public void scheduleTimer(Timer t) {
        timerService.submit(t);
    }

    private void noop() {}
    
    public void idledown() {
        while (! pool.awaitQuiescence(100,TimeUnit.MILLISECONDS))
            noop();
        shutdown();
    }

    final class ForkedTask<V> extends ForkJoinTask<V> {
        Task<V> task;
        public ForkedTask(Task<V> task) { this.task = task; }
        public V getRawResult() { return null; }
        protected void setRawResult(V value) {}
        protected boolean exec() {
            int tid = ((ForkJoinWorkerThread) Thread.currentThread()).getPoolIndex();
            ((Task) task).setTid(tid);
            task.run();
            timerService.trigger(ForkJoinScheduler.this);
            count.decrementAndGet();
            return true;
        }
    }
    final class ForkedRunnable<V> extends ForkJoinTask<V> {
        Runnable task;
        public ForkedRunnable(Runnable task) { this.task = task; }
        public V getRawResult() { return null; }
        protected void setRawResult(V value) {}
        protected boolean exec() {
            task.run();
            timerService.trigger(ForkJoinScheduler.this);
            count.decrementAndGet();
            return true;
        }
    }
    public void shutdown() {
        super.shutdown();
        pool.shutdown();
        timerService.shutdown();
    }
    
}
