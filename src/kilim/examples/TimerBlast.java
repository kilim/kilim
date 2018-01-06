/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;

import java.util.concurrent.atomic.AtomicInteger;
import kilim.Pausable;
import kilim.Task;

/*

TimerBlast and TimerBlast2 are examples that help reproduce race conditions
run them in a loop while simultaneously taxing the cpu eg with 2 maven builds of other projects looping
TimerBlast should alternate between "hello world" and "..."
under heavy external load it will miss a bunch of "hello worlds"

TimerBlast2 should take on the order of a second to print each line
under heavy external load it will hang
attach a debugger and explore the conditions causing the task to not complete

*/

public class TimerBlast extends Task {
    static AtomicInteger cnt = new AtomicInteger();

    
    
    public static class Tick extends Task {
        public void dive(int depth) throws Pausable {
            if (depth==0) Task.sleep(200);
            else dive(depth-1);
        }
        public void execute() throws Pausable {
            for (long ii=0, t1=0, t2=0; ii < 30; ii++, t1=t2) {
                dive(30);
                System.out.println("hello world");
            }
        }
        
    }
    
    public static void main(String[] args) throws Exception {
        int num = 1000;
        
        for (int ii=0; ii < 10; ii++) new TimerBlast().start();
        Thread.sleep(200);
        new Tick().start();
        Thread.sleep(190);
        for (int ii=0; ii < num; ii++) new TimerBlast().start();
        
        for (int ii=0; ii < 30; ii++) {
            System.out.println("...");
            Thread.sleep(200);
        }
 
        
        idledown();
        System.out.println(cnt.get());
    }

    public void execute() throws Pausable{
        Task.sleep(4000);
        cnt.incrementAndGet();
    }
}
