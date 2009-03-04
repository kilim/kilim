package kilim.test.ex;

import kilim.Pausable;
import kilim.Task;

public class ExCatch extends ExYieldBase {
    public ExCatch(int test) {
        testCase = test;
    }
    
    /*
    public void execute() throws pausable {
        if (testCase == 3) {
            doPause = false;
            tryCatchFinally();
        }
    }
    */

    public void execute() throws Pausable {
        doPause = false;
        test();
        doPause = true;
        test();
    }
    
    private void test() throws Pausable {
        switch(testCase) {
            case 0: normalCatch(); break; 
            case 1: pausableCatch(); break;
            case 2: nestedPausableCatch(); break;
            case 3: tryCatchFinally(); break;
            default: throw new IllegalStateException("Unknown test case: " + testCase);
        }
    }

    void normalCatch() throws Pausable {
        double d = fd;
        String[][] sa = fa;
        String s = fs;
        try {
            pausable(d);
        } catch (ExException eye) {
            String es = eye.getMessage();
            verify(es);
            s = es;
        }
        verify(d);
        verify(sa);
        verify(s);
    }
    
    void pausableCatch() throws Pausable {
        double d = fd;
        String  s = fs;
        String[][] sa = fa;
        long   l = fl;
        try {
            pausable(d);
//            throw new ExException("10");
        } catch (ExException eye) {
            String es = eye.getMessage();
            if (doPause) {
                Task.sleep(50);
            }
            verify(es);
            s = es;
        }
        verify(d);
        verify(sa);
        verify(s);
        verify(l);
    }

    void tryCatchFinally() throws Pausable {
        short sh = fsh; 
        String  s = fs;
        double  d = fd;
        long   l = fl;
        try {
            try {
                pausable(d);
//              throw new ExException("10");
            } catch (ExException eye) {
                if (doPause) {
                    Task.sleep(50);
                }
                throw new Exception("");
            } finally {
                if (doPause) {
                    Task.sleep(50);
                }
            }
        } catch (Exception e) {
        }
        verify(d);
        verify(sh);
        verify(s);
        verify(l);
    }

    
    void nestedPausableCatch() throws Pausable {
        double d = fd;
        String  s = fs;
        String[][] sa = fa;
        long   l = fl;
        try {
            throw new ExException("10");
        } catch (final ExException eye) {
            String es = null;;
            try {
                throw new ExException("10");
            } catch (Exception e) {
                es = eye.getMessage();
                verify(es);
                es = e.getMessage();
                if (doPause) {
                    Task.sleep(50);
                }
            }
            verify(es);
            s = es;
        }
        verify(d);
        verify(sa);
        verify(s);
        verify(l);
    }


    private void pausable(double d) throws ExException, Pausable {
        if (doPause) {
            Task.sleep(50);
        }
        verify(d);
        throw new ExException("10");
    }
}