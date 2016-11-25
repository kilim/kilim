// Copyright 2014 nilangshah - offered under the terms of the MIT License

package kilim.concurrent;

import static kilim.concurrent.UnsafeAccess.UNSAFE;

abstract class VolatileLongCellPrePad {
	volatile long p0, p1, p2, p3, p4, p5, p6;
}

abstract class VolatileLongCellValue extends VolatileLongCellPrePad {
	protected volatile long value;
}

@SuppressWarnings("restriction")
public final class VolatileLongCell extends VolatileLongCellValue {
	volatile long p10, p11, p12, p13, p14, p15, p16;
	private final static long VALUE_OFFSET;
	static {
		try {
			VALUE_OFFSET = UNSAFE.objectFieldOffset(VolatileLongCellValue.class
					.getDeclaredField("value"));
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
	}

	public VolatileLongCell() {
		this(0L);
	}

	public VolatileLongCell(long v) {
		lazySet(v);
	}

	public void lazySet(long v) {
		UNSAFE.putOrderedLong(this, VALUE_OFFSET, v);
	}

	public void set(long v) {
		this.value = v;
	}

	public long get() {
		return this.value;
	}
}
