/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench;
import kilim.*;
public class BigPingPong extends Task {
    static Mailbox<Msg>[] mboxes;
    static Mailbox<Msg> mainmb;

    
    @SuppressWarnings("unchecked")
    public static void main(String args[]) throws Exception {
        boolean noargs = args.length == 0;
        int nTasks = noargs ? 10 : Integer.parseInt(args[0]);
        int nSchedulers = noargs ? 1 : Integer.parseInt(args[1]);
        int nThreadsPerScheduler = noargs ? 1 : Integer.parseInt(args[2]);
        Scheduler [] schedulers = new Scheduler[nSchedulers];
        
        System.out.println("nTasks : " + nTasks + ", nSchedulers: " + nSchedulers + 
                ", nThreadsPerScheduler: " + nThreadsPerScheduler);
        
        for (int c = 0; c < 13; c++) { // Timing loop
            long beginTime = System.currentTimeMillis();
            mboxes = new Mailbox[nTasks];
//            mainmb = new Mailbox<Msg>(/* initial size = */ nTasks);
            mainmb = new Mailbox<Msg>(/* initial size = */ nTasks, nTasks);
            for (int i = 0; i < nTasks; i++) {
                mboxes[i] = new Mailbox<Msg>(/* initial size = */ nTasks, nTasks);
            }

            for (int i = 0 ; i < nSchedulers; i++) {
                schedulers[i] = new Scheduler(nThreadsPerScheduler);
            }
            
            BigPingPong[] tasks = new BigPingPong[nTasks];

            for (int i = 0; i < nTasks; i++) {
                BigPingPong t = new BigPingPong(i);
                tasks[i] = t;
                t.setScheduler(schedulers[i % nSchedulers]);
                t.start();
            }
            
            for (int i = 0; i < nTasks; i++) {
//                mainmb.getWait();
                Msg m = mainmb.getb(20000);
//              Msg m = mainmb.getb();
                if (m == null) {
                    System.err.println("TIME OUT (20s). No of tasks finished: " + i);
//                    for (BigPingPong t: tasks) {
//                        System.err.println(t);
//                    }
//                    for (Mailbox mb: mboxes) {
//                        System.err.println(mb);
//                    }
                    System.exit(1);
                }
            }
            // Total number of messages: from each task to every other task,
            // and finally one to the main mailbox to signal completion
            // nTasks * (nTasks - 1) + nTasks
            int nMessages = nTasks * nTasks; 
            System.out.println("Elapsed ms (" + nTasks + " tasks, " + 
                    nMessages + " messages) " + (System.currentTimeMillis() - beginTime));
            System.gc();
            Thread.sleep(1000);
            for (int i = 0; i < nSchedulers; i++) {
                schedulers[i].shutdown();
            }
        }
        System.exit(0);
    }

    int n; // Task's position in the slot array
    BigPingPong(int num) {
        n = num;
    }
    
    boolean done = false;
    int  numRcvd = 0;
    
    public void execute() throws Pausable {
        done = false;
        int l = mboxes.length;
        Msg mymsg = new Msg(id);
        
        int me = n;
        Mailbox<Msg> mymb = mboxes[me];
        
        for (int i = 0; i < l; i++) {
            if (i == me)
                continue;
            mboxes[i].put(mymsg);
        }
        for (int i = 0; i < l - 1; i++) {
            Msg m = mymb.get();
            assert m != null;
            numRcvd++;
        }
        mainmb.put(mymsg);
        done = true;
    }
    
    private static class Msg {
        static Msg gMsg = new Msg(0);
        int from;
        Msg(int f) {from = f;}
    };
}

