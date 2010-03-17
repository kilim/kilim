/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.util.LinkedList;
import java.util.TimerTask;

/**
 * A cell is a single-space buffer that supports multiple producers and a single
 * consumer, functionally identical to Mailbox bounded to a size of 1 (and hence
 * optimized for this size)
 */

public class Cell<T> implements PauseReason, EventPublisher {
    T msg;
    EventSubscriber sink;
    
    public static final int SPACE_AVAILABLE = 1;
    public static final int MSG_AVAILABLE = 2;
    public static final int TIMED_OUT = 3;
    public static final Event spaceAvailble = new Event(MSG_AVAILABLE);
    public static final Event messageAvailable = new Event(SPACE_AVAILABLE);
    public static final Event timedOut = new Event(TIMED_OUT);
    
    LinkedList<EventSubscriber> srcs = new LinkedList<EventSubscriber>();

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
     * @param eo. If non-null, registers this observer and calls it with a MessageAvailable event when
     *  a put() is done.
     * @return buffered message if there's one, or null 
     */
    public T get(EventSubscriber eo) {
        EventSubscriber producer = null;
        T ret;
        synchronized(this) {
            if (msg == null) {
                ret = null;
                addMsgAvailableListener(eo);
            } else {
                ret = msg;
                msg = null;
                if (srcs.size() > 0) {
                    producer = srcs.poll();
                }
            }
        }
        if (producer != null)  {
            producer.onEvent(this, spaceAvailble);
        }
        return ret;
    }
    
    /**
     * Non-blocking, nonpausing put. 
     * @param eo. If non-null, registers this observer and calls it with an SpaceAvailable event 
     * when there's space.
     * @return buffered message if there's one, or null 
     */
    public boolean put(T amsg, EventSubscriber eo) {
        boolean ret = true; // assume we'll be able to enqueue
        EventSubscriber subscriber;
        synchronized(this) {
            if (amsg == null) {
                throw new NullPointerException("Null message supplied to put");
            }
            if (msg == null) { // space available
                msg = amsg;
                subscriber = sink;
                sink = null;
            } else {
                ret = false;
                // unable to enqueue. Cell is full
                subscriber = null;
                if (eo != null) {
                    srcs.add(eo);
                }
            }
        }
        // notify get's subscriber that something is available
        if (subscriber != null) {
            subscriber.onEvent(this, messageAvailable);
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
    public T get() throws Pausable{
        Task t = Task.getCurrentTask();
        T msg = get(t);
        while (msg == null) {
            Task.pause(this);
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
        while (msg == null) {
            TimerTask tt = new TimerTask() {
                    public void run() {
                        Cell.this.removeMsgAvailableListener(t);
                        t.onEvent(Cell.this, timedOut);
                    }
                };
            Task.timer.schedule(tt, timeoutMillis);
            Task.pause(this);
            tt.cancel();
            if (System.currentTimeMillis() - begin > timeoutMillis) {
                break;
            }
            msg = get(t);
        }
        return msg;
    }
    
    public synchronized void addSpaceAvailableListener(EventSubscriber spcSub) {
            srcs.add(spcSub);
        }

    public synchronized void removeSpaceAvailableListener(EventSubscriber spcSub) {
            srcs.remove(spcSub);
        }


    public synchronized void addMsgAvailableListener(EventSubscriber msgSub) {
            if (sink != null) {
                throw new AssertionError(
                  "Error: A mailbox can not be shared by two consumers.  New = "
                  + msgSub + ", Old = " + sink);
            }
            sink = msgSub;
        }

    public synchronized void removeMsgAvailableListener(EventSubscriber msgSub) {
            if (sink == msgSub) {
                sink = null;
            }
        }

    public boolean putnb(T msg) {
        return put(msg, null);
    }

    public void put(T msg) throws Pausable {
        Task t = Task.getCurrentTask();
        while (!put(msg, t)) {
            Task.pause(this);
        }
    }

    public boolean put(T msg, int timeoutMillis) throws Pausable {
        final Task t = Task.getCurrentTask();
        long begin = System.currentTimeMillis();
        while (!put(msg, t)) {
            TimerTask tt = new TimerTask() {
                    public void run() {
                        Cell.this.removeSpaceAvailableListener(t);
                        t.onEvent(Cell.this, timedOut);
                    }
                };
            Task.timer.schedule(tt, timeoutMillis);
            Task.pause(this);
            if (System.currentTimeMillis() - begin >= timeoutMillis) {
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
                        Cell.this.wait(infiniteWait? 0 : remaining);
                    } catch (InterruptedException ie) {}
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

    public synchronized boolean hasMessage() {
            return msg != null;
        }

    public synchronized boolean hasSpace() {
            return msg == null;
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
     * @param millis.
     *            max wait time
     * @return null if timed out.
     */
    public T getb(final long timeoutMillis) {
        BlockingSubscriber evs = new BlockingSubscriber();
        T msg;
        
        if ((msg = get(evs)) == null) {
            evs.blockingWait(timeoutMillis);
            if (evs.eventRcvd) {
                msg = get(null); // non-blocking get.
                assert msg  != null: "Received event, but message is null";
            } 
        }
        if (msg == null) {
            removeMsgAvailableListener(evs);
        }
        return msg;
    }

    public synchronized String toString() {
            return "id:" + System.identityHashCode(this) + " " + msg;
        }

    // Implementation of PauseReason
    public boolean isValid(Task t) {
        synchronized(this) {
            return (t == sink) || srcs.contains(t);
        } 
    }
}

