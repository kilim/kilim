// Copyright 2014 nilangshah - offered under the terms of the MIT License

package kilim.concurrent;

import static kilim.concurrent.UnsafeAccess.UNSAFE;

abstract class VolatileBooleanPrePad {
    // long p0, p1, p2, p3, p4, p5, p6;
}

abstract class VolatileBooleanValue extends VolatileBooleanPrePad {
    protected volatile int value;
}

@SuppressWarnings("restriction")
public final class VolatileBoolean extends VolatileBooleanValue {
    // long p10, p11, p12, p13, p14, p15, p16;
    private final static long VALUE_OFFSET;
    static {
        try {
            VALUE_OFFSET = UNSAFE.objectFieldOffset(VolatileBooleanValue.class.getDeclaredField("value"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public VolatileBoolean() {
        this(false);
    }

    public VolatileBoolean(boolean v) {
        lazySet(v);
    }

    public void lazySet(boolean newV) {
        int v = newV ? 1 : 0;
        UNSAFE.putOrderedInt(this, VALUE_OFFSET, v);
    }

    public void set(boolean newV) {
        value = newV ? 1 : 0;
    }

    public boolean get() {
        return value != 0;
    }

    public final boolean compareAndSet(boolean expect, boolean update) {
        int e = expect ? 1 : 0;
        int u = update ? 1 : 0;
        return UNSAFE.compareAndSwapInt(this, VALUE_OFFSET, e, u);
    }
}
