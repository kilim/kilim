/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench;

import kilim.*;

public class Ring extends Task {
    Mailbox<String> mb;
    Mailbox<String> prev;
    int  times; // num times already gone around. When 0, won't pass it on.
    int  num;  // this task's number

    public static boolean logging = false;
    static long startTime;
    public static void main(String[] args) {
        int n = 10; // num elements in ring.
        int t = 100000; // num times around ring
        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("-n")) {
                    n = Integer.parseInt(args[++i]);
                } else if (arg.equals("-t")) {
                    t = Integer.parseInt(args[++i]);
                } else if (arg.equals("-l")) {
                    logging = true;
                }
            }
        } 
        catch (NumberFormatException e) {
            System.err.println("Integer argument expected");
        }
        if (logging) System.out.println("Started");
        Mailbox<String> mb = new Mailbox<String>();
        Mailbox<String> startmb = mb;
        Ring r = new Ring(mb, null, 0, t);
        r.start();
        Ring start = r;
        Mailbox<String> prevmb = mb;
        for (int i = 1; i < n; i++) {
            mb = new Mailbox<String>();
            new Ring(mb, prevmb,  i, t).start();
            prevmb = mb;
        }
        start.prev = prevmb;
        startTime = System.currentTimeMillis();
        startmb.putnb("ring");
    }
    
    public Ring(Mailbox<String> amb, Mailbox<String> prevms,int anum, int atimes) {
        mb = amb;
        num = anum; 
        times = atimes;
        prev = prevms;
        if (logging) {
            System.out.println("Proc# " + anum);
        }
    }

    public void execute() throws Pausable {
        while (true) {
            String m = mb.get();
            if (logging) 
                System.out.println(" Proc # " + num + ", iters left = " + times);
            if (--times == 0) {
                if (num == 1) { // last process
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    System.out.println("Elapsed time: " + elapsedTime + " ms");
                    System.exit(0);
                }
            }
            prev.put(m);
        }
    }
}


