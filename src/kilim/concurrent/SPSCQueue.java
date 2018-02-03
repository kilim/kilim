// Copyright 2014 nilangshah - offered under the terms of the MIT License

package kilim.concurrent;

//"Inspired directly by Martin's work --> https://github.com/mjpt777/examples"

import kilim.MailboxSPSC;
import kilim.Pausable;
import kilim.Task;

public class SPSCQueue<T> {
	protected T[] buffer;
	public final VolatileLongCell tail = new VolatileLongCell(0L);
	public final VolatileLongCell head = new VolatileLongCell(0L);

	protected final int mask;

	public static class PaddedLong {
		public volatile long p11, p21, p31, p41, p51, p61;
		public long value = 0;
		public volatile long p1, p2, p3, p4, p5, p6;
	}

	protected final PaddedLong tailCache = new PaddedLong();
	protected final PaddedLong headCache = new PaddedLong();

	@SuppressWarnings("unchecked")
	public SPSCQueue(int initialSize) {
		if (initialSize < 1)
			throw new IllegalArgumentException("initialSize: " + initialSize
					+ " cannot be less then 1");
		initialSize = findNextPositivePowerOfTwo(initialSize); // Convert
																// mailbox size
																// to power of 2
		buffer = (T[]) new Object[initialSize];
		mask = initialSize - 1;
	}

	public static int findNextPositivePowerOfTwo(final int value) {
		return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
	}

	public T poll() {
		T msg = null;
		final long currentHead = head.get();
		boolean flag = false;
		if (currentHead >= tailCache.value) {
			tailCache.value = tail.get();
			if (currentHead >= tailCache.value) {
				return null;
			}
		}
		if (!flag) {
			final int index = (int) currentHead & mask;
			msg = buffer[index];
			buffer[index] = null;
			head.lazySet(currentHead + 1);
		}
		return msg;
	}

	public boolean offer(T msg) {
		if (msg == null) {
			throw new NullPointerException("Null is not a valid element");
		}
		final long currentTail = tail.get();
		final long wrapPoint = currentTail - buffer.length;
		if (headCache.value <= wrapPoint) {
			headCache.value = head.get();
			if (headCache.value <= wrapPoint) {

				return false;
			}
		}
		buffer[(int) currentTail & mask] = msg;
		tail.lazySet(currentTail + 1);
		return true;
	}
	public void putMailbox(T[] buf,MailboxSPSC mb) throws Pausable {
		long currentTail = tail.get();
		int n = buf.length;
		for (int i = 0; i < n; i++) {
			if (buf[i] == null) {
				throw new NullPointerException("Null is not a valid element");
			}
		}
		int count = 0;
		Task t = Task.getCurrentTask();
		boolean available = false;
		int m = buffer.length;
		while (n != count) {
			long wrapPoint = currentTail - m;
			while (headCache.value <= wrapPoint) {
				headCache.value = head.get();
				if (headCache.value <= wrapPoint) {
					if (available) {
						// we have put atleast one new message so we should wake
						// up if someone is waiting for message
						tail.lazySet(currentTail);
					}
                                        mb.subscribe(available,t,true);
					available = false;
				}
			}
			buffer[(int) (currentTail++) & mask] = buf[count++];
			available = true;

		}
		tail.lazySet(currentTail);
                mb.subscribe(true,t,false);
	}

	public boolean fillnb(T[] msg) {
		int n = msg.length;
		long currentHead = head.get();
		if ((currentHead + n) > tailCache.value) {
			tailCache.value = tail.get();
		}
		n = (int) Math.min(tailCache.value - currentHead, n);
		if (n == 0) {
			
			return false;
		}
		int i = 0;
		
		do {
			final int index = (int) (currentHead++) & mask;
			msg[i++] = buffer[index];
			buffer[index] = null;

		} while (0 != --n);
		head.lazySet(currentHead);
		return true;
	}

	public boolean isEmpty() {
		headCache.value = head.get();
		return (buffer[(int) headCache.value & mask]==null);
	}

	public boolean hasSpace() {
		tailCache.value = tail.get();
		return (buffer[(int) tailCache.value & mask] == null);
	}

	public int size() {

		return (int) (tail.get() - head.get());
	}

}
