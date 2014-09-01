package kilim;

import java.util.concurrent.TimeUnit;

public class ReentrantLock extends java.util.concurrent.locks.ReentrantLock {
    private static final long serialVersionUID = 1L;

    public ReentrantLock() {
        super(false);
    }

    public ReentrantLock(boolean fair) {
        super(fair);
    }

    public Thread getOwner() {
        return super.getOwner();
    }

    Thread locker = null; // /***************************

    @Override
    public void lock() {
        super.lock();
        Thread t = Thread.currentThread();
        locker = t;

        if (t != null) {
            Task tsk = Scheduler.getCurrentTask();
            if (tsk != null)
                tsk.pinToThread();
        }
    }

    @Override
    public boolean tryLock() {
        // TODO Auto-generated method stub
        boolean ret = super.tryLock();
        Thread t = Thread.currentThread();
        if (ret && (t != null)) {
            locker = t;
            Task tsk = Scheduler.getCurrentTask();
            if (tsk != null)
                tsk.pinToThread();
        }
        return ret;
    }

    @Override
    public boolean tryLock(long timeout, TimeUnit unit)
                                                       throws InterruptedException {
        boolean ret = super.tryLock(timeout, unit);
        Thread t = Thread.currentThread();
        if (ret && (t != null)) {
            locker = t;
            Task tsk = Scheduler.getCurrentTask();
            if (tsk != null)
                tsk.pinToThread();
        }
        return ret;
    }

    @Override
    public void unlock() {
        try {
            super.unlock();
        } catch (IllegalMonitorStateException ims) {
            System.err.println("Locking thread: " + locker.getName()
                    + ", unlocking thread: " + Thread.currentThread().getName());
            ims.printStackTrace();
            System.exit(1);
        }
        Thread t = Thread.currentThread();
        if (t != null) {
            Task tsk = Scheduler.getCurrentTask();
            if (tsk != null)
                tsk.unpinFromThread();
        }
    }
}
