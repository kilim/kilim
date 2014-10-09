package kilim.timerservice;

import java.util.Arrays;

public class TimerPriorityHeap {
	private Timer[] queue;
	private int size = 0;

	public TimerPriorityHeap() {
		this(128);
	}

	public TimerPriorityHeap(int size) {
		queue = new Timer[size];
	}

	public int size() {
		return size;
	}

	public Timer peek() {
		return queue[1];
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public void add(Timer task) {

		if (size + 1 == queue.length)
			queue = Arrays.copyOf(queue, 2 * queue.length);
		queue[++size] = task;
		heapifyUp(size);

	}

	public void reschedule(int i) {
		heapifyUp(i);
		heapifyDown(i);

	}

	private void heapifyUp(int k) {
		while (k > 1) {
			int j = k >> 1;
			if (queue[j].getExecutionTime() <= queue[k].getExecutionTime())
				break;
			Timer tmp = queue[j];
			queue[j] = queue[k];
			queue[j].index = j;
			queue[k] = tmp;
			queue[k].index = k;
			k = j;
		}
	}

	private void heapifyDown(int k) {
		int j;
		while ((j = k << 1) <= size && j > 0) {
			if (j < size
					&& queue[j].getExecutionTime() > queue[j + 1]
							.getExecutionTime())
				j++;
			if (queue[k].getExecutionTime() <= queue[j].getExecutionTime())
				break;
			Timer tmp = queue[j];
			queue[j] = queue[k];
			queue[j].index = j;
			queue[k] = tmp;
			queue[k].index = k;
			k = j;
		}
	}

	public void poll() {
		queue[1] = queue[size];
		queue[1].index = 1;
		queue[size--] = null;
		heapifyDown(1);

	}

	// private void heapify() {
	// for (int i = size / 2; i >= 1; i--)
	// heapifyDown(i);
	// }

}
