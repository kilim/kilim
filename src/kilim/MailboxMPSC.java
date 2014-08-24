/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.LockSupport;

import org.objectweb.asm.tree.IntInsnNode;


/**
 * This is a typed buffer that supports single producers and a single consumer.
 * It is the basic construct used for tasks to interact and synchronize with
 * each other (as opposed to direct java calls or static member variables).
 * put() and get() are the two essential functions.
 * 
 * We use the term "block" to mean thread block, and "pause" to mean fiber
 * pausing. The suffix "nb" on some methods (such as getnb()) stands for
 * non-blocking. Both put() and get() have blocking and non-blocking variants in
 * the form of putb(), putnb
 */

public class MailboxMPSC<T> implements PauseReason, EventPublisher {
    // TODO. Give mbox a config name and id and make monitorable
    MPSCQueue<T> msgs;
    VolatileReferenceCell<EventSubscriber> sink = new VolatileReferenceCell<EventSubscriber>();
    Deque<EventSubscriber> srcs = new ConcurrentLinkedDeque<EventSubscriber>();

    // FIX: I don't like this event design. The only good thing is that
    // we don't create new event objects every time we signal a client
    // (subscriber) that's blocked on this mailbox.
    public static final int SPACE_AVAILABLE = 1;
    public static final int MSG_AVAILABLE = 2;
    public static final int TIMED_OUT = 3;

    public static final Event spaceAvailble = new Event(MSG_AVAILABLE);
    public static final Event messageAvailable = new Event(SPACE_AVAILABLE);
    public static final Event timedOut = new Event(TIMED_OUT);

    // DEBUG steuuff
    // To do: move into monitorable stat object
    /*
     * public int nPut = 0; public int nGet = 0; public int nWastedPuts = 0;
     * public int nWastedGets = 0;
     */
    public MailboxMPSC() {
        this(10);
    }

    @SuppressWarnings("unchecked")
    public MailboxMPSC(int initialSize) {
        if (initialSize < 1)
            throw new IllegalArgumentException("initialSize: " + initialSize
                    + " cannot be less then 1");
        System.out.println(initialSize);
        msgs = new MPSCQueue<T>(initialSize);

    }

    /**
     * Non-blocking, nonpausing get.
     * 
     * @param eo
     *            . If non-null, registers this observer and calls it with a
     *            MessageAvailable event when a put() is done.
     * @return buffered message if there's one, or null
     */

    public T getMsg() {
        T msg = msgs.poll();
        return msg;
    }

    public T get(EventSubscriber eo) {
        EventSubscriber producer = null;
        T e = getMsg();
        if (e == null) {
            if (eo != null) {
                addMsgAvailableListener(eo);
            }
        }

        if (srcs.size() > 0) {
            producer = srcs.poll();
        }
        if (producer != null) {
            producer.onEvent(this, spaceAvailble);
        }
        return e;
    }

    /**
     * Non-blocking, nonpausing put.
     * 
     * @param eo
     *            . If non-null, registers this observer and calls it with an
     *            SpaceAvailable event when there's space.
     * @return buffered message if there's one, or null
     * @see #putnb(Object)
     * @see #putb(Object)
     */
    public boolean putMsg(T msg) {
        return msgs.offer(msg);
    }

    public boolean put(T msg, EventSubscriber eo) {
        if (msg == null) {
            throw new NullPointerException("Null is not a valid element");
        }
        EventSubscriber subscriber;
        boolean b = putMsg(msg);
        if (!b) {
            if (eo != null) {
                srcs.offer(eo);
            }
        }
        subscriber = sink.get();
        if (subscriber != null) {
            removeMsgAvailableListener(subscriber);
            subscriber.onEvent(this, messageAvailable);
        }

        return b;
    }

    /**
     * Get, don't pause or block.
     * 
     * @return stored message, or null if no message found.
     */
    public T getnb() {
        return get(null);
    }

    public void fill(T[] buf) {

        for (int i = 0; i < buf.length; i++) {
            buf[i] = getnb();
            if (buf[i] == null) {
                break;
            }
        }
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
     * @return non-null message, or null if timed out.
     * @throws Pausable
     */
    public T get(long timeoutMillis) throws Pausable {
        final Task t = Task.getCurrentTask();
        T msg = get(t);
        long end = System.currentTimeMillis() + timeoutMillis;
        while (msg == null) {
            TimerTask tt = new TimerTask() {
                public void run() {
                    MailboxMPSC.this.removeMsgAvailableListener(t);
                    t.onEvent(MailboxMPSC.this, timedOut);
                }
            };
            Task.timer.schedule(tt, timeoutMillis);
            Task.pause(this);
            tt.cancel();
            removeMsgAvailableListener(t);
            msg = get(t);

            timeoutMillis = end - System.currentTimeMillis();
            if (timeoutMillis <= 0) {
                removeMsgAvailableListener(t);
                break;
            }
        }
        return msg;
    }

    /**
     * Block caller until at least one message is available.
     * 
     * @throws Pausable
     */
    public void untilHasMessage() throws Pausable {
        while (hasMessage(Task.getCurrentTask()) == false) {
            Task.pause(this);
        }
    }

    /**
     * Block caller until <code>num</code> messages are available.
     * 
     * @param num
     * @throws Pausable
     */
    public void untilHasMessages(int num) throws Pausable {
        while (hasMessages(num, Task.getCurrentTask()) == false) {
            Task.pause(this);
        }
    }

    /**
     * Block caller (with timeout) until a message is available.
     * 
     * @return non-null message.
     * @throws Pausable
     */
    public boolean untilHasMessage(long timeoutMillis) throws Pausable {
        final Task t = Task.getCurrentTask();
        boolean has_msg = hasMessage(t);
        long end = System.currentTimeMillis() + timeoutMillis;
        while (has_msg == false) {
            TimerTask tt = new TimerTask() {
                public void run() {
                    MailboxMPSC.this.removeMsgAvailableListener(t);
                    t.onEvent(MailboxMPSC.this, timedOut);
                }
            };
            Task.timer.schedule(tt, timeoutMillis);
            Task.pause(this);
            tt.cancel();
            has_msg = hasMessage(t);
            timeoutMillis = end - System.currentTimeMillis();
            if (timeoutMillis <= 0) {
                removeMsgAvailableListener(t);
                break;
            }
        }
        return has_msg;
    }

    /**
     * Block caller (with timeout) until <code>num</code> messages are
     * available.
     * 
     * @param num
     * @param timeoutMillis
     * @return Message or <code>null</code> on timeout
     * @throws Pausable
     */
    public boolean untilHasMessages(int num, long timeoutMillis)
            throws Pausable {
        final Task t = Task.getCurrentTask();
        final long end = System.currentTimeMillis() + timeoutMillis;

        boolean has_msg = hasMessages(num, t);
        while (has_msg == false) {
            TimerTask tt = new TimerTask() {
                public void run() {
                    MailboxMPSC.this.removeMsgAvailableListener(t);
                    t.onEvent(MailboxMPSC.this, timedOut);
                }
            };
            Task.timer.schedule(tt, timeoutMillis);
            Task.pause(this);
            if (!tt.cancel()) {
                removeMsgAvailableListener(t);
            }

            has_msg = hasMessages(num, t);
            timeoutMillis = end - System.currentTimeMillis();
            if (!has_msg && timeoutMillis <= 0) {
                removeMsgAvailableListener(t);
                break;
            }
        }
        return has_msg;
    }

    public boolean hasMessage(Task eo) {
        boolean has_msg;
        synchronized (this) {
            int n = (int) msgs.size();
            if (n > 0) {
                has_msg = true;
            } else {
                has_msg = false;
                addMsgAvailableListener(eo);
            }
        }
        return has_msg;
    }

    public boolean hasMessages(int num, Task eo) {
        boolean has_msg;
        synchronized (this) {
            int n = (int) msgs.size();
            if (n >= num) {
                has_msg = true;
            } else {
                has_msg = false;
                addMsgAvailableListener(eo);
            }
        }
        return has_msg;
    }

    /**
     * Takes an array of mailboxes and returns the index of the first mailbox
     * that has a message. It is possible that because of race conditions, an
     * earlier mailbox in the list may also have received a message.
     */
    // TODO: need timeout variant
    public static int select(MailboxMPSC... mboxes) throws Pausable {
        while (true) {
            for (int i = 0; i < mboxes.length; i++) {
                if (mboxes[i].hasMessage()) {
                    return i;
                }
            }
            Task t = Task.getCurrentTask();
            EmptySet_MsgAvListenerMpSc pauseReason = new EmptySet_MsgAvListenerMpSc(
                    t, mboxes);
            for (int i = 0; i < mboxes.length; i++) {
                mboxes[i].addMsgAvailableListener(pauseReason);
            }
            Task.pause(pauseReason);
            for (int i = 0; i < mboxes.length; i++) {
                mboxes[i].removeMsgAvailableListener(pauseReason);
            }
        }
    }

    public synchronized void addSpaceAvailableListener(EventSubscriber spcSub) {
        srcs.offer(spcSub);
    }

    public synchronized void removeSpaceAvailableListener(EventSubscriber spcSub) {
        srcs.remove(spcSub);
    }

    public synchronized void addMsgAvailableListener(EventSubscriber msgSub) {
        EventSubscriber sink1 = sink.get();
        if (sink1 != null && sink1 != msgSub) {
            throw new AssertionError(
                    "Error: A mailbox can not be shared by two consumers.  New = "
                            + msgSub + ", Old = " + sink1);
        }
        sink.set(msgSub);
    }

    public synchronized void removeMsgAvailableListener(EventSubscriber msgSub) {
        if (sink.get() == msgSub) {
            sink.set(null);
        }
    }

    /**
     * Attempt to put a message, and return true if successful. The thread is
     * not blocked, nor is the task paused under any circumstance.
     */
    public boolean putnb(T msg) {
        return put(msg, null);
    }

    /**
     * put a non-null message in the mailbox, and pause the calling task until
     * the mailbox has space
     */

    public void put(T msg) throws Pausable {
        Task t = Task.getCurrentTask();
        while (!put(msg, t)) {
            Task.pause(this);
            System.out.println("paused");
            removeSpaceAvailableListener(t);
        }
    }

    /**
     * put a non-null message in the mailbox, and pause the calling task for
     * timeoutMillis if the mailbox is full.
     */

    public boolean put(T msg, int timeoutMillis) throws Pausable {
        final Task t = Task.getCurrentTask();
        long begin = System.currentTimeMillis();
        while (!put(msg, t)) {
            TimerTask tt = new TimerTask() {
                public void run() {
                    MailboxMPSC.this.removeSpaceAvailableListener(t);
                    t.onEvent(MailboxMPSC.this, timedOut);
                }
            };
            Task.timer.schedule(tt, timeoutMillis);
            Task.pause(this);
            removeSpaceAvailableListener(t);
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
            synchronized (MailboxMPSC.this) {
                eventRcvd = true;
                MailboxMPSC.this.notify();
            }
        }

        public void blockingWait(final long timeoutMillis) {
            long start = System.currentTimeMillis();
            long remaining = timeoutMillis;
            boolean infiniteWait = timeoutMillis == 0;
            synchronized (MailboxMPSC.this) {
                while (!eventRcvd && (infiniteWait || remaining > 0)) {
                    try {
                        MailboxMPSC.this.wait(infiniteWait ? 0 : remaining);
                    } catch (InterruptedException ie) {
                    }
                    long elapsed = System.currentTimeMillis() - start;
                    remaining -= elapsed;
                }
            }
        }
    }

    /**
     * put a non-null message in the mailbox, and block the calling thread for
     * timeoutMillis if the mailbox is full.
     */
    public void putb(T msg, final long timeoutMillis) {
        BlockingSubscriber evs = new BlockingSubscriber();
        if (!put(msg, evs)) {
            evs.blockingWait(timeoutMillis);
        }
        if (!evs.eventRcvd) {
            removeSpaceAvailableListener(evs);
        }
    }

    public int size() {
        return (int) msgs.size();
    }

    public boolean hasMessage() {

        return (msgs.peek() != null);
    }

    public boolean hasSpace() {
        return msgs.hasSpace();
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

    public synchronized String toString() {
        return "id:" + System.identityHashCode(this) + " " +
        // DEBUG "nGet:" + nGet + " " +
        // "nPut:" + nPut + " " +
        // "numWastedPuts:" + nWastedPuts + " " +
        // "nWastedGets:" + nWastedGets + " " +
                "numMsgs:" + msgs.size();
    }

    // Implementation of PauseReason
    public synchronized boolean isValid(Task t) {
        if (t == sink.get()) {
            return !hasMessage();
        } else if (srcs.contains(t)) {
            return !hasSpace();
        } else {
            return false;
        }
    }

}

class EmptySet_MsgAvListenerMpSc implements PauseReason, EventSubscriber {
    final Task task;
    final MailboxMPSC<?>[] mbxs;

    EmptySet_MsgAvListenerMpSc(Task t, MailboxMPSC<?>[] mbs) {
        task = t;
        mbxs = mbs;
    }

    public boolean isValid(Task t) {
        // The pauseReason is true (there is valid reason to continue
        // pausing) if none of the mboxes have any elements
        for (MailboxMPSC<?> mb : mbxs) {
            if (mb.hasMessage())
                return false;
        }
        return true;
    }

    public void onEvent(EventPublisher ep, Event e) {
        for (MailboxMPSC<?> m : mbxs) {
            if (m != ep) {
                ((MailboxMPSC<?>) ep).removeMsgAvailableListener(this);
            }
        }
        task.resume();
    }

    public void cancel() {
        for (MailboxMPSC<?> mb : mbxs) {
            mb.removeMsgAvailableListener(this);
        }
    }
  
}

//Referred Nitasan work
abstract class MPSCQueueL0Pad {
    public long p00, p01, p02, p03, p04, p05, p06, p07;
    public long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MPSCQueueColdFields<E> extends MPSCQueueL0Pad {
    protected static final int BUFFER_PAD = 64; //to pad queue ends
    protected static final int SPARSE_SHIFT = Integer.getInteger(
            "sparse.shift", 0);  //pad each element of queue
    protected final int capacity;
    protected final long mask;
    protected final E[] buffer;

    @SuppressWarnings("unchecked")
    public MPSCQueueColdFields(int capacity) {
        if (isPowerOf2(capacity)) {
            this.capacity = capacity;
        } else {
            this.capacity = findNextPositivePowerOfTwo(capacity);
        }
        mask = this.capacity - 1;
        // pad data on either end with some empty slots.
        buffer = (E[]) new Object[(this.capacity << SPARSE_SHIFT) + BUFFER_PAD
                * 2];
    }
    public static int findNextPositivePowerOfTwo(final int value) {
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }
    public static boolean isPowerOf2(final int value){
        return (value & (value-1)) == 0;
    }
}
abstract class MPSCQueueL1Pad<E> extends MPSCQueueColdFields<E> {
    public long p10, p11, p12, p13, p14, p15, p16;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public MPSCQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class MPSCQueueTailField<E> extends MPSCQueueL1Pad<E> {
    protected volatile long tail;

    public MPSCQueueTailField(int capacity) {
        super(capacity);
    }
}

abstract class MPSCQueueL2Pad<E> extends MPSCQueueTailField<E> {
    public long p20, p21, p22, p23, p24, p25, p26;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public MPSCQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class MPSCQueueHeadField<E> extends MPSCQueueL2Pad<E> {
    protected long head;

    public MPSCQueueHeadField(int capacity) {
        super(capacity);
    }
}

abstract class MPSCQueueL3Pad<E> extends MPSCQueueHeadField<E> {
    protected final static long TAIL_OFFSET;
    protected final static long HEAD_OFFSET;
    protected static final long ARRAY_BASE;
    protected static final int ELEMENT_SHIFT;
    static {
        try {
            TAIL_OFFSET = UnsafeAccess.UNSAFE
                    .objectFieldOffset(MPSCQueueTailField.class
                            .getDeclaredField("tail"));
            HEAD_OFFSET = UnsafeAccess.UNSAFE
                    .objectFieldOffset(MPSCQueueHeadField.class
                            .getDeclaredField("head"));
            final int scale = UnsafeAccess.UNSAFE
                    .arrayIndexScale(Object[].class);
            if (4 == scale) {
                ELEMENT_SHIFT = 2 + SPARSE_SHIFT;
            } else if (8 == scale) {
                ELEMENT_SHIFT = 3 + SPARSE_SHIFT;
            } else {
                throw new IllegalStateException("Unknown pointer size");
            }
            // Including the buffer pad in the array base offset
            ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(Object[].class)
                    + (BUFFER_PAD << (ELEMENT_SHIFT - SPARSE_SHIFT));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    public long p40, p41, p42, p43, p44, p45, p46;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public MPSCQueueL3Pad(int capacity) {
        super(capacity);
    }
}

class MPSCQueue<E> extends MPSCQueueL3Pad<E> {
    private static final BackOffStrategy CAS_BACKOFF = BackOffStrategy
            .getStrategy("cas.backoff", BackOffStrategy.SPIN); //set property to change backoff strategy

    public MPSCQueue(final int capacity) {
        super(capacity);
    }

    private long getHead() {
        return UnsafeAccess.UNSAFE.getLongVolatile(this, HEAD_OFFSET);
    }

    private void lazySetHead(long l) {
        UnsafeAccess.UNSAFE.putOrderedLong(this, HEAD_OFFSET, l);
    }

    private long getTail() {
        return UnsafeAccess.UNSAFE.getLongVolatile(this, TAIL_OFFSET);
    }

    private boolean casTail(long expect, long newValue) {
        return UnsafeAccess.UNSAFE.compareAndSwapLong(this, TAIL_OFFSET,
                expect, newValue);
    }

    public boolean add(final E e) {
        if (offer(e)) {
            return true;
        }
        throw new IllegalStateException("Queue is full");
    }

    private long elementOffsetInBuffer(long index) {
        return ARRAY_BASE + ((index & mask) << ELEMENT_SHIFT);
    }

    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        long currentTail;
        for (int missCount = 0;;) {
            currentTail = getTail();
            final long wrapPoint = currentTail - capacity;
            if (getHead() <= wrapPoint) {
                return false;
            }
            if (casTail(currentTail, currentTail + 1)) {
                break;
            } else {
                missCount = CAS_BACKOFF.backoff(missCount);

            }
        }

        UnsafeAccess.UNSAFE.putOrderedObject(buffer,
                elementOffsetInBuffer(currentTail), e);
        return true;
    }

    public boolean hasSpace() {
        long currentTail;
        currentTail = getTail();
        @SuppressWarnings("unchecked")
        final E e = (E) UnsafeAccess.UNSAFE.getObjectVolatile(buffer,
                elementOffsetInBuffer(currentTail));
        if (e == null) {
            return true;
        } else {
            return false;
        }

    }

    public E poll() {
        final long offset = elementOffsetInBuffer(head);
        @SuppressWarnings("unchecked")
        final E e = (E) UnsafeAccess.UNSAFE.getObjectVolatile(buffer, offset);
        if (null == e) {
            return null;
        }
        UnsafeAccess.UNSAFE.putObject(buffer, offset, null);
        lazySetHead(head + 1);
        return e;
    }

    public E remove() {
        final E e = poll();
        if (null == e) {
            throw new NoSuchElementException("Queue is empty");
        }

        return e;
    }

    public E element() {
        final E e = peek();
        if (null == e) {
            throw new NoSuchElementException("Queue is empty");
        }

        return e;
    }

    public E peek() {
        long currentHead = getHead();
        return getElement(currentHead);
    }

    @SuppressWarnings("unchecked")
    private E getElement(long index) {
        return (E) kilim.UnsafeAccess.UNSAFE.getObject(buffer,
                elementOffsetInBuffer(index));
    }

    public int size() {
        long currentConsumerIndexBefore;
        long currentProducerIndex;
        long currentConsumerIndexAfter = getHead();

        do {
            currentConsumerIndexBefore = currentConsumerIndexAfter;
            currentProducerIndex = getHead();
            currentConsumerIndexAfter = getTail();
        } while (currentConsumerIndexBefore != currentConsumerIndexAfter);

        return (int) (currentProducerIndex - currentConsumerIndexBefore);

    }
    public enum BackOffStrategy {
        SPIN {
            public int backoff(int called) {
                return ++called;
            }
        },
        YIELD{
            public int backoff(int called) {
                Thread.yield();
                return called++;
            }
        },
        PARK{
            public int backoff(int called) {
                LockSupport.parkNanos(1);
                
                return called++;
            }
        },
        SPIN_YIELD {
            public int backoff(int called) {
                if(called>1000)
                    Thread.yield();
                return called++;
                
            }
        };
        public abstract int backoff(int called);
        public static BackOffStrategy getStrategy(String propertyName, BackOffStrategy defaultS){
            try{
                return BackOffStrategy.valueOf(System.getProperty(propertyName));
            }
            catch(Exception e){
                return defaultS;
            }
        }
    }
    
}
