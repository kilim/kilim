package kilim;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

class ThreadFactoryImpl implements ThreadFactory
{
    protected String id_;
    protected ThreadGroup threadGroup_;
    protected final AtomicInteger threadNbr_ = new AtomicInteger(1);
    
    public ThreadFactoryImpl(String id)
    {
        SecurityManager sm = System.getSecurityManager();
        threadGroup_ = ( sm != null ) ? sm.getThreadGroup() : Thread.currentThread().getThreadGroup();
        id_ = id;
    }    
    
    public Thread newThread(Runnable runnable)
    {        
        String name = id_ + ":" + threadNbr_.getAndIncrement();       
        Thread thread = new Thread(threadGroup_, runnable, name);        
        return thread;
    }
}
