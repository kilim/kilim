package kilim;

import kilim.concurrent.MPSCQueue;
import kilim.concurrent.VolatileReferenceCell;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class MainMailbox<T> implements PauseReason, EventPublisher {

    public static final int SPACE_AVAILABLE = 1;
    public static final int MSG_AVAILABLE = 2;
    public static final int TIMED_OUT = 3;

    public static final Event spaceAvailble = new Event(SPACE_AVAILABLE);
    MPSCQueue<T> msgs;

    VolatileReferenceCell<EventSubscriber> sink = new VolatileReferenceCell<EventSubscriber>();
    Queue<EventSubscriber> srcs = new ConcurrentLinkedQueue<EventSubscriber>();
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

    synchronized
    public void removeMsgAvailableListener(EventSubscriber msgSub) {
        sink.set(null);
    }

    synchronized
    public void addMsgAvailableListener(EventSubscriber msgSub) {
        sink.set(msgSub);
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

    public EventSubscriber getProducer() {
        return srcs.poll();
    }

}
