/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench;

import kilim.*;

public class LotsOfTasks {
    static boolean block;
    public static void main(String[] args) throws Exception {
        // Usage: java kilim.bench.LotsOfTasks [ntasks] [pause]
        // pause will tell each task to wait on its mailbox
        
        final int numTasks = (args.length > 0) ? Integer.parseInt(args[0]) : 100000;
        final boolean block = (args.length > 1) ? true : false;
        final Stopwatch s = new Stopwatch("Tasks(" + numTasks + ")");
        for (int round = 0; round < 10; round++) {
            s.tick();
            final Mailbox<ExitMsg> exitmb = new Mailbox<ExitMsg>();
                    
            System.out.println("Creating " + numTasks + (block ? " blocking tasks" : " tasks"));
            for (int i = 1; i <= numTasks; i++) {
                Task t = new LTask();
                t.informOnExit(exitmb);
                t.start();
                if (i % 100000 == 0) {
                    System.out.println("Created " + i + " tasks .... (contd.)");
                }
            }
            
            if (block) {
                for (int i = 1; i <= numTasks; i++) {
                    exitmb.getb();
                    if (i % 100000 == 0) {
                        System.out.println("Created " + i + " tasks .... (contd.)");
                    }
                };
            } 
            System.out.println("Round #" + round + " done. " + numTasks + " created in " + s.tick() + " ms");
            s.tickPrint(numTasks);
            System.gc();
            Thread.sleep(1000); // give the GC a chance.
        }
        System.exit(0);
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
