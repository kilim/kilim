/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench;

import kilim.*;


public class Sleep extends Task {
    static boolean pause = false;
    static boolean pausable = false;
    public static void main(String[] args) throws Exception {
        int n = Integer.parseInt(args[0]);
        
        sleep(); // waste 
        pausable = true;
        
        pause = true;
        testCont(new Sleep(50));
        long tpause = testCont(new Sleep(n));
        
        pause = false;
        testCont(new Sleep(50));
        long tnopause = testCont(new Sleep(n));

        pausable = false;
        testCont(new Sleep(50));
        long tbase = testCont(new Sleep(n));
        
//        System.out.println(n + " " + tbase + " " + tnopause + " " + tpause);
        System.out.println("n = " + n + " Not pausable: " + (tbase) + ", Not pausing: " + (tnopause) + ", Pausing: " + (tpause));
    }
    
    public static long testCont(Sleep ex) throws Exception {
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
    public Sleep(int an) {
        n = an;
    }

    public void execute() throws Pausable  {
        Fiber.pause();
        for (int i = 0; i < n; i++) {
            echo(i);
        }
    }

    private void echo(int x) throws Pausable  {
        sleep();
        if (pause) {
            Fiber.pause();
        }
    }

    public void noPauseRun() {
        for (int i = 0; i < n; i++) {
            echoNoPause(i);
        }
    }

    public void echoNoPause(int x) {
        sleep();
    }
    
    static void sleep() {
//        for (int i = 500000;i >= 0; i--) {
//            nop();
//        }
        try {Thread.sleep(1);}catch(Exception e) {}
    }
    
    public static void nop() {}

}
