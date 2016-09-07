package kilim.demo;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.Task;

public class Battle {
    static Random rand = new Random();
    int num = 1000;
    Actor [] actors = new Actor[num];
    AtomicInteger living = new AtomicInteger(num);

    
    public class Actor extends Task {
        Mailbox<Integer> damage = new Mailbox<>();
        int hp = 1 + rand.nextInt(10);

        public void execute() throws Pausable {
            while (hp > 0) {
                hp -= damage.get();
                actors[rand.nextInt(num)].damage.putnb(1);
                actors[rand.nextInt(num)].damage.putnb(1);
                actors[rand.nextInt(num)].damage.putnb(1);
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
    
    public static void main(String [] args) {
        if (kilim.tools.Kilim.trampoline(false,args)) return;
        Battle battle = new Battle();
        battle.start();
        Scheduler.getDefaultScheduler().idledown();
        System.out.format("\n%d actors survived the Battle Royale\n\n",battle.living.get());
    }
    
}
