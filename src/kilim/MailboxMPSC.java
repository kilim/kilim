/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import kilim.concurrent.MPSCQueue;
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

public class MailboxMPSC<T> implements PauseReason, EventPublisher {
	// TODO. Give mbox a config name and id and make monitorable

        MPSCQueue<T> msgs;
    
	VolatileReferenceCell<EventSubscriber> sink = new VolatileReferenceCell<EventSubscriber>
            ();
	Queue
            <EventSubscriber> srcs = new 
                ConcurrentLinkedQueue<EventSubscriber>();

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
	public MailboxMPSC() {
		this(10);
	}

	@SuppressWarnings("unchecked")
	public MailboxMPSC(int initialSize) {
		msgs = new MPSCQueue(initialSize);
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

        synchronized         
	public void addSpaceAvailableListener(EventSubscriber spcSub) {
		srcs.offer(spcSub);
	}
        synchronized 
	public void removeSpaceAvailableListener(EventSubscriber spcSub) {
		srcs.remove(spcSub);
	}
        synchronized 
	public void addMsgAvailableListener(EventSubscriber msgSub) {
		sink.set(msgSub);
	}
        synchronized 
	public void removeMsgAvailableListener(EventSubscriber msgSub) {
		sink.set(null);
	}
        private EventSubscriber getProducer() {
            return srcs.poll();
        }
        private boolean srcContains(Task t) {
            return srcs.contains(t);
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

        
        
        
        
        

        synchronized 
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

	public void fill(T[] msg) {
		for (int i = 0; i < msg.length; i++) {
			msg[i] = getnb();
			if (msg[i] == null) {
				break;
			}
		}
	}

}

