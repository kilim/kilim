/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

import kilim.queuehelper.TaskQueue;
import kilim.timerhelper.Timer;

/**
 * A cell is a single-space buffer that supports multiple producers and a single
 * consumer, functionally identical to Mailbox bounded to a size of 1 (and hence
 * optimized for this size)
 */

public class Cell<T> implements PauseReason, EventPublisher {
    Deque<EventSubscriber> srcs = new ConcurrentLinkedDeque<EventSubscriber>();
    public static final int SPACE_AVAILABLE = 1;
    public static final int MSG_AVAILABLE = 2;
    public static final int TIMED_OUT = 3;
    public static final Event spaceAvailble = new Event(MSG_AVAILABLE);
    public static final Event messageAvailable = new Event(SPACE_AVAILABLE);
    public static final Event timedOut = new Event(TIMED_OUT);
    // private static final String defaultName_ = "DEFAULT-CELL";

    VolatileReferenceCell<EventSubscriber> sink = new VolatileReferenceCell<EventSubscriber>();
    VolatileReferenceCell<T> message = new VolatileReferenceCell<T>();

    // DEBUG stuff
    // To do: move into monitorable stat object
    /*
     * public int nPut = 0; public int nGet = 0; public int nWastedPuts = 0;
     * public int nWastedGets = 0;
     */
    public Cell() {
    }

    /**
     * Non-blocking, nonpausing get.
     * 
     * @param eo
     *            . If non-null (and if there is no message), registers this
     *            observer. The observer is notified with a MessageAvailable
     *            event when a put() is done.
     * 
     * @return buffered message if there's one, or null
     */
    public T get(EventSubscriber eo) {
        EventSubscriber producer = null;
        T ret;
        ret = message.get();
        if (ret == null) {
            addMsgAvailableListener(eo);
        } else {
            message.compareAndSet(ret, null);
            if (srcs.size() > 0) {
                producer = srcs.poll();
            }
        }
        if (producer != null) {
            producer.onEvent(this, spaceAvailble);
        }
        return ret;
    }

    /**
     * Non-blocking, nonpausing put.
     * 
     * @param eo
     *            . If non-null, registers this observer and calls it with an
     *            SpaceAvailable event when there's space.
     * @return buffered message if there's one, or null
     */
    public boolean put(T amsg, EventSubscriber eo) {
        boolean ret = true; // assume we'll be able to enqueue
        EventSubscriber subscriber;
        if (amsg == null)
            throw new NullPointerException("Null message supplied to put");

        if (message.compareAndSet(null, amsg)) {
            subscriber = sink.get();
            // sink.set(null);
        } else {
            ret = false;
            subscriber = null;
            if (eo != null) {
                srcs.add(eo);
            }
        }
        // notify get's subscriber that something is available
        if (subscriber != null) {
            // removeMsgAvailableListener(subscriber);
            if (sink.compareAndSet(subscriber, null)) {
                subscriber.onEvent(this, messageAvailable);
            }
        }
        return ret;
    }

    /**
     * Get, don't pause or block.
     * 
     * @return stored message, or null if no message found.
     */
    public T getnb() {
        return get(null);
    }

    /**
     * @return non-null message.
     * @throws Pausable
     */
    public T get() throws Pausable {
        Task t = Task.getCurrentTask();
        T msg = get(t);
        while (msg == null) {
            Task.pause(this);
            removeMsgAvailableListener(t);
            msg = get(t);
        }
        return msg;
    }

    /**
     * @return non-null message.
     * @throws Pausable
     */
    public T get(long timeoutMillis) throws Pausable {
        final Task t = Task.getCurrentTask();
        T msg = get(t);
        long begin = System.currentTimeMillis();
        long time = timeoutMillis;
        while (msg == null) {
            t.timer_new.setTimer(time);
            if (t.timer_new.onQueue.compareAndSet(false, true)) {
                t.scheduler.producertaskQueue.put(t.timer_new);
            }
            Task.pause(this);
            t.timer_new.nextExecutionTime = -1;
            removeMsgAvailableListener(t);
            time = timeoutMillis - (System.currentTimeMillis() - begin);
            if (time <= 0) {
                break;
            }
            msg = get(t);
        }
        return msg;
    }

    public void addSpaceAvailableListener(EventSubscriber spcSub) {
        srcs.add(spcSub);
    }

    public void removeSpaceAvailableListener(EventSubscriber spcSub) {
        srcs.remove(spcSub);
    }

    public void addMsgAvailableListener(EventSubscriber msgSub) {
        if (sink.get() != null && sink.get() != msgSub) {
            throw new AssertionError(
                    "Error: A mailbox can not be shared by two consumers.  New = "
                            + msgSub + ", Old = " + sink);
        }
        sink.compareAndSet(null, msgSub);
    }

    public void removeMsgAvailableListener(EventSubscriber msgSub) {
        sink.compareAndSet(msgSub, null);
    }

    public boolean putnb(T msg) {
        return put(msg, null);
    }

    public void put(T msg) throws Pausable {
        Task t = Task.getCurrentTask();
        while (!put(msg, t)) {
            Task.pause(this);
            removeSpaceAvailableListener(t);
        }
    }

    public boolean put(T msg, int timeoutMillis) throws Pausable {
        final Task t = Task.getCurrentTask();
        long begin = System.currentTimeMillis();
        long time = timeoutMillis;
        while (!put(msg, t)) {
            t.timer_new.setTimer(time);
            if (t.timer_new.onQueue.compareAndSet(false, true)) {
                t.scheduler.producertaskQueue.put(t.timer_new);
            }
            Task.pause(this);
            t.timer_new.nextExecutionTime = -1;
            removeSpaceAvailableListener(t);
            time = timeoutMillis - (System.currentTimeMillis() - begin);
            if (time <= 0) {
                return false;
            }
        }
        return true;
    }

    public void putb(T msg) {
        putb(msg, 0 /* infinite wait */);
    }

    public class BlockingSubscriber implements EventSubscriber {
        public volatile boolean eventRcvd = false;

        public void onEvent(EventPublisher ep, Event e) {
            synchronized (Cell.this) {
                eventRcvd = true;
                Cell.this.notify();
            }
        }

        public void blockingWait(final long timeoutMillis) {
            long start = System.currentTimeMillis();
            long remaining = timeoutMillis;
            boolean infiniteWait = timeoutMillis == 0;
            synchronized (Cell.this) {
                while (!eventRcvd && (infiniteWait || remaining > 0)) {
                    try {
                        Cell.this.wait(infiniteWait ? 0 : remaining);
                    } catch (InterruptedException ie) {
                    }
                    long elapsed = System.currentTimeMillis() - start;
                    remaining -= elapsed;
                }
            }
        }
    }

    public void putb(T msg, final long timeoutMillis) {
        BlockingSubscriber evs = new BlockingSubscriber();
        if (!put(msg, evs)) {
            evs.blockingWait(timeoutMillis);
        }
        if (!evs.eventRcvd) {
            removeSpaceAvailableListener(evs);
        }
    }

    public boolean hasMessage() {
        return message.get() != null;
    }

    public boolean hasSpace() {
        return message.get() == null;
    }

    /**
     * retrieve a message, blocking the thread indefinitely. Note, this is a
     * heavyweight block, unlike #get() that pauses the Fiber but doesn't block
     * the thread.
     */

    public T getb() {
        return getb(0);
    }

    /**
     * retrieve a msg, and block the Java thread for the time given.
     * 
     * @param millis
     *            . max wait time
     * @return null if timed out.
     */
    public T getb(final long timeoutMillis) {
        BlockingSubscriber evs = new BlockingSubscriber();
        T msg;

        if ((msg = get(evs)) == null) {
            evs.blockingWait(timeoutMillis);
            if (evs.eventRcvd) {
                msg = get(null); // non-blocking get.
                assert msg != null : "Received event, but message is null";
            }
        }
        if (msg == null) {
            removeMsgAvailableListener(evs);
        }
        return msg;
    }

    public String toString() {
        return "id:" + System.identityHashCode(this) + " " + message;
    }

    // Implementation of PauseReason
    public boolean isValid(Task t) {
        if (t == sink.get()) {
            return !hasMessage();
        } else if (srcs.contains(t)) {
            return !hasSpace();
        } else {
            return false;
        }
    }
}
