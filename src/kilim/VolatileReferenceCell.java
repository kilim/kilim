package kilim;

import static kilim.UnsafeAccess.UNSAFE;

abstract class VolatileReferenceCellValue<V> extends VolatileLongCellPrePad {
    protected volatile V value;
}

public class VolatileReferenceCell<V> extends VolatileReferenceCellValue<V> {
    long p10, p11, p12, p13, p14, p15, p16;
    private static final long valueOffset;

    static {
        try {
            valueOffset = UNSAFE
                    .objectFieldOffset(VolatileReferenceCellValue.class
                            .getDeclaredField("value"));
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    public VolatileReferenceCell(V initialValue) {
        value = initialValue;
    }

    public VolatileReferenceCell() {
    }

    public final V get() {
        return value;
    }

    public final void set(V newValue) {
        value = newValue;
    }

    public final void lazySet(V newValue) {
        UNSAFE.putOrderedObject(this, valueOffset, newValue);
    }

    public final boolean compareAndSet(V expect, V update) {
        return UNSAFE.compareAndSwapObject(this, valueOffset, expect, update);
    }

    public final boolean weakCompareAndSet(V expect, V update) {
        return UNSAFE.compareAndSwapObject(this, valueOffset, expect, update);
    }

    public final V getAndSet(V newValue) {
        while (true) {
            V x = get();
            if (compareAndSet(x, newValue))
                return x;
        }
    }

    public String toString() {
        return String.valueOf(get());
    }

}
