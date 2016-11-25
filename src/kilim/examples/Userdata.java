// copyright 2016 nqzero - offered under the terms of the MIT License

package kilim.examples;

import java.util.stream.Stream;
import kilim.Pausable;
import kilim.Task;

/*
    test of a number of ways of invoking a method that are or look similar to SAMs
    added for: https://github.com/kilim/kilim/issues/38

    also shows off Kilim.trampoline() - ie, this class can be run with or without weaving
    if it hasn't been woven, it will automatically call the runtime weaver
*/
public class Userdata extends Task {
    Eats1 eats1 = new Eats1Impl();
    Eats2 eats2 = new Eats2();
    
    public interface Eats1 {
        public static String stuff(String foo) { return foo + "-stuff"; }
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
        public static String fluff(String foo) { return foo + "-fluff"; }
        public void insert1(int kfood) throws Pausable;
    }
    public interface Eats5 extends Eats4 {
        public static void buff(int kfood) throws Pausable { System.out.println(Eats4.fluff("fox-"+kfood)); }
    }
    public static void eater(Eats4 eat,int kfood) throws Pausable {
        eat.insert1(kfood);
    }
    
    public void execute() throws kilim.Pausable {
        eats1.insert1(0);
        System.out.println(Eats1.stuff("funky"));
        eats2.insert1(0);
        new Eats3().insert1(0);
        eater(kfood -> System.out.println("lam"), 0);
        Eats4.fluff("marshmallow");
        Eats5.buff(5);
        Stream.of(0,1,2,3,4).forEach(System.out::println);
        System.exit(0);
    }

    /**
     * start the userdata task
     * this entry point supports automatic runtime weaving
     * if the code hasn't been woven when invoked, it will trampoline off the WeavingClassLoader
     */
    public static void main(String [] args) {
        if (kilim.tools.Kilim.trampoline(true,args)) return;
        new Userdata().start();
    }
    
}
