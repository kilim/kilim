/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench;

public class Stopwatch {
    long lastTickMillis = System.currentTimeMillis();
    long lastElapsedMillis = 0;
    int  multiplier = 1; // see reportMicros
    String  unit = " ms";
    String name = "";
    
    
    public Stopwatch() {tick();}
    public Stopwatch(String nm) {name = nm; tick();}
    
    /**
     * @return the diff in millis from the last tick. Both tick and elapsed times are 
     * kept in millis
     */
    public long tick() {
        long l = lastTickMillis;
        lastTickMillis = System.currentTimeMillis();
        lastElapsedMillis = lastTickMillis - l;
        return lastElapsedMillis;
    }
    
    /**
     * Report in micros or millis 
     */
    public void reportMicros() {multiplier = 1000; unit = " micros";}
    public void reportMillis() {multiplier = 1; unit = " ms";}

    public long timePerIter(int iters) {
        return iters == 0? 0 : lastElapsedMillis*multiplier/iters;
    }
    
    long itersPerTime(int iters) {
        return lastElapsedMillis == 0 ? iters : iters/(lastElapsedMillis*multiplier);
    }
    
    public String toString() {
        return name + ": elapsed: " + (lastElapsedMillis * multiplier) + " " + unit;
    }
    
    public String toString(int iters) {
        return name + " elapsed: " + (lastElapsedMillis * multiplier) + 
            unit +", iters = " + iters + 
            ", " + unit + "/iter = " + timePerIter(iters) + 
            ", iters/" + unit + " = " + itersPerTime(iters);
    }
    
    public void print() {System.out.println(this);}
    
    public void print(int iters) { System.out.println(this.toString(iters));}

    public void tickPrint() {tick(); print();}
    
    public void tickPrint(int iters) {tick(); print(iters);}
}