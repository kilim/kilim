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

public class TestYield extends TestCase {
    
    public void testStackBottom_st() throws Exception {
        runTask(new kilim.test.ex.ExYieldStack(0));
    }

    public void testStackBottom_v() throws Exception {
        runTask(new kilim.test.ex.ExYieldStack(1));
    }
    
    public void testStackBottom_av() throws Exception {
        runTask(new kilim.test.ex.ExYieldStack(2));
    }
    
    public void testFactorial_st() throws Exception {
        runTask(new kilim.test.ex.ExYieldStack(3));
    }

    public void testFactorial_av() throws Exception {
        runTask(new kilim.test.ex.ExYieldStack(4));
    }
    
    public void testDupsInVars() throws Exception {
        runTask(new kilim.test.ex.ExYieldDups(0));
    }

    public void testDupsInStack() throws Exception {
        runTask(new kilim.test.ex.ExYieldDups(1));
    }

    public void testConstantsInStack() throws Exception {
        runTask(new kilim.test.ex.ExYieldConstants(0));
    }
    
    public void testLoop() throws Exception {
         kilim.test.ex.ExLoop ex = new kilim.test.ex.ExLoop();
         runTask(ex);
         assertTrue(ex.verify());
    }


    public static void runTask(String taskClassName, int testCase) throws Exception {
        ExYieldBase task;
        
        task = (ExYieldBase) (Class.forName(taskClassName).newInstance());
        task.testCase = testCase;
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
