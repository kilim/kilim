package kilim.test.ex;

import kilim.Task;

public class ExYieldBase extends Task {
    public static int        fi          = 10;
    public static double     fd          = 10.0;
    public static long       fl          = 10L;
    public static float      ff          = 10.0f;
    public static char       fc          = 10;
    public static byte       fb          = 10;
    public static short      fsh         = 10;
    public static String     fs          = "10";
    public static String[][] fa          = { { "10" } };

    public boolean doPause = false;
    
    public int testCase; // set by TestYield.runTask

    public static void verify(int i) {
    	verify(i, fi);
    }

    public static void verify(int i, int compareTo) {
        if (i != compareTo) {
            throw new RuntimeException("i = " + i);
        }
    }
    
    public static void verify(short s, short compareTo) {
        if (s != compareTo) {
            throw new RuntimeException("s = " + s);
        }
    }
    
    public static void verify(short s) {
    	verify(s, fsh);
    }

    
    public static void verify(double d) {
    	verify(d, fd);
    }
    
    public static void verify(double d, double compareTo) {
        if (d != compareTo) {
            throw new RuntimeException("d = " + d);
        }
    }

    public static void verify(long l) {
    	verify(l, fl);
    }

    public static void verify(long l, long compareTo) {
        if (l != compareTo) {
            throw new RuntimeException("l = " + l);
        }
    }

    public static void verify(char c) {
        if (c != fc) {
            throw new RuntimeException("c = " + c);
        }
    }

    
    public static void verify(float f) {
    	verify(f, ff);
    }
    
    public static void verify(float f, float compareTo) {
        if (f != compareTo) {
            throw new RuntimeException("f = " + f);
        }
    }

    public static void verify(byte b) {
        if (b != fb) {
            throw new RuntimeException("b = " + b);
        }
    }

    public static void verify(String s) {
        if (s != fs) {
            throw new RuntimeException("s = " + s);
        }
    }

    public static void verify(String[][] a) {
        if (a != fa) {
            throw new RuntimeException("a = " + a);
        }
    }
}
