// Copyright 2014 nilangshah - offered under the terms of the MIT License

package kilim.timerservice;


import kilim.Event;
import kilim.EventSubscriber;
import kilim.concurrent.VolatileBoolean;

public class Timer implements Comparable<Timer> {
    private volatile long  nextExecutionTime; 
    public VolatileBoolean onQueue = new VolatileBoolean(false);  //true if timer is already on timerqueue
    public volatile boolean onHeap = false; //true if timer is already on timerHeap

    public static final int                TIMED_OUT        = 3;
    public static final Event              timedOut         = new Event(TIMED_OUT);
    
    
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

        // -2: deferred (timer has completed)
        // -1: cancelled
        //  0: move to heap before processing
        //   : process on queue if ready, otherwise move to heap
        /** set the timer relative to the current time, ie set a delay (in milliseconds) */
	public void setTimer(long timeoutMillis) {
		nextExecutionTime = System.currentTimeMillis() + timeoutMillis;
	}
        /** set the timer value explicitly, ie not relative to the current time */
	public void setLiteral(long value) {
		nextExecutionTime = value;
	}

	public void cancel(){
		nextExecutionTime = -1;
	}
	
	public long getExecutionTime(){
		return nextExecutionTime;
	}
	
}
