package kilim.test.ex;

import kilim.Pausable;

public class ExBasicBlock {
    void noop() throws ArrayIndexOutOfBoundsException {
    }
    
    static void pausable() throws Pausable {
        
    }
    
    int loop() throws Pausable {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum++;
        }
        return sum;
    }

    void nestedloop() throws Pausable {
        for (int i = 0; i < 100; i++) {
            while (i > 10) {
                i--;
            }
        }
    }
    
    void exception() throws Pausable {
        try {
            try {
                pausable();
            } catch (ArrayIndexOutOfBoundsException e) {
                try {
                    e.printStackTrace();
                } catch (Throwable t) {
                    noop();
                }
            }
        } finally {
            noop();
        }
    }
    
    void complex() throws Pausable {
        double d = 10.0;
        Object o = new Object();
        for (int i = 0; i < 100; i++) {
            try {
                if (d > 10.3 && d < 10.5) {
                    d = 20.0;
                    try {
                        synchronized(o) {
                            o.hashCode();
                        }
                    } catch (RuntimeException re) {
                        throw new Error(re.toString());
                    }
                }
            } finally {
                d = 100.0;
            }
        }
    }
}