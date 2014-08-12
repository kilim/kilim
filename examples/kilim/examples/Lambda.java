/* Copyright (c) 2014, seth lytle
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim.examples;

import kilim.Pausable;
import kilim.Task;


/**
 * Demo of passing lambda expressions as kilim tasks
 */
public class Lambda {
    static Tasklet greeter = () -> {
        Task.sleep(1000);
        System.out.println("foobar");
        Task.sleep(1000);
        System.out.println("hello");
        Task.sleep(1000);
        System.out.println("world");
        System.exit(0);
    };
    public static void main(String [] args) {
        start(greeter);
        start( () -> { Task.sleep(1000); System.out.println("gijoe !!!"); } );
        fake(() -> System.out.println("i'm a faker"));
    }
    public interface Tasklet {
        public void run() throws Pausable;
    }
    public static class LambdaTask extends Task {
        Tasklet tasklet;
        public void execute() throws Pausable {
            tasklet.run();
        }
    }
    
    public static void start(Tasklet stuff) {
        LambdaTask task = new LambdaTask();
        task.tasklet = stuff;
        task.start();
    }

    public interface Fake {
        public void fake();
    }
    public static void fake(Fake task) {
        task.fake();
    }
}
