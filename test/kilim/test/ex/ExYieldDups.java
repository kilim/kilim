package kilim.test.ex;

import kilim.Pausable;
import kilim.Task;

public class ExYieldDups extends ExYieldBase {
    public ExYieldDups(int test) {
        testCase = test;
    }
    
    public void execute() throws Pausable {
        doPause = false;
        test();
        doPause = true;
        test();
    }
    
    private void test() throws Pausable {
        switch(testCase) {
            case 0: testDupVars(); break;
            case 1: testDupsInStack(); break;
            case 2: testLongArgs(); break;
            default: throw new IllegalStateException("Unknown test case: " + testCase);
        }
    }



    void testLongArgs() throws Pausable {
        LongVal val = new LongVal();
        useLongArg(fl,val);
        verify(val.msg);
    }

    static class LongVal {
        long msg;
    }
    
    // long and double values occupy 2 slots in the local variables table
    // the weaver needs to track this to be able to mark the arguments as in-use
    // test whether the weaver is correctly marking val as used after the pause
    void useLongArg(long index,LongVal val) throws Pausable {
        if (doPause)
            Task.sleep(50);
        val.msg = index;
    }
    
    
    void testDupVars() throws Pausable {
        double d = fd;
        double dup_d = d;
        long l = fl;
        long dup_l = l;
        String s = fs;
        String dup_s = s; 
        if (doPause) { 
            Task.sleep(50);
        }
        verify(d);
        verify(dup_d);
        verify(l);
        verify(dup_l);
        verify(s);
        verify(dup_s);
    }

    void testDupsInStack() throws Pausable {
        float f = ff;
        long l = fl;
        long dup_l = l;
        char c = fc;
        char dup_c = fc;
        float dup_f = f;
        verify(l, dup_l, c, dup_c, f, dup_f);
        if (doPause) {
            Task.sleep(50);
        }
        verify(f);
        verify(dup_f);
        verify(l);
        verify(dup_l);
    }

    
    static void verify(long l, long dup_l, char c, char dup_c, float f , float dup_f) {
        verify(l);
        verify(dup_l);
        verify(c);
        verify(dup_c);
        verify(f);
        verify(dup_f);
    }
}
