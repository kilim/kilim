package kilim.demo;

import kilim.*;

public class CodeA implements CodeB {
    public void doSome() throws Pausable, Exception {
        Task.sleep(1500);
        System.out.println("\n\nPausable runtime-woven method successfully invoked\n\n");
    }
}
