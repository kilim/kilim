package kilim.test.ex;

import kilim.*;


public class ExInterfaceGenericTask extends Task  {
	final ExInterfaceGeneric<String> cache;
	public String getResponse;

	public ExInterfaceGenericTask(ExInterfaceGeneric<String> cacheValue ) {
	    cache = cacheValue;
    }
	
    public void execute() throws Pausable {
    	getResponse = cache.get();
    }
}

