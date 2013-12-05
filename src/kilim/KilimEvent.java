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
		
	private int tid_;
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
