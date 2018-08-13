package kilim.demo;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

/*
  kilim battle demo 10
  the battle demo using java 10 features (var)
 */

public class Battle {
    static Random rand = new Random();
    int num = 1000;
    Actor [] actors = new Actor[num];
    AtomicInteger living = new AtomicInteger(num);

    
    public class Actor extends Task {
        Mailbox<Integer> damage = new Mailbox<>();
        int hp = 1 + rand.nextInt(10);

        public void strike() throws Pausable {
	    // token use of java-10-feature var
            var victim = rand.nextInt(num);
            var mb = actors[victim].damage;
	    mb.putnb(1);
        }
        
        public void execute() throws Pausable {
            while (hp > 0) {
                hp -= damage.get();
                strike();
                strike();
                strike();
                Task.sleep(100);
            }
            living.decrementAndGet();
        }
    }

    void start() {
        for (int ii=0; ii < num; ii++) (actors[ii] = new Actor()).start();
        actors[0].damage.putb(1);
        
        for (int cnt, prev=num; (cnt=living.get()) > num/2 || cnt < prev; prev=cnt, sleep())
            System.out.println(cnt);

    }

    static void sleep() {
        try { Thread.sleep(100); }
        catch (InterruptedException ex) {}
    }

    // force kilim to weave the class so we're able to detect runtime weaving
    private void unused() throws Pausable {}
    
    public static void main(String [] args) {
	System.out.println("pre weave announcement (appears twice for runtime weaving)");
        if (kilim.tools.Kilim.trampoline(true,args)) return;
        Battle battle = new Battle();
        battle.start();
        Task.idledown();
        System.out.format("\n%d actors survived the Battle Royale\n\n",battle.living.get());
    }
    
}
