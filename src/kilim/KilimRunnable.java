package kilim;

public abstract class KilimRunnable implements Runnable
{
	private long creationTime_;
	private long timeInQ_;
	private long executionStartTime_;	
	private long processingTime_;
	
	public KilimRunnable()
	{
		creationTime_ = System.currentTimeMillis();				
	}
		
	private void beforeExecute()
	{
		long currentTime = System.currentTimeMillis();		
		timeInQ_ = (currentTime - creationTime_);		     
	}

	public abstract void doWork();
	
	public void run()
	{
		beforeExecute();
		doWork();
		afterExecute();
	}
	
	private void afterExecute()
	{
		long currentTime = System.currentTimeMillis();		
        processingTime_ = timeInQ_ + ( currentTime - executionStartTime_ );                                                
	}
	
	protected long getTimeInQ()
	{
		return timeInQ_;
	}
	
	protected long getProcessingTime()
	{
		return processingTime_;
	}
}
