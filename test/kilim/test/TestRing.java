package kilim.test;

import junit.framework.TestCase;

import java.util.Random;

import kilim.RingQueue;

public class TestRing extends TestCase {
    public void testInfiniteQueue() {
        Integer[] a = mkRandom();
        RingQueue<Integer> rq = new RingQueue<Integer>(10);
        for (int i = 0; i < a.length; i++) {
            assertTrue("put returned false", rq.put(a[i]));
        }
        assertEquals("Queue size", a.length, rq.size());
        for (int i = 0; i < a.length; i++) {
            assertEquals("get[" + i + " ]returned different element", a[i], rq.get());
        }
        // No more elements. The next ha    d better be null
        assertNull("Queue should not have any more elements", rq.get());
        assertTrue(rq.size() == 0);
    }

    public void testBoundedQueue() {
        Integer[] a = mkRandom();
        RingQueue<Integer>   rq = new RingQueue<Integer>(10, a.length);
        for (int i = 0; i < a.length; i++) {
            assertTrue("put returned false", rq.put(a[i]));
        }
        assertFalse("put should not accept more than bound", rq.put(100));
        assertTrue(rq.size() == a.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals("get[" + i + " ]returned different element", a[i], rq.get());
        }
        // No more elements. The next had better be null
        assertNull("Queue should not have any more elements", rq.get());
        assertTrue(rq.size() == 0);
    }

    private Integer[] mkRandom() {
        Random r = new Random();
        Integer[] ret = new Integer[1000];
        for (int i = 0; i < 1000; i++) {
            ret[i] = new Integer(r.nextInt(1000));
        }
        return ret;
    }
}
