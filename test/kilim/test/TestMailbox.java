/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import java.util.HashMap;
import java.util.HashSet;

import junit.framework.TestCase;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

public class TestMailbox extends TestCase { 
    public void testThread() {
        final Mailbox<Msg> mb = new Mailbox<Msg>();
        final int nThreads = 30;
        final int nTimes = 1000;
        final int nMsgs = nThreads * nTimes;
        for (int i = 0; i < nThreads ; i++) {
            final int id = i;
            new Thread() {
                public void run() {
                    for (int j = 0; j < nTimes; j++) {
                        mb.putnb(new Msg(id, j));
                        Thread.yield();
                    }
                }
            }.start();
        }
        
        
        HashMap<Integer, Integer> lastRcvd = new HashMap<Integer,Integer>();
        int nNewThreads = 0;
        for (int i = 0; i < nMsgs; i++) {
            Msg m = mb.getb();
            // assert that the number received is one more than the last received 
            // from that thread.
            Integer last = lastRcvd.put(m.tid, m.num);
            if (last == null) {
                nNewThreads++;
            } else {
                assertTrue(m.num == last.intValue() + 1);
            }
        }
        assertTrue(nNewThreads == nThreads);
        // Make sure we have heard from all threads
        assertTrue(lastRcvd.keySet().size() == nThreads);
        int lastVal = nTimes - 1;
        for(Integer tid : lastRcvd.keySet()) {
            Integer v = lastRcvd.get(tid);
            assertTrue(v != null);
            assertTrue(v.intValue() == lastVal);
        }
        try {Thread.sleep(1000);} catch (InterruptedException ignore) {}
        // Make sure there are no extra messages floating around.
        assertTrue(mb.getnb() == null); 
    }
    
    public void testSimpleTask_NotPausing() {
        final int numMsgs = 100;
        Mailbox<Msg> mainmb = new Mailbox<Msg>();
        
        TaskMB_NoPause t = new TaskMB_NoPause(mainmb, numMsgs);
        t.start(); 
        for (int i = 0; i < numMsgs ; i++) {
            Msg m = mainmb.getb(1000);
            assertTrue(m.num == i);
        }
    }

    public void testSimpleTask_Pausing() {

        Mailbox<Msg> mainmb = new Mailbox<Msg>();
        
        final int nTasks = 1;
        final int nTimes = 1000;
        final int nMsgs = nTasks * nTimes;
        
        for (int i = 0; i < nTasks ; i++) {
            TaskMB t = new TaskMB(mainmb);
            t.start(); 
            t.mymb.putnb(new Msg(i, nTimes));
        }
        
        HashMap<Integer, Integer> lastRcvd = new HashMap<Integer,Integer>();
        int nNewThreads = 0;
        for (int i = 0; i < nMsgs; i++) {
            Msg m = mainmb.getb();
            // assert that the number received is one more than the last received 
            // from that thread.
            Integer last = lastRcvd.put(m.tid, m.num);
            if (last == null) {
                nNewThreads++;
            } else {
                assertTrue(m.num == last.intValue() + 1);
            }
        }
        assertTrue(nNewThreads == nTasks);
        // Make sure we have heard from all threads
        assertTrue(lastRcvd.keySet().size() == nTasks);
        int lastVal = nTimes - 1;
        for(Integer tid : lastRcvd.keySet()) {
            Integer v = lastRcvd.get(tid);
            assertTrue(v != null);
            assertTrue(v.intValue() == lastVal);
        }
        try {Thread.sleep(1000);} catch (InterruptedException ignore) {}
        // Make sure there are no extra messages floating around.
        assertTrue(mainmb.getnb() == null); 

    }
    
    
    public void testMbxBounds() {
        Mailbox<Msg> mainmb = new Mailbox<Msg>(2, 2);
        
        TaskMB t = new TaskMB(mainmb);
        t.start();
        t.mymb.putnb(new Msg(1, 100));
        for (int i = 0; i < 100; i++) {
            if (i % 5 == 0) {
                // Every so often, make sure that the task is forced to block on put, by delaying draining the mbx
                try {Thread.sleep(100);} catch (InterruptedException ignore) {}
            }
            mainmb.getb();
        }
        try {Thread.sleep(500);} catch (InterruptedException ignore) {}
        assertTrue(mainmb.getnb() == null);
    }
        
    public void testTasks() {

        Mailbox<Msg> mb = new Mailbox<Msg>();
        
        final int nTasks = 100;
        final int nTimes = 1000;
        final int nMsgs = nTasks * nTimes;
        
        for (int i = 0; i < nTasks ; i++) {
            TaskMB t = new TaskMB(/*mainmb=*/mb);
            t.start();
            t.mymb.putnb(new Msg(i, nTimes));
        }
        
        HashMap<Integer, Integer> lastRcvd = new HashMap<Integer,Integer>();
        int nNewThreads = 0;
        for (int i = 0; i < nMsgs; i++) {
            Msg m = mb.getb();
            // assert that the number received is one more than the last received 
            // from that thread.
            Integer last = lastRcvd.put(m.tid, m.num);
            if (last == null) {
                nNewThreads++;
            } else {
                assertTrue(m.num == last.intValue() + 1);
            }
        }
        assertTrue(nNewThreads == nTasks);
        // Make sure we have heard from all threads
        assertTrue(lastRcvd.keySet().size() == nTasks);
        int lastVal = nTimes - 1;
        for(Integer tid : lastRcvd.keySet()) {
            Integer v = lastRcvd.get(tid);
            assertTrue(v != null);
            assertTrue(v.intValue() == lastVal);
        }
        try {Thread.sleep(1000);} catch (InterruptedException ignore) {}
        // Make sure there are no extra messages floating around.
        assertTrue(mb.getnb() == null); 
    }
    
    // Send messages on two mailboxes and collect them back on one mailbox.
    public void testSelectSimple() {
        Mailbox<Msg> mainmb = new Mailbox<Msg>();
        SelectTaskMB t = new SelectTaskMB(mainmb);
        t.start();

        // Make sure the task is blocked on select and hasn't already
        // sent us a message
        try {Thread.sleep(100);} catch (InterruptedException ignore) {}
        assertTrue(! mainmb.hasMessage());
        HashSet<Msg> sentMsgs = new HashSet<Msg>();
        final int n = 10;
        for (int i = 0; i < n; i++) {
            Msg m = new Msg();
            assertTrue(t.mymb2.putnb(m));
            sentMsgs.add(m);
            try {Thread.sleep(10);} catch (InterruptedException ignore) {}
            m = new Msg();
            assertTrue(t.mymb1.putnb(m));
            sentMsgs.add(m);
        }
        for (int i = 0; i < n*2; i++) {
            Msg m = mainmb.getb(1000);
            assertTrue(m != null && sentMsgs.contains(m));
        }
    }
}

class Msg {
    int tid; // thread or task id
    int num;
    Msg(){}
    Msg(int id, int n) {tid = id; num = n;}
    public String toString() {
        return "Msg(" + tid + "," + num + ")";
    }
};


class TaskMB extends Task {
    Mailbox<Msg> mymb;
    Mailbox<Msg> mainmb;
    
    TaskMB(Mailbox<Msg> ms) {
        mymb = new Mailbox<Msg>();
        mainmb = ms;
    }
    
    public void execute() throws Pausable {
        Msg m = mymb.get();
        assert m != null : "task rcvd null msg";
        int id = m.tid; // Receive this task's id
        int n = m.num;  // Receive the number of times we have to loop
        
        for (int i = 0; i < n; i++) {
            mainmb.put(new Msg(id, i));
            if (i % 10 == 0) {
                Task.yield();
            }
        }
    }
}


/** 
 * A Task that only makes nonpausing calls.
 * @author s
 */
class TaskMB_NoPause extends Task {
    Mailbox<Msg> mainmb;
    int numMsgs;
    
    TaskMB_NoPause(Mailbox<Msg> ms, int numMsgs) {
        mainmb = ms;
        this.numMsgs = numMsgs;
    }
    
    public void execute() throws Pausable {
        int n = numMsgs;
        
        for (int i = 0; i < n; i++) {
            mainmb.putnb(new Msg(id, i));
        }
    }
}



class SelectTaskMB extends Task {
    Mailbox<Msg> mymb1, mymb2;
    Mailbox<Msg> mainmb;
    
    SelectTaskMB(Mailbox<Msg> mb) {
        mymb1 = new Mailbox<Msg>();
        mymb2 = new Mailbox<Msg>();
        mainmb = mb;
    }
        
    public void execute() throws Pausable  {
        while (true) {
            Msg m;
            // Receive a message on mymb1 or 2 and forward to mainmb
            // If some error, send a dummy message and testSelect()
            // will flag an error.
            switch (Mailbox.select(mymb1, mymb2)) {
                case 0: 
                    m = mymb1.getnb();
                    mainmb.put(m);
                    break;
                case 1:
                    m = mymb2.getnb();
                    if (m == null) m = new Msg();
                    mainmb.put(m);
                    break;
                default: 
                    mainmb.put(new Msg());
            }
        }
    }
}