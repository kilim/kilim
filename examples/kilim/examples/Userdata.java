// copyright 2016 seth lytle
package kilim.examples;

import kilim.Pausable;
import kilim.Task;

/*
this class fails with:
java.lang.IncompatibleClassChangeError: Found class kilim.examples.Userdata$Eats1, but interface was expected
	at kilim.examples.Userdata.$shim$1(Userdata.java)
	at kilim.examples.Userdata.execute(Userdata.java:27)
	at kilim.Task._runExecute(Task.java:435)
	at kilim.WorkerThread.run(WorkerThread.java:32)
*/
public class Userdata extends Task {
    Eats1 eats1 = new Eats1();
    Eats2 eats2 = new Eats2();
    
    public static class Eats1 {
        public void insert(int kfood) throws Pausable {}
    }
    public static class Eats2 {
        public void insert(int kfood) throws Pausable {}
        public void insert2(int kfood) throws Pausable {}
    }
    public void execute() throws kilim.Pausable {
        eats2.insert(0);
        eats1.insert(0); // gets converted to $shim$1 (i'm guessing this is a lambda)
    }

    public static void main(String [] args) {
        new Userdata().start();
    }
    
}
