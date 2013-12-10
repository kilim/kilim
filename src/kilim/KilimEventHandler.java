package kilim;

import com.lmax.disruptor.EventHandler;

class KilimEventHandler implements EventHandler<KilimEvent>
{			
	private int tid_;	
	
	KilimEventHandler(int tid)
	{
		tid_ = tid;		
	}
	
	private void beforeExecute(KilimEvent kEvent)
	{
		
	}
	
	private void afterExecute(KilimEvent kEvent)
	{
		
	}
	
	public void onEvent(KilimEvent kEvent, long sequence, boolean endOfBatch) throws Exception
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
		int tid = kEvent.getTid();		
		if (tid == tid_)
		{		
			if (task != null)
			{
				Scheduler.setCurrentTask(task);
				task._runExecute(kEvent.getTid());
			}
		}		
	}
}
