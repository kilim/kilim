package kilim.test.ex;

import kilim.Pausable;

public class ExPausable {
    void noop() throws Pausable {
        
    }
    
    void simple() throws Pausable {
        noop();
    }
}
