package kilim.test.ex;

import kilim.Pausable;
import kilim.Task;

public class ExLoop extends Task {
    public String foo[] = new String[5];
    String dummy() throws Pausable {
        Task.yield();
        return "dummy";
    }
    @Override
    public void execute() throws Pausable, Exception {
        for (int i = 0; i < foo.length; i++) {
            // foo and i are on the operand stack before dummy gets called. This
            // test checks that the operand stack is correctly restored.
            foo[i] = dummy();
        }
    }
    
    public boolean verify() {
        // Call after ExLoop task has finished. foo[1..n] must have "dummy".
        for (int i = 0; i < foo.length; i++) {
            if (! "dummy".equals(foo[i])) {
                return false;
            }
        }
        return true;
    }
}
