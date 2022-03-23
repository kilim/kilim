/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.util.LinkedList;

/**
 * This is a typed buffer that supports multiple producers and a single
 * consumer. It is the basic construct used for tasks to interact and
 * synchronize with each other (as opposed to direct java calls or static member
 * variables). put() and get() are the two essential functions.
 * 
 * We use the term "block" to mean thread block, and "pause" to mean
 * fiber pausing. The suffix "nb" on some methods (such as getnb())
 * stands for non-blocking. Both put() and get() have blocking and
 * non-blocking variants in the form of putb(), putnb
 */

public class Mailbox<T> implements PauseReason, EventPublisher {
    // TODO. Give mbox a config name and id and make monitorable
    T[] msgs;
    private int iprod = 0; // producer index
    private int icons = 0; // consumer index;
    private int numMsgs = 0;
    private int maxMsgs = 300;
    EventSubscriber sink;
    
    // FIX: I don't like this event design. The only good thing is that
    // we don't create new event objects every time we signal a client
    // (subscriber) that's blocked on this mailbox.
    public static final int SPACE_AVAILABLE = 1;
    public static final int MSG_AVAILABLE = 2;
    public static final int TIMED_OUT = 3;
    public static final Event spaceAvailble = new Event(SPACE_AVAILABLE);
    public static final Event messageAvailable = new Event(MSG_AVAILABLE);
    public static final Event timedOut = new Event(TIMED_OUT);
    
    LinkedList<EventSubscriber> srcs = new LinkedList<EventSubscriber>();

    // DEBUG stuff
    // To do: move into monitorable stat object
    /*
     * public int nPut = 0; public int nGet = 0; public int nWastedPuts = 0;
     * public int nWastedGets = 0;
     */
    public Mailbox() {
        this(10);
    }

    public Mailbox(int initialSize) {
        this(initialSize, Integer.MAX_VALUE);
    }

    @SuppressWarnings("unchecked")
    public Mailbox(int initialSize, int maxSize) {
        if (initialSize > maxSize)
            throw new IllegalArgumentException("initialSize: " + initialSize
                    + " cannot exceed maxSize: " + maxSize);
        msgs = (T[]) new Object[initialSize];
        maxMsgs = maxSize;
    }

    /**
     * Non-blocking, nonpausing get. 
     * //@param eo. If non-null, registers this observer and calls it with a MessageAvailable event when
     *  a put() is done.
     * @return buffered message if there's one, or null 
     */
    public T get(EventSubscriber eo) {
        T msg;
        EventSubscriber producer = null;
        synchronized(this) {
            int n = numMsgs;
            if (n > 0) {
                int ic = icons;
                msg = msgs[ic]; msgs[ic]=null;
                icons = (ic + 1) % msgs.length;
                numMsgs = n - 1;
                
                if (srcs.size() > 0) {
                    producer = srcs.poll();
                }
            } else {
                msg = null;
                addMsgAvailableListener(eo);
            }
        }
        if (producer != null)  {
            producer.onEvent(this, spaceAvailble);
        }
        return msg;
    }
    
    /**
     * Non-blocking, nonpausing put. 
     * //@param eo. If non-null, registers this observer and calls it with an SpaceAvailable event
     * when there's space.
     * @return buffered message if there's one, or null
     * @see #nonBlockingPut(Object)
     * @see #blockingPut(Object)
     */
    @SuppressWarnings("unchecked")
    public boolean put(T msg, EventSubscriber eo) {
        boolean ret = true; // assume we will be able to enqueue
        EventSubscriber subscriber;
        synchronized(this) {
            if (msg == null) {
                throw new NullPointerException("Null message supplied to put");
            }
            int ip = iprod;
            int ic = icons;
            int n = numMsgs;
            if (n == msgs.length) {
                assert ic == ip : "numElements == msgs.length && ic != ip";
                if (n < maxMsgs) {
                    T[] newmsgs = (T[]) new Object[Math.min(n * 2, maxMsgs)];
                    System.arraycopy(msgs, ic, newmsgs, 0, n - ic);
                    if (ic > 0) {
                        System.arraycopy(msgs, 0, newmsgs, n - ic, ic);
                    }
                    msgs = newmsgs;
                    ip = n;
                    ic = 0;
                } else {
                    ret = false;
                }
            }
            if (ret) {
                numMsgs = n + 1;
                msgs[ip] = msg;
                iprod = (ip + 1) % msgs.length;
                icons = ic;
                subscriber = sink;
                sink = null;
            } else {
                subscriber = null;
                // unable to enqueue
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
    public T nonBlockingGet() {
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
        long begin = System.currentTimeMillis();
        long time = timeoutMillis;
        while (msg == null) {
            t.timer.setTimer(time);
            t.scheduler.scheduleTimer(t.timer);
            Task.pause(this);
            t.timer.cancel();
            removeMsgAvailableListener(t);
            time = timeoutMillis - (System.currentTimeMillis() - begin);
            if (time <= 0) {
                break;
            }
            msg = get(t);
        }
        return msg;
    }
    
    
    /**
     * Block caller until at least one message is available.
     * @throws Pausable
     */
	public void untilHasMessage() throws Pausable {
		while (hasMessage(Task.getCurrentTask()) == false) {
			Task.pause(this);
		}
	}

	/**
	 * Block caller until <code>num</code> messages are available.
	 * @param num
	 * @throws Pausable
	 */
	public void untilHasMessages(int num) throws Pausable {
		while (hasMessages(num, Task.getCurrentTask()) == false) {
			Task.pause(this);
		}
	}

	public boolean hasMessage(Task eo) {
		boolean has_msg;
		synchronized (this) {
			int n = numMsgs;
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
			int n = numMsgs;
			if (n >= num) {
				has_msg = true;
			} else {
				has_msg = false;
				addMsgAvailableListener(eo);
			}
		}
		return has_msg;
	}


	public T peek(int idx) {
		assert idx >= 0 : "negative index";
		T msg;
		synchronized (this) {
			int n = numMsgs;
			if (idx < n) {
				int ic = icons;
				msg = msgs[(ic + idx) % msgs.length];

				assert msg != null : "peeked null message!";
			} else {
				msg = null;
			}
		}
		return msg;
	}

	public T remove(final int idx) {
		assert idx >= 0 : "negative index";
		T msg;
		synchronized (this) {
			int n = numMsgs;
			assert idx < numMsgs;
			if (idx < n) {
				int ic = icons;
				int mlen = msgs.length;
				msg = msgs[(ic + idx) % mlen];
				for (int i = idx; i > 0; i--) {
					msgs[(ic + i) % mlen] = msgs[(ic + i - 1) % mlen];
				}
				msgs[icons] = null;
				numMsgs -= 1;
				icons = (icons + 1) % mlen;
			} else {
				throw new IllegalStateException();
			}
		}
		return msg;
	}

	public synchronized Object[] messages() {
		synchronized (this) {
			Object[] result = new Object[numMsgs];
			for (int i = 0; i < numMsgs; i++) {
				result[i] = msgs[(icons + i) % msgs.length];
			}
			return result;
		}

	}


    /**
     * Takes an array of mailboxes and returns the index of the first mailbox
     * that has a message. It is possible that because of race conditions, an
     * earlier mailbox in the list may also have received a message.
     */
    // TODO: need timeout variant
    @SuppressWarnings("unchecked")
    public static int select(Mailbox... mboxes) throws Pausable {
        while (true) {
            for (int i = 0; i < mboxes.length; i++) {
                if (mboxes[i].hasMessage()) {
                    return i;
                }
            }
            Task t = Task.getCurrentTask();
            EmptySet_MsgAvListener pauseReason = 
                    new EmptySet_MsgAvListener(t, mboxes);
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
        srcs.add(spcSub);
    }

    public synchronized void removeSpaceAvailableListener(EventSubscriber spcSub) {
        srcs.remove(spcSub);
    }


    public synchronized void addMsgAvailableListener(EventSubscriber msgSub) {
        if (sink != null && sink != msgSub) {
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

    /**
     * Attempt to put a message, and return true if successful. The thread is not blocked, nor is the task
     * paused under any circumstance. 
     */
    public boolean nonBlockingPut(T msg) {
        return put(msg, null);
    }

    /**
     * put a non-null message in the mailbox, and pause the calling task  until the
     * mailbox has space
     */

    public void put(T msg) throws Pausable {
        Task t = Task.getCurrentTask();
        while (!put(msg, t)) {
            Task.pause(this);
            removeSpaceAvailableListener(t);
        }
    }

    /**
     * put a non-null message in the mailbox, and pause the calling task  for timeoutMillis
     * if the mailbox is full. 
     */

    public boolean put(T msg, int timeoutMillis) throws Pausable {
        final Task t = Task.getCurrentTask();
        long begin = System.currentTimeMillis();
        long time = timeoutMillis;
        while (!put(msg,t)) {
            t.timer.setTimer(time);
            t.scheduler.scheduleTimer(t.timer);
            Task.pause(this);
            t.timer.cancel();
            removeSpaceAvailableListener(t);
            time = timeoutMillis-(System.currentTimeMillis()-begin);
            if (time<=0)
                return false;
        }
        return true;
    }
    
    public void blockingPut(T msg) {
        blockingPut(msg, 0 /* infinite wait */);
    }

    public class BlockingSubscriber implements EventSubscriber {
        public volatile boolean eventRcvd = false;
        private long current = -1;
        private long tom;
        private long start = 0;

        public BlockingSubscriber(long tom) {
            this.tom = tom;
            if (tom > 0)
                start = current = System.currentTimeMillis();
        }
        
        public void onEvent(EventPublisher ep, Event e) {
            synchronized (Mailbox.this) {
                eventRcvd = true;
                Mailbox.this.notify();
            }
        }
        /** wait for either a timeout or event, returning true if the timeout occurred */        
        public boolean blockingWait() {
            long fin = start + tom;
            synchronized (Mailbox.this) {
                while (!eventRcvd && current < fin) {
                    try {
                        Mailbox.this.wait(tom==0 ? 0 : fin-current);
                    }
                    catch (InterruptedException ie) {}
                    if (tom > 0)
                        current = System.currentTimeMillis();
                }
                if (!eventRcvd)
                    removeSpaceAvailableListener(this);
            }
            return current < fin;
        }
    }
    
    
    /**
     * put a non-null message in the mailbox, and block the calling thread for timeoutMillis
     * if the mailbox is full
     * @return true if the message was successfully put in the mailbox
     */
    public boolean blockingPut(T msg, long timeoutMillis) {
        BlockingSubscriber evs = new BlockingSubscriber(timeoutMillis);
        boolean success;
        while (!(success = put(msg, evs)) && evs.blockingWait()) {}
        return success;
    }

    public synchronized int size() {
        return numMsgs;
    }
    
    public synchronized boolean hasMessage() {
        return numMsgs > 0;
    }

    public synchronized boolean hasSpace() {
        return (maxMsgs - numMsgs) > 0;
    }

    /**
     * retrieve a message, blocking the thread indefinitely. Note, this is a
     * heavyweight block, unlike #get() that pauses the Fiber but doesn't block
     * the thread.
     */

    public T blockingGet() {
        return blockingGet(0);
    }

    /**
     * retrieve a msg, and block the Java thread for the time given.
     * 
     * //@param millis. max wait time
     * @return null if timed out.
     */
    public T blockingGet(final long timeoutMillis) {
        BlockingSubscriber evs = new BlockingSubscriber(timeoutMillis);
        T msg;
        while ((msg = get(evs))==null && evs.blockingWait()) {}
        return msg;
    }

    public synchronized String toString() {
        return "id:" + System.identityHashCode(this) + " " +
        // DEBUG "nGet:" + nGet + " " +
                // "nPut:" + nPut + " " +
                // "numWastedPuts:" + nWastedPuts + " " +
                // "nWastedGets:" + nWastedGets + " " +
                "numMsgs:" + numMsgs;
    }

    // Implementation of PauseReason
    public boolean isValid(Task t) {
        synchronized(this) {
            return ((t == sink) || srcs.contains(t)) && ! t.checkTimeout();
        }
    }
}

class EmptySet_MsgAvListener implements PauseReason, EventSubscriber {
    final Task task;
    final Mailbox<?>[] mbxs;

    EmptySet_MsgAvListener(Task t, Mailbox<?>[] mbs) {
        task = t;
        mbxs = mbs;
    }

    public boolean isValid(Task t) {
        // The pauseReason is true (there is valid reason to continue
        // pausing) if none of the mboxes have any elements
        for (Mailbox<?> mb : mbxs) {
            if (mb.hasMessage())
                return false;
        }
        return true;
    }

    public void onEvent(EventPublisher ep, Event e) {
        for (Mailbox<?> m : mbxs) {
            if (m != ep) {
                ((Mailbox<?>)ep).removeMsgAvailableListener(this);
            }
        }
        task.resume();
    }

    public void cancel() {
        for (Mailbox<?> mb : mbxs) {
            mb.removeMsgAvailableListener(this);
        }
    }
}
