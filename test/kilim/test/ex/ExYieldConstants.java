package kilim.test.ex;

import kilim.Pausable;
import kilim.Task;

public class ExYieldConstants extends ExYieldBase {
    public ExYieldConstants(int test) {
        testCase = test;
    }
    
    public void execute() throws Pausable {
        doPause = false;
        testConstants();
        doPause = true;
        testConstants();
    }

    void testConstants() throws Pausable {
    	double d0 = 0;
    	double d1 = 1;
    	double d = 10.0;
    	int i0 = 0;
    	long l0 = 0;
    	long l1 = 1;
    	long l = 10;
    	int i1 = 1;
    	int i2 = 2;
    	int i3 = 3;
    	int i4 = 4;
    	int i5 = 5;
    	float f0 = 0;
    	float f1 = 1;
    	int i = 10;
    	String s = "10";
        if (doPause) { 
            Task.sleep(50);
        }
        pausable_st(d, s, l, doPause);
        new ExYieldConstants(testCase).pausable_v(d, s, l);
        verify(d);
        verify(l);
        verify(s);
        verify(i);
        // constants with built-in opcode support
        verify(f0, 0.0f);
        verify(f1, 1.0f);
        verify(d0, 0.0d);
        verify(d1, 1.0d);
        verify(i0, 0);
        verify(i1, 1);
        verify(i2, 2);
        verify(i3, 3);
        verify(i4, 4);
        verify(i5, 5);
        verify(l0, 0);
        verify(l1, 1);
    }

    static void pausable_st(double d, String s, long l, boolean doPause) throws Pausable {
        if (doPause) {
            Task.sleep(50);
        }
        verify(l);
        verify(s);
        verify(d);
    }

    void pausable_v(double d, String s, long l) throws Pausable {
        if (doPause) {
            Task.sleep(50);
        }
        verify(l);
        verify(s);
        verify(d);
    }

}
