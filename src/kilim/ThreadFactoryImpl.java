// Copyright 2014 by Avinash Lakshman (hedviginc.com)

package kilim;

import java.util.concurrent.ThreadFactory;

class ThreadFactoryImpl implements ThreadFactory
{
    protected String id_;
    protected ThreadGroup threadGroup_;    
    
    public ThreadFactoryImpl(String id)
    {
        SecurityManager sm = System.getSecurityManager();
        threadGroup_ = ( sm != null ) ? sm.getThreadGroup() : Thread.currentThread().getThreadGroup();
        id_ = id;
    }    
    
    public Thread newThread(Runnable runnable)
    {                      
        Thread thread = new Thread(threadGroup_, runnable, id_);        
        return thread;
    }
}
