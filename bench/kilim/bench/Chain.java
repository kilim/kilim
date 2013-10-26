/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench;

import kilim.*;

public class Chain extends Task {
    static class Mbx extends Mailbox<Integer>{}
    static int nTasks = 500;
    static int nMsgs = 10000;
    
    Mbx  mymb, nextmb;
    static long startTime;
    
    static Mailbox<Integer> signalMbx = new Mailbox<Integer>();
    
    
    public static void main(String[] args) {
//        Scheduler.setDefaultScheduler(new Scheduler(2)); // 2 threads.
        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equalsIgnoreCase("-nMsgs")) {
                    nMsgs = Integer.parseInt(args[++i]);
                } else if (arg.equalsIgnoreCase("-nTasks")) {
                    nTasks = Integer.parseInt(args[++i]);
                }
            }
        } 
        catch (NumberFormatException e) {
            System.err.println("Integer argument expected");
        }
        System.out.println("Num tasks in chain: " + nTasks + ". Num messages sent:" + nMsgs);
        for (int i = 0; i < 5; i++) {
          bench(nMsgs, nTasks);
        }
        System.exit(0);
    }
    
    static void bench(int nMsgs, int nTasks) {
        startTime = System.currentTimeMillis();
        
        Mbx mb = new Mbx();
        Mbx nextmb = null;
        // Create a chain of tasks.
        
        for (int i = 0; i < nTasks; i++) {
           Task t = new Chain(mb, nextmb);
           t.start();
           nextmb = mb;
           mb = new Mbx();
        }
        for (int i = 0; i < nMsgs; i++) {
            nextmb.putnb(0); // enqueue a message for the head of the chain.
        }
        signalMbx.getb();
        System.out.println("Bench finished");
        try {Thread.sleep(500);}catch (Exception ignore) {}
        System.gc();
        
    }
    
    public Chain(Mbx mb, Mbx next) {
        mymb = mb;
        nextmb = next;
    }
    
    int numReceived = 0;
    
    public void execute() throws Pausable {
        while (true) {
//          System.out.println("Waiting: # " + id());
            Integer val = mymb.get();
//          System.out.println("GET ===== # " + id());
            if (nextmb == null) {
                numReceived++;
                if (numReceived == nMsgs) {
                  done();
                  break;
                }
            } else {
                nextmb.put(val);
            }
        }
    }
    
    public void done() {
      System.out.println("Elapsed time: " + 
          (System.currentTimeMillis() - startTime)
          + " ms "); 
      signalMbx.putnb(0);
//      System.exit(0);
    }
  }
