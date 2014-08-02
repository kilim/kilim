package kilim.timerhelper;


import kilim.EventPublisher;
import kilim.EventSubscriber;

public class Timer implements Comparable<Timer>{
    public long nextExecutionTime;
    public int period;
    public int state = 0;
    public static final int SCHEDULED = 1;

    public static final int EXECUTED = 2;

    public static final int CANCELLED = 3;
    public EventSubscriber es;
    
    public EventPublisher o;

    public Timer(EventSubscriber es) {
        this.es = es;
    }


    @Override
    public int compareTo(Timer o) {
        // TODO Auto-generated method stub
        return (int) (((Long)nextExecutionTime)).compareTo((Long)o.nextExecutionTime);
    }
    
    public void setTimer(long timeoutMillis, int period){
        nextExecutionTime = System.currentTimeMillis()+timeoutMillis;
        this.period= period;
      //  this.state.set(Timer.SCHEDULED);
        this.state = SCHEDULED;
    }
}
