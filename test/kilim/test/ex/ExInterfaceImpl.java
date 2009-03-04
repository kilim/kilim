package kilim.test.ex;

import kilim.*;

interface ExInterface {
    int foo(float f) throws Pausable;
}

public class ExInterfaceImpl extends Task implements ExInterface  {
    public void execute() throws Pausable {
        foo(10.2f);
    }
    
    public int foo(float f) throws Pausable {
        Task.sleep(1000);
        
        return (int)f;
    }
}
