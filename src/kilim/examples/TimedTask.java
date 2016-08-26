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
 * Creates lots of tasks that print stuff, sleep, then wake up and print more.
 * 
 * [compile] javac -d ./classes TimedTask.java
 * [weave]   java kilim.tools.Weave -d ./wlasses kilim.examples.TimedTask
 * [run]     java -cp ./wlasses:./classes:$CLASSPATH  kilim.examples.TimedTask
 * 
 * @author sriram@malhar.net
 */
public class TimedTask extends Task {
    public static void main(String[] args) throws Exception {
        int numTasks = (args.length > 0) ? Integer.parseInt(args[0]) : 100;
        Mailbox<ExitMsg> exitmb= new Mailbox<ExitMsg> ();
        
        for (int i = 0; i < numTasks; i++) { 
            new TimedTask().start().informOnExit(exitmb);
        }
        
        for (int i = 0; i < numTasks; i++) { 
            exitmb.getb();
        }
        
        System.exit(0);
    }
    
    public void execute() throws Pausable {
        System.out.println("Task #" + id() + " going to sleep ...");
        Task.sleep(2000);
        System.out.println("           Task #" + id() + " waking up");
    }
}
