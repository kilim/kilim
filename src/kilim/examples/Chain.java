/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;

import kilim.*;
/**
 * Set up a chain of tasks. Each task knows about its mailbox and
 * that of the next in the chain, but is not given the other tasks
 * ref.
 * The main thread pushes an empty StringBuffer into the first task's
 * mailbox, which writes "hello" into the buffer and passes the
 * modified StringBuffer on to the next task, and so on. The last
 * task appends "world", prints it out and exits.
 *
 * [compile] javac -d ./classes Chain.java
 * [weave]   java kilim.tools.Weave -d ./wclasses kilim.examples.Chain
 * [run]     java -cp ./wclasses:./classes:$CLASSPATH  kilim.examples.Chain
 * @author ram
 */
public class Chain extends Task {
    Mailbox<StringBuffer> mymb, nextmb;
    public Chain(Mailbox<StringBuffer> mb, Mailbox<StringBuffer> next) {
        mymb = mb; 
        nextmb = next;
    }
    

    public void execute() throws Pausable{
        while(true) {
            StringBuffer sb = mymb.get();
            if (nextmb == null) {
                System.out.print(sb);
                System.out.println("world");
                System.exit(0);
            } else {
                sb.append("hello ");
                nextmb.put(sb);
            }
        }
    }

    public static void main(String args[]) {
        int n = args.length == 0 ? 10 : Integer.parseInt(args[0]);
        Mailbox<StringBuffer> mb = new Mailbox<StringBuffer>();
        Mailbox<StringBuffer> nextms = null;
        for (int i = 0; i < n; i++) {
           new Chain(mb, nextms).start();
           nextms = mb;
           mb = new Mailbox<StringBuffer>();
        }
        nextms.putnb(new StringBuffer());        
    }
}
