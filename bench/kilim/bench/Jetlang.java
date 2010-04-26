/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;
/**
 * Compare this to Jetlang's PerfMain tests
 * See http://code.google.com/p/jetlang/
 */
public class Jetlang extends Task {
    
    /* limit number  of msgs in mailbox */
    static Mailbox<Integer> mb = new Mailbox<Integer>(1000,1000);
    final static int max = 5000000;
    
    public static void main(String args[]) throws Exception {
        Stopwatch s = new Stopwatch();
        s.tick();

        Task t = new Jetlang().start();
        new Publisher().start();
        t.joinb(); // wait for receiver to finish
        
        s.tickPrint(max+1); // same number of iterations as jetlang's tests.
    }

    public void execute() throws Pausable {
        while (true) {
            int i = mb.get();
            if (i == max) {
                break;
            }
        }
    }
    
    static class Publisher extends Task {
        public void execute() throws Pausable {
            for (int i = 0; i <= max; i++) {
                mb.put(i);
          }
        }
    }
}

