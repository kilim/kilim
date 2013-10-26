package kilim.test.ex;

import kilim.Pausable;

public class ExGenerics<T> extends ExYieldBase {
    
    public void execute() throws Pausable {
        doPause = false;
        test();
        doPause = true;
        test();
    }

    T foo() throws Pausable {
        return null;
    }
    
    static class ExGenericsConcrete extends ExGenerics<String> {
        String foo() throws Pausable {
            String s = fs;
            if (doPause) {
                sleep(1);
            }
            return s;
        }
    }
    private void test() throws Pausable {
        ExGenericsConcrete eb = new ExGenericsConcrete();
        verify(eb.foo());
    }
}
