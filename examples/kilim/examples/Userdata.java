// copyright 2016 seth lytle
package kilim.examples;

import kilim.Pausable;
import kilim.Task;

/*
    test of a number of ways of invoking a method that are or look similar to SAMs
    added for: https://github.com/kilim/kilim/issues/38
*/
public class Userdata extends Task {
    Eats1 eats1 = new Eats1Impl();
    Eats2 eats2 = new Eats2();
    
    public interface Eats1 {
        public void insert1(int kfood) throws Pausable;
    }
    public static class Eats1Impl implements Eats1 {
        public void insert1(int kfood) throws Pausable { System.out.println("gah"); }
    }
    public static class Eats2 {
        public void insert1(int kfood) throws Pausable { System.out.println("foo"); }
        public void insert2(int kfood) throws Pausable {}
    }
    public static class Eats3 {
        public void insert1(int kfood) throws Pausable { System.out.println("bar"); }
    }
    public interface Eats4 {
        public void insert1(int kfood) throws Pausable;
    }
    public static void eater(Eats4 eat,int kfood) throws Pausable {
        eat.insert1(kfood);
    }
    
    public void execute() throws kilim.Pausable {
        eats1.insert1(0);
        eats2.insert1(0);
        new Eats3().insert1(0);
        eater(kfood -> System.out.println("lam"), 0);
    }

    public static void main(String [] args) {
        new Userdata().start();
    }
    
}
