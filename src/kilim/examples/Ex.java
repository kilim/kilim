/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;

import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

/**
 * Spawn a task, communicate through a shared mailbox. The task's
 * termination is knowm through another mailbox.
 * 
 * The structure of this class is not much different from a Thread 
 * version that uses PipedInput/OutputStreams (Task instead of Thread,
 * execute() instead of run(), and typed, buffered mailboxes instead
 * of pipes.
 * 
 * [compile] javac -d ./classes Ex.java
 * [weave]   java kilim.tools.Weave -d ./classes kilim.examples.Ex
 * [run]     java -cp ./classes:./classes:$CLASSPATH  kilim.examples.Ex
 */
public class Ex extends Task {
    static Mailbox<String> mb = new Mailbox<String>();
    
    public static void main(String[] args) throws Exception {
        new Ex().start();
        Thread.sleep(10);
        mb.putnb("Hello ");
        mb.putnb("World\n");
        mb.putnb("done");
    }

    /**
     * The entry point. mb.get() is a blocking call that yields
     * the thread ("pausable")
     */

    public void execute() throws Pausable{
      for (int i = 0; i < 10; i++) {
        try {
          foo(i);
        } catch (Exception ignore) {
          System.out.println(i);
          foo(++i);
        }
      }
    }
  private void foo(int i) throws Pausable {
    if (i %2 == 0) {
      Task.sleep(100);
    } else {
      throw new RuntimeException();
    }
    
  }
}
