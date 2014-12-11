package kilim.test.ex;

import java.io.IOException;
import java.lang.reflect.Method;
import kilim.Fiber;
import kilim.Pausable;
import kilim.Continuation;
import kilim.Mailbox;
import kilim.Task;
import static kilim.Task.idledown;

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
            case 6: whileCatch(); break;
            case 7: restoreArgument(fd); break;
            case 8: correctException(); break;
            case 9: pausableInvokeCatch(); break;
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
    public static void restoreArgument(Double resp) throws Pausable {
        try {
            throwEx();
        }
        catch (Exception ex) {}
        verify(resp);
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

    private class MyFile {
        private int isFile = 0;
        boolean isDirectory() throws Pausable, IOException {
            if (doPause)
                Task.sleep(50);
            isFile++;
            if (isFile % 15==0) throw new IOException();
            int val = isFile % 6;
            return val==0 | val==2 | val==3;
        }
    }
    private MyFile baseDirectory = new MyFile();
    private void readRequest(String obj) throws Pausable, IOException {
        if (doPause)
            Task.sleep(50);
        verify(obj);
    }
    
    public void whileCatch() throws Pausable {
        int vi = fi;
        byte vb = fb;
        try {
            double d = fd;
            String  s = fs;
            String[][] sa = fa;
            long   l = fl;
            String req = fs;
            while (true) {
                verify(d);
                readRequest(req);
                verify(s);
                MyFile file = baseDirectory;
                if (file.isDirectory()) {
                    verify(sa);
                    file.isDirectory();
                    verify(l);
                }
            }
        } catch (Exception ioe) {
            verify(vi);
        }
        verify(vb);
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
        {
            double  d = fd, d2 = d+1;
            try {
                pausable(d);
                d = d2;
            }
            catch (Exception e) {}
            verify(d);
        }
        {
            double  d = fd, d2 = d+1;
            try {
                pausable(d);
            }
            catch (Exception e) { d2 = d; }
            verify(d2);
        }
        {
            double  d = fd, d2 = d+1;
            try {
                pausable(d);
            }
            catch (Exception e) { d2 = d; }
            verify(d2);
        }
        try { doFinally(); }
        catch (NullPointerException ex) {}
        try { doFinally2(); }
        catch (NullPointerException ex) {}
        try { doFinally3(); }
        catch (NullPointerException ex) {}
        doFinally4();

        double  d = fd, d2 = d+1;
        if (doPause)
            Task.sleep(50);
        try {
            ((Object) null).toString();
            d = d2;
        }
        catch (Throwable ex) {}
        verify(d);
    }

    void doFinally() throws Pausable {
        double  d = fd, d2 = d+1;
        if (!doPause)
            Task.sleep(50);
        try {
            if (doPause)
                Task.sleep(50);
            ((Object) null).toString();
            d2 = d;
        }
        finally { verify(d); verify(d2-1); }
    }
    void doFinally2() throws Pausable {
        {
            double  d = fd, d2 = d+1;
            try {
                if (doPause)
                    Task.sleep(50);
                ((Object) null).toString();
                d = d2;
            }
            catch (Exception e) {
                ((Object) null).toString();
                d = d2;
            }
            finally { verify(d); }
        }
    }
    void doFinally3() throws Pausable {
        {
            double  d = fd, d2 = d+1;
            if (doPause)
                Task.sleep(50);
            try {
                ((Object) null).toString();
                d = d2;
            }
            catch (Exception e) {
                ((Object) null).toString();
                d = d2;
            }
            finally { verify(d); }
        }
    }
    void doFinally4() throws Pausable {
        double  d = fd, d2 = d+1;
        try {
            try {
                if (doPause)
                    Task.sleep(50);
                ((Object) null).toString();
                d = d2;
            }
            catch (Exception e) {
                ((Object) null).toString();
                d = d2;
            }
            finally { verify(d); }
        }
        catch (NullPointerException ex) {}
        try {
            if (doPause)
                Task.sleep(50);
            try {
                ((Object) null).toString();
                d = d2;
            }
            catch (Exception e) {
                ((Object) null).toString();
                d = d2;
            }
            finally { verify(d); }
        }
        catch (NullPointerException ex) {}
        if (doPause)
            Task.sleep(50);
        try {
            try {
                ((Object) null).toString();
                d = d2;
            }
            catch (Exception e) {
                ((Object) null).toString();
                d = d2;
            }
            finally {}
        }
        catch (NullPointerException ex) {}
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


    // crude check for issue 55 - verify exceptions don't get reordered
    public void correctException() throws Pausable {
        double val = fd;
        try {
            Task.sleep(1);
            throw new RuntimeException();
        }
        catch (RuntimeException ex) {}
        catch (Exception ex) {
            throw new RuntimeException("incorrect exception");
        }
        verify(val);
    }


    // merged from https://github.com/hyleeon/kilim/tree/fix-invoke
    void pausableInvokeCatch() throws Pausable {
        String[][] sa = fa;
        long l = fl;
        try {
            Method mthd = ExCatch.class.getDeclaredMethod("pausableInvokeCatch0",new Class[0]);
            Task.invoke(mthd,this);
        } catch (Exception eye) {
            eye.printStackTrace();
        }
        verify(sa);
        verify(l);
    }
    
    public void pausableInvokeCatch0() throws Pausable {
        double d = fd;
        String s = fs;
        try {
            pausable(d);
        }
        catch (Exception eye) {
            if (eye instanceof java.lang.reflect.InvocationTargetException)
                eye = (Exception) eye.getCause();
            String es = eye.getMessage();
            if (doPause)
                Task.sleep(50);
            verify(es);
            s = es;
        }
        verify(d);
        verify(s);
    }


}
