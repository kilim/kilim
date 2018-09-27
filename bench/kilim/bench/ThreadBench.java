/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench;

public class ThreadBench extends Thread {
    ThreadBench next;
    int val = -1; // This value is filled by the previous process before this process is
    
    public static boolean tracing = false;
    static long startTime;
    
    public static void main(String[] args) {
        int n = 500;
        int k = 10000;
        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("-k")) {
                    k = Integer.parseInt(args[++i]);
                } else if (arg.equals("-n")) {
                    n = Integer.parseInt(args[++i]);
                } else if (arg.equals("-t")) {
                    tracing = true;
                }
            }
        } 
        catch (NumberFormatException e) {
            System.err.println("Integer argument expected");
        }
        startTime = System.currentTimeMillis();
        bench(k, n);
    }
    
    static void bench(int k, int n) {
        Sink sink = new Sink();
        ThreadBench next = sink;
        for (int i = n; i >= 1; i--) {
            next.start();
            next = new Copy(i, next);
        }
        next.start();
        Source source = new Source(k, next);
        sink.source = source;
        source.start();
        try {
            source.join();
        } catch (InterruptedException ie) {}
        System.out.println("Done");
    }
    
    ThreadBench() {
    }
    
    synchronized void write(int v) {
        while (val != -1) {
            try {
                wait();
            } catch (InterruptedException ie) {ie.printStackTrace();}
        }
        val = v;
        notify();
    }
    
    synchronized int read() {
        while (val == -1) {
            try {
                wait();
            } catch (InterruptedException ie) {ie.printStackTrace();}       
        }
        int v = val;
        val = -1;
        notify();
        return v;
    }
    
    static class Copy extends ThreadBench {
        int  id;
        // woken up
        Copy(int aid, ThreadBench p) {
            id = aid;
            next = p;
        }
        
        public void run() {
            while (true) {
                int v = read();
                if (tracing) 
                    System.out.println(this.toString() + " copying number " + v);
                next.write(v);
                if (v == 0) break; 
            }
        }
        public String toString() {return "copy " + id;}
    }

    static class Sink extends ThreadBench {
        ThreadBench source;
        public  void run() {
            int v;
            int i = 0;
            while (true) {
                v = read();
                i++;
                if(tracing) 
                    System.out.printf("sink: receiving number %d\n-----\n", v);
                if (v == 0) {
                    System.out.println("Elapsed time: " + 
                            (System.currentTimeMillis() - startTime)
                            + " ms, iterations = " + i);
                    System.exit(0);
                }
            }
        }
        public String toString() {return "sink";}
    }

    static class Source extends ThreadBench {
        int loops;

        Source(int k, ThreadBench p) {
            loops = k;
            next = p;
        }
        
        public void run() {
            for (int i = 1; i <= loops; i++) {
                if(tracing) 
                    System.out.printf("source: sending number %d\n", i);
                next.write(i);
            }
            // Kill
            next.write(0);
        }
        public String toString() {return "source";}
    }
}

