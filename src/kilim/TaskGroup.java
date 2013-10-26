package kilim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class TaskGroup extends Task {
    private Mailbox<Task> addedTasksMB = new Mailbox<Task>();
    private Mailbox<ExitMsg> exitmb = new Mailbox<ExitMsg>();
    private HashSet<Task> tasks = new HashSet<Task>();
    
    public List<ExitMsg> results = Collections.synchronizedList(new ArrayList<ExitMsg>());

    public void execute() throws Pausable {
        while (!tasks.isEmpty() || addedTasksMB.hasMessage()) {
            switch (Mailbox.select(addedTasksMB, exitmb)) {
            case 0: 
                Task t = addedTasksMB.getnb();
                t.informOnExit(exitmb);
                tasks.add(t);
                break;
            case 1: 
                ExitMsg em = exitmb.getnb();
                results.add(em);
                tasks.remove(em.task);
                break;
            }
        }
        exit(results);
    }
    
    @Override
    public ExitMsg joinb() {
        start();
        return super.joinb();
    }
    
    @Override
    public ExitMsg join() throws Pausable {
        start();
        return super.join();
    }

    public void add(Task t) {
        t.informOnExit(exitmb);
        addedTasksMB.putnb(t); // will wake up join if it is waiting.
    }
}
