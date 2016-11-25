// copyright 2016 nqzero - offered under the terms of the MIT License

package kilim.examples;

import kilim.Pausable;
import kilim.Continuation;
import kilim.Scheduler;
import kilim.Task;

public class Pure {
    public static class PureDemos {
        PureDemo fibers [];
        boolean [] done;
        public void setup(int num) {
            fibers = new PureDemo [num];
            done = new boolean[fibers.length];
            for (int ii=0; ii < fibers.length; ii++)
                fibers[ii] = new PureDemo();
        }
        public void reset() {
            for (int ii=0; ii < fibers.length; ii++) {
                done[ii] = false;
                fibers[ii].reset();
                fibers[ii].sum = 0;
            }
        }
        public String perf() {
            reset();
            boolean pending = true;
            while (pending) {
                pending = false;
                for (int ii=0; ii<fibers.length; ii++)
                    if (!done[ii]) pending |= !(done[ii] = fibers[ii].run());
            }
            int sum = 0;
            for (PureDemo pf : fibers) sum += pf.sum;
            return "pure array: " + fibers.length +" -- "+ sum;
        }
        public String mainPerf(int num) {
            if (fibers==null || num != fibers.length) setup(num);
            return perf();
        }
    }
    public static class PureDemo extends Continuation {
        int sum = 0;
        public void execute() throws Pausable {
            for (int ii=0; ii < 10; ii++) {
                kilim.Fiber.yield();
                sum += ii;
            }
        }
    }
    public static class PureMega extends Continuation {
        int sum = 0;
        public void execute() throws Pausable {
            for (int ii=0; ii < 10; ii++) {
                kilim.Task.yield();
                sum += ii;
            }
        }
    }
    public static class TaskDemo extends Task {
        int sum = 0;
        public void execute() throws Pausable {
            for (int ii=0; ii < 10; ii++) {
                kilim.Task.yield();
                sum += ii;
            }
        }
    }
    public static class JavaDemo {
        int sum = 0;
        public void execute() {
            for (int ii=0; ii < 10; ii++) {
                sum += ii;
            }
        }
        public boolean run() { execute(); return true; }
    }
    PureDemos pds = new PureDemos();
    public static void main(String [] args) {
        int num = 0, repeat = 5; 
        if (args.length==0) {
            System.out.println("args  : mode, number of cycles, number of repeats");
            System.out.println("modes : pure (default), perf, task, mega, java");
            System.out.println("cycles: 0 (default) implies mode specific default");
            System.out.format ("using : pure %d %d\n",num,repeat);
        }
        String name = args.length==0 ? "pure" : args[0];
        if (args.length > 1) 
            try { num = Integer.parseInt(args[1]); }
            catch (Exception ex) {}
        if (args.length > 2) 
            try { repeat = Integer.parseInt(args[2]); }
            catch (Exception ex) {}
        Pure pure = new Pure();
        for (int ii=0; ii<repeat; ii++)
            pure.run(name,num);
        if (name.equals("task")) {
            Scheduler.getDefaultScheduler().shutdown();
            Task.shutdown();
        }
    }
    public void run(String name,int num) {
        final long start = System.nanoTime();
        String r = "";
        if (name.equals("perf")) r = pds.mainPerf(num = num==0 ?  5000000 : num);
        if (name.equals("task")) r = mainTask(num = num==0 ?  1000000 : num);
        if (name.equals("pure")) r = mainPure(num = num==0 ?  5000000 : num);
        if (name.equals("mega")) r = mainMega(num = num==0 ?  5000000 : num);
        if (name.equals("java")) r = mainJava(num = num==0 ? 25000000 : num);
        final long duration = System.nanoTime() - start;
        System.out.format("%40s, %10.3f\n",r,1.0*duration/num);
    }
    public static String mainPure(int num) {
        PureDemo fibers [] = new PureDemo [num];
        boolean [] done = new boolean[fibers.length];
        for (int ii=0; ii < fibers.length; ii++)
            fibers[ii] = new PureDemo();
        boolean pending = true;
        while (pending) {
            pending = false;
            for (int ii=0; ii<fibers.length; ii++)
                if (!done[ii]) pending |= !(done[ii] = fibers[ii].run());
        }
        int sum = 0;
        for (PureDemo pf : fibers) sum += pf.sum;
        return "pure array: " + fibers.length +" -- "+ sum;
    }
    public static String mainMega(int num) {
        PureMega fibers [] = new PureMega [num];
        boolean [] done = new boolean[fibers.length];
        for (int ii=0; ii < fibers.length; ii++)
            fibers[ii] = new PureMega();
        boolean pending = true;
        while (pending) {
            pending = false;
            for (int ii=0; ii<fibers.length; ii++)
                if (!done[ii]) pending |= !(done[ii] = fibers[ii].run());
        }
        int sum = 0;
        for (PureMega pf : fibers) sum += pf.sum;
        return "mega array: " + fibers.length +" -- "+ sum;
    }
    public static String mainTask(int num) {
        TaskDemo fibers [] = new TaskDemo [num];
        for (int ii=0; ii < fibers.length; ii++)
            fibers[ii] = new TaskDemo();
        for (Task task : fibers) task.start();
        for (Task task : fibers) task.joinb();
        int sum = 0;
        for (TaskDemo pf : fibers) sum += pf.sum;
        return "task array: " + fibers.length +" -- "+ sum;
    }
    public static String mainJava(int num) {
        JavaDemo fibers [] = new JavaDemo [num];
        boolean [] done = new boolean[fibers.length];
        for (int ii=0; ii < fibers.length; ii++)
            fibers[ii] = new JavaDemo();
        boolean pending = true;
        while (pending) {
            pending = false;
            for (int ii=0; ii<fibers.length; ii++)
                if (!done[ii]) pending |= !(done[ii] = fibers[ii].run());
        }
        int sum = 0;
        for (JavaDemo pf : fibers) sum += pf.sum;
        return "java array: " + fibers.length +" -- "+ sum;
    }
}
