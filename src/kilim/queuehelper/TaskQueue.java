package kilim.queuehelper;

import java.util.Arrays;
import java.util.concurrent.locks.Lock;

import kilim.ReentrantLock;
import kilim.timerhelper.Timer;

public class TaskQueue {
    public Lock lock = new java.util.concurrent.locks.ReentrantLock();
    public Timer[] queue = new Timer[128];
    private int size = 0;

    public int size() {
        return size;
    }

    public Timer peek() {
        return queue[1];
    }

    public Timer get(int i) {
        return queue[i];
    }

    public void rescheduleMin(long newTime) {
        queue[1].nextExecutionTime = newTime;
        heapifyDown(1);
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        for (int i = 1; i <= size; i++)
            queue[i] = null;

        size = 0;
    }

    public  void add(Timer task) {

        if (size + 1 == queue.length)
            queue = Arrays.copyOf(queue, 2 * queue.length);
        queue[++size] = task;
        heapifyUp(size);

    }
    
    public  void addWithoutHepify(Timer task) {

        if (size + 1 == queue.length)
            queue = Arrays.copyOf(queue, 2 * queue.length);
        queue[++size] = task;
        queue[size-1].index=size-1;
       // heapifyUp(size);

    }

    public void heapifyUp(int k) {
        while (k > 1) {
            int j = k >> 1;
            if (queue[j].nextExecutionTime <= queue[k].nextExecutionTime)
                break;
            Timer tmp = queue[j];
            queue[j] = queue[k];
            queue[j].index=j;
            queue[k] = tmp;
            queue[k].index=k;
            k = j;
        }
    }

    public void heapifyDown(int k) {
        int j;
        while ((j = k << 1) <= size && j > 0) {
            if (j < size
                    && queue[j].nextExecutionTime > queue[j + 1].nextExecutionTime)
                j++;
            if (queue[k].nextExecutionTime <= queue[j].nextExecutionTime)
                break;
            Timer tmp = queue[j];
            queue[j] = queue[k];
            queue[j].index=j;
            queue[k] = tmp;
            queue[k].index=k;
            k = j;
        }
    }

    public void poll() {
        queue[1] = queue[size];
        queue[1].index=1;
        queue[size--] = null;
        heapifyDown(1);

    }

    public void heapify() {
        for (int i = size / 2; i >= 1; i--)
            heapifyDown(i);
    }
}
