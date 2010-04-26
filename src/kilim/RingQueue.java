package kilim;

public class RingQueue<T> {
    protected T[] elements;
    protected int iprod;   // producer index
    protected int icons;   // consumer index;
    protected int maxSize;
    protected int size;

    public RingQueue(int initialSize) {
        this(initialSize, Integer.MAX_VALUE);
    }
    
    @SuppressWarnings("unchecked")
    public RingQueue(int initialSize, int maxSize) {
        elements = (T[]) new Object[initialSize];
        size = 0;
        this.maxSize = maxSize;
    }

    public int size() {
        return size;
    }

    public T get() {
        T elem;
        T[] elems;
        int n = size;
        if (n > 0) {
            elems = elements;
            int ic = icons;
            elem = elems[ic];
            elems[ic] = null;
            icons = (ic + 1) % elems.length;
            size = n - 1;
        } else {
            elem = null;
        }
        return elem;
    }

    @SuppressWarnings("unchecked")
    public boolean put(T elem) {
        boolean ret = true;
        if (elem == null) {
            throw new NullPointerException("Null message supplied to put");
        }
        int ip = iprod;
        int ic = icons;
        int n = size;
        if (n == elements.length) {
            assert ic == ip : "numElements == elements.length && ic != ip";
            if (n < maxSize) {
                T[] newmsgs = (T[]) new Object[Math.min(n * 2, maxSize)];
                System.arraycopy(elements, ic, newmsgs, 0, n - ic);
                if (ic > 0) {
                    System.arraycopy(elements, 0, newmsgs, n - ic, ic);
                }
                elements = newmsgs;
                ip = n;
                ic = 0;
            } else {
                ret = false;
            }
        }
        if (ret) {
            size = n + 1;
            elements[ip] = elem;
            iprod = (ip + 1) % elements.length;
            icons = ic;
        }
        return ret;
    }

    public boolean contains(T obj) {
        int i = icons;
        int c = 0;
        T[] elems = elements;
        while (c < size) {
            if (obj == elems[i])
                return true;
            i = (i + 1) % elems.length;
            c++;
        }
        return false;
    }

    public void reset() {
        icons = iprod = 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int i = icons;
        int c = 0;
        T[] elems = elements;
        while (c < size) {
            sb.append(elems[i]);
            i = (i + 1) % elems.length;
            c++;
        }
        return sb.toString();
    }
}
