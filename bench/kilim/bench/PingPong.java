/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench;

import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;
/*
 * This code is unnecessarily long for what it aims to do: bounce a message back
 * and forth between two tasks for a certain number of times. The reason for the
 * length is to compare against a similar example in the scala distribution.
 */
public class PingPong {

    public static void main(String[] args) {
        Mailbox<Msg> pingmb = new Mailbox<Msg>();
        Mailbox<Msg> pongmb = new Mailbox<Msg>();
        new Ping(pingmb, pongmb).start();
        new Pong(pongmb).start();
        pingmb.putnb(new Msg(MsgType.Init, 100000, null));
        pingmb.putnb(new Msg(MsgType.Start, pingmb));
    }
}

class Ping extends Task {
    Mailbox<Msg> mymb;
    Mailbox<Msg> pongmb;
    int origcount;
    int count;
    long beginTime;
    Ping(Mailbox<Msg> mb, Mailbox<Msg> pong) {
        mymb = mb;
        pongmb = pong;
    }
    
    public void execute() throws Pausable {
        while (true) {
            Msg m = mymb.get();
            switch(m.type) {
                case Init:
                    origcount = count = m.count;
                    beginTime = System.currentTimeMillis();
                    break;
                case Start:
                case PongMsg:
                    if (count > 0) {
//                        System.out.println("Ping: " + m + " " + count); System.out.flush();
//                        pongmb.put(new Msg(MsgType.PingMsg, mymb));
                        m.type = MsgType.PingMsg;
                        pongmb.put(m);
                        count--;
                    } else {
                        long  elapsed = System.currentTimeMillis() - beginTime;
                        System.out.println("Total time: " + elapsed + " millis, " 
                                + origcount + " rounds");
                        System.out.println("Time to send msg + context switch: " +
                                (elapsed * 1000.0 / 2 / origcount) + " micros");
                        System.exit(0);
                    }
                    break;
            }
        }
    }
}

class Pong extends Task {
    Mailbox<Msg> mymb;
    Pong(Mailbox<Msg> mb) {
        mymb = mb;
    }
    
    public void execute() throws Pausable {
        while (true) {
            Msg m = mymb.get();
//            System.out.println("Pong: " + m); System.out.flush();
            switch(m.type) {
                case PingMsg:
//                    m.mb.put(new Msg(MsgType.PongMsg, null));
                    m.type = MsgType.PongMsg;
                    m.mb.put(m);
                    break;
            }
        }
    }
}


enum MsgType {Init, Start, PingMsg, PongMsg};

class Msg {
    MsgType type;
    Mailbox<Msg> mb;
    int count; // for init
    Msg(MsgType t, int c, Mailbox<Msg> amb) {type = t; mb = amb; count = c;}
    Msg(MsgType t, Mailbox<Msg> amb) {type = t; mb = amb;}
    
    public String toString() {
        return "" + System.identityHashCode(this) + " " + type;
    }
}