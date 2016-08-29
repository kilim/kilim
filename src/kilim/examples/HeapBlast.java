/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;

import java.util.Random;
import kilim.timerservice.Timer;
import kilim.timerservice.TimerPriorityHeap;

public class HeapBlast {
    
    
    static Random rand = new Random(0L);
    int num = 1000;
    int size = 0;

    Timer [] sorted = new Timer[num];
    Timer [] polled = new Timer[num];

    TimerPriorityHeap heap;

    
    void claim(boolean test) {
        if (! test)
            throw new RuntimeException();
    }
    
    void poll() {
        if (size==0) return;
        Timer t1 = heap.peek();
        heap.poll();
        int ii;
        for (ii = 0; sorted[ii] != t1; ii++)
            claim(polled[ii] != null);
        polled[ii] = t1;
        size--;
    }
    
    void add() {
        if (size==num) return;
        int index;
        while (polled[index = rand.nextInt(num)]==null) {}
        heap.add(polled[index]);
        polled[index] = null;
        size++;
    }

    HeapBlast() {
        for (int ii=0; ii < num; ii++)
            (polled[ii] = sorted[ii] = new Timer(null)).setTimer(ii);
    }
    
    void build() {
        heap = new TimerPriorityHeap();
        for (int ii=0; ii < num; ii++)
            (polled[ii] = sorted[ii] = new Timer(null)).setTimer(ii);
        size = 0;
        test();
    }

    void step(int target) {
        boolean flip = rand.nextInt(16) > 13;
        boolean up = (target >= size) ^ flip;
        if (up) add();
        else poll();
    }
    
    void test() {
        for (int ii=0; ii < 1000; ii++) {
            boolean mode = rand.nextInt(16) > 7;
            int target = mode ? size+rand.nextInt(15)-7 : 1+rand.nextInt(num);
            if (mode)
                for (int jj=0; jj < 100; jj++) step(target);
            else
                while (size != target) step(target);
        }
    }
    
    
    public static void main(String[] args) throws Exception {
        HeapBlast blast = new HeapBlast();

        for (int ii=0; ii < 100; ii++)
            blast.build();
    }

}
