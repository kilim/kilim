/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;

import java.util.Random;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.Task;

public class TimerBlast2 {
    int num;
    int delay;
    int spread;

    static Random rand = new Random();
    
    
    public static class Tick extends Task {
        int delay;
        int count;
        boolean stop = false;
        Mailbox<Long> box = new Mailbox();
        
        Tick(int $delay) { delay = $delay; }
        public void dive(int depth) throws Pausable {
            if (depth==0) {
                stop = (box.get(delay) != null);
                count++;
            }
            else
                dive(depth-1);
        }
        public void execute() throws Pausable {
            long start = System.currentTimeMillis();
            while (!stop)
                dive(count & 0x1f);
            long finish = System.currentTimeMillis();
            System.out.println("Tick: " + 1.0*(finish-start)/count/delay);
        }
        
    }
    static public class Info {
        volatile int count;
        volatile boolean stop;
    }
    
    Object monitor = new Object();
    int nthreads = Scheduler.getDefaultScheduler().numThreads();
    Info [] infos = new Info[nthreads];
    public class Tock extends Task {
        int delta;
        boolean stop = false;
        boolean check;
        Mailbox<Long> box = new Mailbox();
        public void dive(int depth) throws Pausable {
            if (depth==0)
                box.get(delta);
            else dive(depth-1);
        }
        public void execute() throws Pausable {
            while (! stop) {
                dive(30);
                delta = delay-delta;
                dive(10);
                int tid = getTid();
                infos[tid].count++;
                stop = (box.get()==1);
            }
        }
        Tock(int $delta) { delta = $delta; }
    }

    Tock [] tocks;
    void setup(int $num) throws Exception {
        num = $num;
        tocks = new Tock[num];
        delay = 1000;
        spread = 200;
        System.out.println("-------------------------- " + num);

        for (int ii=0; ii < nthreads; ii++) 
            infos[ii] = new Info();
        
        for (int ii=0; ii < num; ii++) {
            int delta = delay/2 - spread + rand.nextInt(2*spread+1);
            tocks[ii] = new Tock(delta);
        }
        for (int ii=0; ii < num; ii++) tocks[ii].start();

        Tick tick = new Tick(20);
        tick.start();
        
        for (int ii=10; ii >= 0; ii--)
            loop(ii==0 ? 1:0);
        
        tick.box.putb(0L);
        tick.joinb();
    }

// Scheduler.defaultScheduler.affinePool_.queues_.get(0).add(new kilim.timerservice.TimerService.WatchdogTask())
    
    long prev = 0;
    void loop(long val) throws Exception {
        long start = System.currentTimeMillis();
        while (true) {
            Thread.sleep(10);
            int sum = 0;
            for (int ii=0; ii < nthreads; ii++) sum += infos[ii].count;
            if (sum==num) break;
        }
        for (int ii=0; ii < nthreads; ii++) infos[ii].count = 0;

        long finish = System.currentTimeMillis();
        System.out.println("time: " + (start-prev) + " -- " + (finish-start));
        broadcast(val);
        prev = finish;
    }
    void broadcast(long val) {
        for (int ii=0; ii < num; ii++) 
            tocks[ii].box.putb(val);
    }
    
    
    public static void main(String[] args) throws Exception {
        int num = 4096 << 6;

        
        

        for (int ii=0; ii < 1; ii++)
            new TimerBlast2().setup(num << ii);
        
        Task.idledown();
    }

}
