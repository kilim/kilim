package kilim.test.ex;

import kilim.Pausable;


// Ensure we don't call a pausable method from within a synchronized block
public class ExInvalidSynchronized {
    void foo() throws Pausable {}
    synchronized void sync() throws Pausable {
        foo();
    }
}

class ExInvalidSynchronized1 {
    void foo() throws Pausable {}
    void sync() throws Pausable {
        synchronized(this) {
            foo();
        }
    }
}
