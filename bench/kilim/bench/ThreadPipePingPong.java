/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;


// Create two threads, have a message ping pong between them using pipes.
public class ThreadPipePingPong {
    public static void main(String args[]) throws Exception {
        int ntimes = args.length == 0 ? 1000 : Integer.parseInt(args[0]);
        PipedInputStream  pi_in = new PipedInputStream();
        PipedOutputStream pi_out = new PipedOutputStream();
        PipedInputStream  po_in = new PipedInputStream(pi_out);
        PipedOutputStream po_out = new PipedOutputStream(pi_in);
        
        PongThread po = new PongThread(po_in, po_out);
        PingThread pi = new PingThread(ntimes, pi_in, pi_out);
        po.start();
        pi.start();
    }
}

class PingThread extends Thread {
    int ntimes;
    PipedInputStream in;
    PipedOutputStream out;
    PingThread(int n, PipedInputStream i, PipedOutputStream o) {
        ntimes = n;
        in  = i;
        out = o;
    }
    public void run() {
        try {
            long begin = System.currentTimeMillis();
            for (int i = 0; i < ntimes; i++) {
                out.write(100); out.flush(); //ping
                in.read(); // wait for pong
            }
            System.out.println("Elapsed (" + ntimes + " iters) : " + 
                    (System.currentTimeMillis() - begin) + " millis");
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class PongThread extends Thread {
    PipedInputStream in;
    PipedOutputStream out;

    PongThread(PipedInputStream i, PipedOutputStream o) {
        in = i;
        out = o;
    }
    public void run() {
        try {
            while (true) {
                in.read();
                out.write(200); out.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
