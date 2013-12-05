package kilim;

import com.lmax.disruptor.WorkHandler;

class KilimWorkHandler implements WorkHandler<KilimEvent>
{			
	private int tid_;
	private Scheduler scheduler_;
	
	KilimWorkHandler(int tid, Scheduler scheduler)
	{
		tid_ = tid;
		scheduler_ = scheduler;
	}
	
	private void beforeExecute(KilimEvent kEvent)
	{
		
	}
	
	private void afterExecute(KilimEvent kEvent)
	{
		
	}
	
	public void onEvent(KilimEvent kEvent) throws Exception
	{
		beforeExecute(kEvent);
		try
		{
			handleEvent(kEvent);
		}
		finally
		{
			afterExecute(kEvent);
		}
	}
	
	public void handleEvent(KilimEvent kEvent) throws Exception
	{
		Task task = kEvent.getTask();
		Scheduler.setCurrentTask(task);
		int tid = kEvent.getTid();		
		if (tid == 0 || tid == tid_)
			task._runExecute(kEvent.getTid());
		else
			scheduler_.schedule(tid, task);
	}
}
