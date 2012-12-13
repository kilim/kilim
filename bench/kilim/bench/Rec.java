/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench;

import kilim.*;
    
public class Rec extends Task {
    static boolean pause = false;
    static boolean pausable = false;
    public static void main(String[] args) throws  Exception {
        int n = Integer.parseInt(args[0]);
        int d = Integer.parseInt(args[1]);
        
        pausable = true;
        
        pause = true;
        testCont(new Rec(5,5));
        long tpause = testCont(new Rec(n, d));
        
        pause = false;
        testCont(new Rec(5,5));
        long tnopause = testCont(new Rec(n, d));

        pausable = false;
        testCont(new Rec(5, 5));
        long tbase = testCont(new Rec(n, d));
        System.out.println(n + " " + tbase + " " + tnopause + " " + tpause);
    }
    
    public static long testCont(Rec ex) throws NotPausable, Exception {
        long start = System.currentTimeMillis();
        if (pausable) {
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
    int depth;
    public Rec(int an, int aDepth) {
        n = an;
        depth = aDepth;
    }

    public void execute() throws Pausable {
        for (int i = 0; i < n; i++) {
            rec(depth, "foo");
        }
    }

    public void noPauseRun() {
        for (int i = 0; i < n; i++) {
            recNoPause(depth, "foo");
        }
    }

    private void rec(int d, String s) throws Pausable {
        if (d == 1) {
            if (pause) {
                Task.yield();
            }
            return;
        }
        rec(d-1, s);
    }

    private void recNoPause(int d, String s) {
        if (d == 1) {
            return;
        }
        recNoPause(d-1, s);
    }

}
