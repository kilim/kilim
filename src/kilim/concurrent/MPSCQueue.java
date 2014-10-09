package kilim.concurrent;

import java.util.NoSuchElementException;
import java.util.concurrent.locks.LockSupport;

// "Inspired directly by Nitasan's work --> https://github.com/nitsanw/QueueEvolution"
abstract class MPSCQueueL0Pad {
	public long p00, p01, p02, p03, p04, p05, p06, p07;
	public long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MPSCQueueColdFields<E> extends MPSCQueueL0Pad {
	protected static final int BUFFER_PAD = 64; // to
												// pad
												// queue
												// ends
	protected static final int SPARSE_SHIFT = Integer.getInteger(
			"sparse.shift", 0); // pad
								// each
								// element
								// of
								// queue
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

	public static boolean isPowerOf2(final int value) {
		return (value & (value - 1)) == 0;
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

public class MPSCQueue<E> extends MPSCQueueL3Pad<E> {
	private static final BackOffStrategy CAS_BACKOFF = BackOffStrategy
			.getStrategy("cas.backoff", BackOffStrategy.SPIN); // set property to change backoff strategy

	
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
			if(currentTail==Long.MAX_VALUE){
				System.out.println("bug");
			}
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

	public void fill(E []buf){
		int n=buf.length;
		for(int i=0;i<n;i++){
			buf[i] = null;
			buf[i] = poll();
			if(buf[i]==null){
				return;
			}
		}
		
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
		return (E) UnsafeAccess.UNSAFE.getObject(buffer,
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
		YIELD {
			public int backoff(int called) {
				Thread.yield();
				return called++;
			}
		},
		PARK {
			public int backoff(int called) {
				LockSupport.parkNanos(1);
				return called++;
			}
		},
		SPIN_YIELD {
			public int backoff(int called) {
				if (called > 1000)
					Thread.yield();
				return called++;
			}
		};
		public abstract int backoff(int called);

		public static BackOffStrategy getStrategy(String propertyName,
				BackOffStrategy defaultS) {
			try {
				return BackOffStrategy
						.valueOf(System.getProperty(propertyName));
			} catch (Exception e) {
				return defaultS;
			}
		}
	}

}
