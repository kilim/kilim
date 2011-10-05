package kilim.test.ex;

import kilim.Pausable;
import kilim.Task;

public class ExYieldStack extends ExYieldBase {

    public ExYieldStack(int test) {
        testCase = test;
    }
    
    public void execute() throws Pausable {
        doPause = false;
        test();
        doPause = true;
        test();
    }
    
    private void test() throws Pausable {
        switch (testCase) {
            case 0: testStackBottom_st(); break;
            case 1: testStackBottom_v(); break;
            case 2: testStackBottom_av(); break;
            case 3: testFactorial_st(); break;
            case 4: testFactorial_av(); break;
            default: new IllegalStateException("Unknown testCase " + testCase);
        }
    }

    // load stack with a number of types, call a static pausable method
    // The call to verify ensures that the stack is loaded with
    // a few elements before the call to pausable_x is made.
    void testStackBottom_st() throws Pausable {
        verify(fd, fs, fl, fa, pausable_st(10, doPause));
    }

    // load stack with a number of types, call a virtual pausable method
    void testStackBottom_v() throws Pausable {
        verify(fd, fs, pausable_v(fl), fa, ff);
    }

    // load stack with a number of types, call a virtual pausable method
    // on another object
    void testStackBottom_av() throws Pausable {
        verify(fd, fs, fl, fa, new ExYieldStack(testCase).pausable_v(10));
    }
    
    // test a deep hierarchy that makes the state stack grow, have
    // long parameters and return values, and pause in the middle
    // of the computation so it is saves longs on the stack
    void testFactorial_st() throws Pausable {
        long n = fact_st(15L, doPause);
        if (n != 1307674368000L) {
            throw new RuntimeException("Incorrect factorial, n =" + n);
        }
    }
    
    // Issue 8 on github
    void testLoop() throws Pausable {
        // The other tests don't test constant propagation, but not dynamic operands
        // on stack.
        
    }
    
    static long fact_st(long n, boolean doPause) throws Pausable {
//        System.out.println("n = " + n + ", doPause = " + doPause);
        if (n == 1) {
            if (doPause) Task.sleep(10);
            return 1L;
        } 
        if (n == 10) {
            // Initial state stack is 10 elements long, so it is worth
            // testing a pause here. 
            if (doPause) Task.sleep(10);
        }
        return n * fact_st(n-1, doPause);
    }

    void testFactorial_av() throws Pausable {
        long n = new ExYieldStack(testCase).fact_av(15L, doPause);
        if (n != 1307674368000L) {
            throw new RuntimeException("Incorrect factorial, n =" + n);
        }
    }
    
    long fact_av(long n, boolean doPause) throws Pausable {
        if (n == 1) {
            if (doPause) Task.sleep(50);
            return 1L;
        } 
        if (n == 10) {
            // Initial state stack is 10 elements long, so it is worth
            // testing a pause here. 
            if (doPause) Task.sleep(50);
        }
        return n * new ExYieldStack(testCase).fact_av(n-1, doPause);
    }

    static float pausable_st(int i, boolean doPause) throws Pausable {
        if (doPause) {
            Task.sleep(50);
        }
        return 11.0f;
    }

    long pausable_v(long l) throws Pausable {
        if (doPause) {
            Task.sleep(50);
        }
        return l;
    }

    static void verify(double ad, Object as, long al, Object aa, float aii) {
        verify(ad);
        verify((String) as);
        verify(al);
        verify((String[][]) aa);
    }
    
    void dummySyncTest() throws Pausable {
        // just making sure that the weaver doesn't barf if we call
        // call a pausable function from _outside_ a synchronized block.
        synchronized(this) {
            notify(); // dummy non-pausable method in a sync block
        }
        
        pausable_st(0, false);

        synchronized(this) {
            notify(); // dummy non-pausable method in a sync block
        }
        
    }
    
    
}
