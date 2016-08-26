/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;

import kilim.ExitMsg;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

/**
 * A slight extension to SimpleTask. This 
 * 
 * [compile] javac -d ./classes SimpleTask2.java
 * [weave]   java kilim.tools.Weave -d ./classes kilim.examples.SimpleTask
 * [run]     java -cp ./classes:./classes:$CLASSPATH  kilim.examples.SimpleTask2
 */
public class SimpleTask2 extends Task {
    static Mailbox<String> mb = new Mailbox<String>();
    static Mailbox<ExitMsg> exitmb = new Mailbox<ExitMsg>();
    
    public static void main(String[] args) throws Exception {
        Task t = new SimpleTask2().start();
        t.informOnExit(exitmb);
        mb.putnb("Hello ");
        mb.putnb("World\n");
        mb.putnb("done");
        
        exitmb.getb();
        System.exit(0);
    }

    /**
     * The entry point. mb.get() is a blocking call that yields
     * the thread ("pausable")
     */

    public void execute() throws Pausable{
        while (true) {
            String s = mb.get();
            if (s.equals("done")) break;
            System.out.print(s);
        }
        Task.exit(0); // Strictly optional.
    }
}
