package kilim;

import java.lang.reflect.Method;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;
import kilim.timerservice.Timer;
import kilim.timerservice.TimerService;

/*
    testing with this scheduler:
        ant testcompile
        cp=$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/fd/1)
        java -ea -cp target/classes:$cp kilim.tools.Kilim kilim.ForkJoinScheduler \
            junit.textui.TestRunner kilim.test.AllWoven
*/


public class ForkJoinScheduler extends Scheduler
        implements TimerService.WatchdogContext {
    ForkJoinPool pool;
    private TimerService timerService;
    private AtomicInteger count = new AtomicInteger(0);

    public ForkJoinScheduler(int numThreads) {
        numThreads = numThreads >= 0 ? numThreads : Scheduler.defaultNumberThreads;
        pool = new ForkJoinPool(numThreads);
        timerService = new TimerService(this);
    }

    public void publish(TimerService.WatchdogTask dog) {
        publish((Runnable) dog);
    }
    
    public boolean isEmpty() {
        return count.get()==0;
    }

    public boolean isPinnable() { return false; }
    
    public void schedule(int index,Task task) {
        assert index < 0 : "attempt to pin task to FJS";
        publish(task);
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

    public void idledown() {
        while (!Thread.interrupted())
            if (waitIdle(100)) return;
    }
    public boolean waitIdle(int delay) {
        // avoid the java 8 feature to simplify the java 7 build
        // if (! pool.awaitQuiescence(delay,java.util.concurrent.TimeUnit.MILLISECONDS)) return false;
        if (! isEmpty())
            return false;
        if (timerService.isEmptyLazy(this))
            return true;
        try { Thread.sleep(delay); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        return false;
    }

    final class ForkedRunnable<V> extends ForkJoinTask<V> {
        Runnable task;
        public ForkedRunnable(Runnable task) { this.task = task; }
        public V getRawResult() { return null; }
        protected void setRawResult(V value) {}
        protected boolean exec() {
            // generally would Task.setTid here, but they can't be pinned and non-pool threads can participate
            //   so skip it
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
    private static String[] processArgs(String[] args,int offset) {
        String[] ret = new String[args.length-offset];
        if (ret.length > 0) 
            System.arraycopy(args, offset, ret, 0, ret.length);
        return ret;
    }
    private static Integer parseNum(String [] args,int index) {
        try {
            return Integer.parseInt(args[index]);
        }
        catch (Throwable ex) { return null; }
    }
    private static void run(String className,String method,String ... args) throws Exception {
        Class<?> mainClass = ForkJoinScheduler.class.getClassLoader().loadClass(className);
        Method mainMethod = mainClass.getMethod(method, new Class[]{String[].class});
        mainMethod.invoke(null,new Object[] {args});
    }
    /** run the main method from another class using this scheduler as the default scheduler */
    public static void main(String[] args) throws Exception {
        Integer numThreads = parseNum(args,0);
        int offset = numThreads==null ? 0:1;
        if (args.length <= offset) {
            System.out.println(
                    "usage:\n"
                    + "  java kilim.ForkJoinScheduler [numThreads] class [args]\n"
                    + "call the main method of the specified class and pass the remaining arguments,\n"
                    + "  using `new ForkJoinScheduler(numThreads)` as the default scheduler"
            );
            System.exit(1);
        }
        int num = numThreads==null || numThreads <= 0 ? Scheduler.defaultNumberThreads : numThreads;
        Scheduler sched = new ForkJoinScheduler(num);
        Scheduler.setDefaultScheduler(sched);
        String className = args[offset];
        String [] pargs = processArgs(args,offset+1);
        run(className,"main",pargs);
    }
    
}
