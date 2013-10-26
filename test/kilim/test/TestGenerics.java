/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import junit.framework.TestCase;
import kilim.ExitMsg;
import kilim.Mailbox;
import kilim.Scheduler;
import kilim.Task;
import kilim.test.ex.ExYieldBase;

public class TestGenerics extends TestCase {
    
    public void testGenerics() throws Exception {
        ExYieldBase task;
        
        task = (ExYieldBase) (Class.forName("kilim.test.ex.ExGenerics").newInstance());
        runTask(task);
    }

    public static void runTask(Task task) throws Exception {
        Mailbox<ExitMsg> exitmb = new Mailbox<ExitMsg>();
        Scheduler s = new Scheduler(1);
        task.informOnExit(exitmb);
        task.setScheduler(s); 
        task.start();
        
        ExitMsg m = exitmb.getb();
        if (m == null) {
            fail("Timed Out");
        } else {
            Object res = m.result;
            if (res instanceof Throwable) {
                ((Throwable)res).printStackTrace();
                fail(m.toString());
            }
        }
        s.shutdown();
    }
}
