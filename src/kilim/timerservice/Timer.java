package kilim.timerservice;


import kilim.EventSubscriber;
import kilim.concurrent.VolatileBoolean;

public class Timer implements Comparable<Timer> {
    private volatile long  nextExecutionTime; 
    public VolatileBoolean onQueue = new VolatileBoolean(false);  //true if timer is already on timerqueue
    public volatile boolean onHeap = false; //true if timer is already on timerHeap

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
		nextExecutionTime = System.currentTimeMillis() + timeoutMillis;
	}
	
	public void cancel(){
		nextExecutionTime = -1;
	}
	
	public long getExecutionTime(){
		return nextExecutionTime;
	}
	
}
