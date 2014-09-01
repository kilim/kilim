package kilim;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class RingQueue<T> {
    private int           maxSize_;
    private AtomicInteger currentSize_ = new AtomicInteger(0);
    private Queue<T>      queue_       = new ConcurrentLinkedQueue<T>();

    public RingQueue() {
    }

    public RingQueue(int maxSize) {
        maxSize_ = maxSize;
    }

    public T peek() {
        return queue_.peek();
    }

    public boolean put(T t) {
        int value = currentSize_.incrementAndGet();
        if (value >= maxSize_)
            return false;
        queue_.offer(t);
        return true;
    }

    public T get() {
        return queue_.poll();
    }

    public int size() {
        return queue_.size();
    }

    public boolean isEmpty() {
        return queue_.isEmpty();
    }

    public void reset() {
        queue_.clear();
    }
}
