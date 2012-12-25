/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench;

import kilim.*;


public class Unwind extends Task {
    static boolean pause = false;
    static boolean pausable = false;
    public static void main(String[] args) throws Exception {
        int n = Integer.parseInt(args[0]);
        pausable = true;
        
        pause = true;
        testCont(new Unwind(50));
        long tpause = testCont(new Unwind(n));
        
        pause = false;
        testCont(new Unwind(50));
        long tnopause = testCont(new Unwind(n));

        pausable = false;
        testCont(new Unwind(50));
        long tbase = testCont(new Unwind(n));
        
//        System.out.println(n + " " + tbase + " " + tnopause + " " + tpause);
        System.out.println("n = " + n + " Not pausable: " + (tbase) + ", Not pausing: " + (tnopause) + ", Pausing: " + (tpause));
    }
    
    public static long testCont(Unwind ex) throws Exception {
        System.gc();
        try {Thread.sleep(100);}catch (Exception e) {}

        long start = System.currentTimeMillis();
        if (pausable) {
            // Manually doing what the scheduler would do, just to cut out the
            // thread scheduling.
            Fiber f = new Fiber(ex);
            while (true) {
                ex.execute(f.begin());
                if (f.end()) break;
            }
        } else {
            ex.noPauseRun();
        }
        return (System.currentTimeMillis() - start);
    }
    
    int n;
    public Unwind(int an) {
        n = an;
    }

    public void execute() throws Pausable  {
        for (int i = 0; i < n; i++) {
            echo(i);
        }
    }

    private void echo(int x) throws Pausable {
        long l = x - (x - 2);
        String foo = new String("foo");
        String bar = "bar";
        if (pause) {
            Task.yield();
        }
        foo.charAt((int)l);
        bar.charAt((int)l);
    }

    public void noPauseRun() {
        for (int i = 0; i < n; i++) {
            echoNoPause(i);
        }
    }

    public void echoNoPause(int x) {
        long l = x - (x - 2);
        String foo = new String("foo");
        String bar = "bar";
        foo.charAt((int)l);
        bar.charAt((int)l);
    }
}
