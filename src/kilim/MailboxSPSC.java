/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import kilim.concurrent.SPSCQueue;
import kilim.concurrent.VolatileReferenceCell;


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

public class MailboxSPSC<T> implements PauseReason,
		EventPublisher {
	// TODO. Give mbox a config name and id and make monitorable

        SPSCQueue<T> msgs;
    
	final
        VolatileReferenceCell<EventSubscriber> sink = new VolatileReferenceCell<EventSubscriber>
            (null);
	final VolatileReferenceCell
            <EventSubscriber> srcs = new
                VolatileReferenceCell<EventSubscriber>(null);

	// FIX: I don't like this event design. The only good thing is that
	// we don't create new event objects every time we signal a client
	// (subscriber) that's blocked on this mailbox.
	public static final int SPACE_AVAILABLE = 1;
	public static final int MSG_AVAILABLE = 2;
	public static final int TIMED_OUT = 3;

        public static final Event spaceAvailble = new Event(SPACE_AVAILABLE);
        public static final Event messageAvailable = new Event(MSG_AVAILABLE);
	public static final Event timedOut = new Event(TIMED_OUT);

	// DEBUG steuuff
	// To do: move into monitorable stat object
	/*
	 * public int nPut = 0; public int nGet = 0; public int nWastedPuts = 0;
	 * public int nWastedGets = 0;
	 */
	public MailboxSPSC() {
		this(10);
	}

	@SuppressWarnings("unchecked")
	public MailboxSPSC(int initialSize) {
                msgs = new SPSCQueue(initialSize);
	}

	/**
	 * Non-blocking, nonpausing fill.
	 * 
	 * @param eo
	 *            . If non-null, registers this observer and calls it with a
	 *            MessageAvailable event when a put() is done.
	 * @return buffered true if there's one, or up to burst size messages else
	 *         false
	 */
	public boolean fill(EventSubscriber eo, T[] msg) {
		boolean b = msgs.fillnb(msg);
		if (!b) {
			addMsgAvailableListener(eo);
			return false;
		}
		EventSubscriber producer = srcs.getAndSet(null);
		if (producer != null)
			producer.onEvent(this, spaceAvailble);
		return true;
	}
	/**
	 * put a non-null messages from buffer in the mailbox, and pause the calling
	 * task until all the messages put in the mailbox
	 */
	public void put(T[] buf) throws Pausable {
            msgs.putMailbox(buf,this);
	}

	/**
	 * Non-blocking, nonpausing get.
	 * 
	 * @param eo
	 *            . If non-null, registers this observer and calls it with a
	 *            MessageAvailable event when a put() is done.
	 * @return buffered message if there's one, or null
	 */
	public T get(EventSubscriber eo) {
		EventSubscriber producer = null;
		T e = msgs.poll();
		if (e == null) {
			if (eo != null) {
				addMsgAvailableListener(eo);
			}
		}

		producer = getProducer();
		if (producer != null) {
			producer.onEvent(this, spaceAvailble);
		}
		return e;
	}

	public boolean put(T msg, EventSubscriber eo) {
		if (msg == null) {
			throw new NullPointerException("Null is not a valid element");
		}
		EventSubscriber subscriber;
		boolean b = msgs.offer(msg);
		if (!b) {
			if (eo != null) {
				addSpaceAvailableListener(eo);
			}
		}
		subscriber = sink.getAndSet(null);
		if (subscriber != null) {
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
	 * Attempt to put a message, and return true if successful. The thread is
	 * not blocked, nor is the task paused under any circumstance.
	 */
	public boolean putnb(T msg) {
		return put(msg, null);
	}


	public void addSpaceAvailableListener(EventSubscriber spcSub) {
		srcs.set  (spcSub);
	}

	public void removeSpaceAvailableListener(EventSubscriber spcSub) {
		srcs.compareAndSet(spcSub, null);
	}

	public void addMsgAvailableListener(EventSubscriber msgSub) {
		sink.set(msgSub);
	}

	public void removeMsgAvailableListener(EventSubscriber msgSub) {
		sink.compareAndSet(msgSub, null);
	}
        private EventSubscriber getProducer() {
            return srcs.getAndSet(null);
        }
        private boolean srcContains(Task t) {
            return srcs.get()==t;
        }
        private long getSize() {
            return msgs.size();
        }


	/**
	 * put a non-null message in the mailbox, and pause the calling task until
	 * the mailbox has space
	 */

	public void put(T msg) throws Pausable {
		Task t = Task.getCurrentTask();
		while (!put(msg, t)) {
			Task.pause(this);
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
		long time = timeoutMillis;
		while (!put(msg, t)) {
			t.timer.setTimer(time);
			t.scheduler.scheduleTimer(t.timer);
			Task.pause(this);
			t.timer.cancel();
			removeSpaceAvailableListener(t);
			time = timeoutMillis - (System.currentTimeMillis() - begin);
			if (time <= 0) {
				return false;
			}
		}
		return true;
	}

        
        
        
        
        
        
        
        





        
        
        
        public synchronized String toString() {
		return "id:" + System.identityHashCode(this) + " " +
				"numMsgs:" + getSize();
	}

	public void clear() {
		Object value;
		do {
			value = getnb();
		} while (null != value);
	}

	// Implementation of PauseReason
	public boolean isValid(Task t) {
		if (t == sink.get()) {
			return msgs.isEmpty();
		} else if (srcContains(t)) {
			return !msgs.hasSpace();
		} else {
			return false;
		}
	}

	/**
	 * Pausable fill Pause the caller until at least one message is available.
	 *
	 * @throws Pausable
	 */
	public void fill(T[] msg) throws Pausable {
		Task t = Task.getCurrentTask();
		boolean b = fill(t, msg);
		while (!b) {
			Task.pause(this);
			removeMsgAvailableListener(t);
			b = fill(t, msg);
		}
	}

        public void subscribe(boolean avail,Task t,boolean set) throws Pausable {
            EventSubscriber subscriber = avail ? sink.getAndSet(null) : null;
            if (subscriber != null)
                subscriber.onEvent(this,messageAvailable);
            if (set) {
                srcs.set(t);
                Task.pause(this);
                removeSpaceAvailableListener(t);
            }
        }
        
}

