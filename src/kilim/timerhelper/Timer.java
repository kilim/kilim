package kilim.timerhelper;


import kilim.EventSubscriber;
import kilim.VolatileBoolean;

public class Timer implements Comparable<Timer> {
    public volatile long  nextExecutionTime; 
    public VolatileBoolean onQueue = new VolatileBoolean(false);  //true if timer is already on timerqueue
    public boolean onHeap = false; //true if timer is already on timerHeap
  
    public int index;
       
    public EventSubscriber es; 

    public Timer(EventSubscriber es) {
        this.es = es;
    }

    @Override
    public int compareTo(Timer o) {
        return (int) (((Long) nextExecutionTime))
                .compareTo((Long) o.nextExecutionTime);
    }

    public void setTimer(long timeoutMillis) {
        nextExecutionTime=System.currentTimeMillis() + timeoutMillis;
    }
}
