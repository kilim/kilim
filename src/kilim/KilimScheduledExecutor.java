package kilim;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * This is a wrapper class for the <i>ScheduledThreadPoolExecutor</i>. It
 * provides an implementation for the <i>afterExecute()</i> found in the
 * <i>ThreadPoolExecutor</i> class to log any unexpected Runtime Exceptions.
 */

public final class KilimScheduledExecutor extends ScheduledThreadPoolExecutor {
    public KilimScheduledExecutor(int threads, ThreadFactory threadFactory) {
        super(threads, threadFactory);
    }

    public void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
    }

    /**
     * (non-Javadoc)
     * 
     * @see java.util.concurrent.ThreadPoolExecutor#afterExecute(java.lang.Runnable,
     * java.lang.Throwable)
     */
    public void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
    }
}
