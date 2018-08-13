// copyright 2018 nqzero - offered under the terms of the MIT License

package kilim.demo;

import kilim.Mailbox;
import kilim.Task;

public class HelloWorld {

    public static void main(String[] args) throws Exception {
	System.out.println("pre weave announcement (appears twice for runtime weaving)");
        if (kilim.tools.Kilim.trampoline(true,args)) return;
        Mailbox<Integer> mb = new Mailbox(100,1000);

        int num = 100, delay = 1000, val = -1;
        if (args.length > 0) num = Integer.parseInt(args[0]);
        long t0 = 0, t1=0;

        for (int jj = 0; jj < 10; jj++, t0=t1) {
            for (int ii=0; ii < num; ii++)
                Task.fork(fiber -> {
                    Task.sleep(delay);
                    mb.put(1);
                });
            for (int ii=0; ii < num; ii++) {
                int tmp = mb.getb();
                if (tmp != 1)
                    System.out.println("tmp: " + tmp);
                val += tmp;
            }
            t1 = System.currentTimeMillis();
            System.out.format("hello world: %d in %d millis\n",val,t1-t0);
        }
        
        Task.idledown();
    }

    
}
