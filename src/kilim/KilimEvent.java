package kilim;

import com.lmax.disruptor.EventFactory;

class KilimEvent
{
	protected static EventFactory<KilimEvent> factory_ = new EventFactory<KilimEvent>()
	{
		public KilimEvent newInstance()
		{
			return new KilimEvent();
		}
	};
	
	protected static long cacheLinePadding(KilimEvent kEvent)
	{
		return kEvent.l1 + kEvent.l2 + kEvent.l3 + kEvent.l4 + kEvent.l5 + kEvent.l6 + kEvent.l7; 
	}
	
	private int tid_;	
	protected volatile long l1, l2, l3, l4, l5, l6, l7 = 7L;
	private Task task_;
	
	public int getTid()
	{
		return tid_;
	}
	
	public void putTid(int tid)
	{
		tid_ = tid;
	}
	
	public Task getTask()
	{
		return task_;
	}
	
	public void putTask(Task task)
	{
		task_ = task;
	}
}
