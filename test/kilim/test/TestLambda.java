package kilim.test;

import junit.framework.TestCase;
import kilim.ExitMsg;
import kilim.Pausable;
import kilim.Task;

public class TestLambda extends TestCase {
    public void testLambda() throws Exception {
        ExitMsg e = new LamTask("testLambda", 100, 200).start().joinb();
        if (!(e.result.equals("testLambda300"))) {
            fail("Expected add300");
        }
    }
    /**
     * Task that calls a pausable lambda method. We test with repeated pauses, captured variables
     * and explicit arguments. 
     */
    static class LamTask extends kilim.Task {
        
        
        private String s;
        private int a;
        private int b;

        public LamTask(String s, int a, int b) {
            this.s = s;
            this.a = a;
            this.b = b;
        }

        @Override
        public void execute() throws Pausable {
            int captured1 = this.a;
            int captured2 = this.b;
            
            Lambda<String> lam = (String input) -> {
                int c = 0;
                Task.yield();
                c += captured1;
                Task.sleep(10);
                c += captured2;
                Task.yield();
                return input + c;
            };
            String output = lam.process("testLambda"); // should be testLambda<a+b>
            Task.exit(output);
        }
    }
    
    static interface Lambda<T> {
        T process(String input) throws Pausable;
    }
}

