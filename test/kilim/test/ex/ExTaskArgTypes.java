package kilim.test.ex;

import kilim.Pausable;
import kilim.Task;

public class ExTaskArgTypes extends Task {
    
    public void execute() throws Pausable {
        int i = 99;
        double d = Math.PI;
        String s = "foobar";
        long l = Long.MAX_VALUE;
        float f = 10.5f;
        System.out.println("Going into check");
        check(f, l, s, d, i);
//        Task.yield();
        assert i == 99 : "Int wrong";
        assert d == Math.PI: "Double wrong";
        assert s == "foobar" : "String wrong";
        assert l == Long.MAX_VALUE : "Long wrong";
        assert f == 10.5f: "Float wrong";
        System.out.println("Exiting");
        Task.exit("Done");
    }
 
    void check(float f, long l, Object s, double d, int i) throws Pausable {
        assert d == Math.PI;
        assert l == Long.MAX_VALUE;
        assert f == 10.5f;
        System.out.println("Before yield");
        Task.yield();
        System.out.println("After yield");
        assert i == 99;
        assert l == Long.MAX_VALUE;
    }
}
