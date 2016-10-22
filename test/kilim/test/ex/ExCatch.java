package kilim.test.ex;

import kilim.Fiber;
import kilim.Pausable;
import kilim.Continuation;
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
            case 4: pausableBeforeCatch(); break;
            case 5: tryDefUse(); break;
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

    // Issue#6 on github. A pausable block before the catch block.
    void pausableBeforeCatch() throws Pausable {
        int foo = 0;
        Task.sleep(1);
        if (foo != 0) throw new RuntimeException("Expected 0");
        
        foo = 1;
        Task.sleep(1);
        if (foo != 1) throw new RuntimeException("Expected 1");
        
        foo = 2;
        Task.sleep(1);
        if (foo != 2) throw new RuntimeException("Expected 2");
        
        try {
            foo = 3;
            throwEx();
        } catch (Throwable t) {
            if (foo != 3) throw new RuntimeException("Expected 3");
        }
    }
    private static void throwEx() throws Pausable {
        Task.sleep(1);
        throw new RuntimeException();
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

    // use after a define that can be skipped by an exception
    void tryDefUse() throws Pausable {
        double  d = fd, d2 = d+1;
        try {
            pausable(d);
            d = d2;
        }
        catch (Exception e) {}
        verify(d);
    }


    private void pausable(double d) throws ExException, Pausable {
        if (doPause) {
            Task.sleep(50);
        }
        verify(d);
        throw new ExException("10");
    }

    public static class Pure extends Continuation {
        int [] count = new int[11];
        public void execute() throws Pausable {
            double d = fd;
            String[][] sa = fa;
            String s = fs;
            try {
                pausableThrow(0);
            } catch (Exception kex) {
                String es = kex.getMessage();
                verify(es);
                s = es;
            }
            verify(d);
            verify(sa);
            verify(s);
            for (int cc : count)
                verify(cc,14);
        }
        public void pausableThrow(int num) throws Pausable, Exception {
            count[num]++;
            Fiber.yield();
            count[num] += 13;
            if (num < 10) pausableThrow(num+1);
            else throw new Exception("10");
            count[num] += 107;
        }
    }
}
