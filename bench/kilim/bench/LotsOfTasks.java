/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench;

import kilim.*;

// Usage: java kilim.bench.LotsOfTasks -ntasks  
//                 creates ntasks and waits for them to finish
// Or     
//Usage: java kilim.bench.LotsOfTasks ntasks pause 
//          creates ntasks, which in turn block indefinitely on their mailboxes.
public class LotsOfTasks {
    static boolean block;
    static int nTasks = 100000;
    static int nRounds = 10;

    public static void main(String[] args) throws Exception {
        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equalsIgnoreCase("-nRounds")) {
                    nRounds = Integer.parseInt(args[++i]);
                } else if (arg.equalsIgnoreCase("-nTasks")) {
                    nTasks = Integer.parseInt(args[++i]);
                } else if (arg.equalsIgnoreCase("-block")) {
                    block = true;
                }
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
            System.exit(0);
        }
        System.out.println("kilim.bench.LotsOfTasks -nTasks " + nTasks + (block ? " -block": "") + " -nRounds " + nRounds);

        final Stopwatch s = new Stopwatch("Tasks(" + nTasks + ")");
        for (int round = 1; round <= nRounds; round++) {
            System.out.println("Round #" + round + " ================= ");
            s.tick();
            final Mailbox<ExitMsg> exitmb = new Mailbox<ExitMsg>();

            System.out.println("Creating " + nTasks + (block ? " blocking tasks" : " tasks"));
            for (int i = 1; i <= nTasks; i++) {
                Task t = new LTask();
                t.informOnExit(exitmb);
                t.start();
                if (i % 100000 == 0) {
                    System.out.println("  created " + i + " tasks .... (contd.)");
                }
            }
            profilerMark(); // dummy method to study memory consumption at this stage
            if (!block) {
                System.out.println("Waiting for completion");
                for (int i = 1; i <= nTasks; i++) {
                    exitmb.getb();
                    if (i % 100000 == 0) {
                        System.out.println("  " + i + " tasks finished.... (contd.)");
                    }
                }
                ;
            }
            System.out.println("Round #" + round + " done:");
            System.out.print("  ");
            s.tickPrint(nTasks);
            System.gc();
            Thread.sleep(100); // give the GC a chance.
        }
        System.exit(0);
    }

    public static void profilerMark() {
        // dummy method to help as a profiler breakpoint in JProfiler.
    }
}

class LTask extends Task {
    Mailbox<String> mymb = new Mailbox<String>();

    public void execute() throws Pausable {
        if (LotsOfTasks.block) {
            mymb.get();
        }
    }

}
