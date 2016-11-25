/* Copyright (c) 2006, Sriram Srinivasan, nqzero 2016
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;


import kilim.Mailbox;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.Task;

/**
 * This class wraps a selector and runs it in a separate thread.
 * 
 * It runs one or more ListenTasks (bound to their respective ports), which in turn spawn as many session tasks (see
 * {@link #listen(int, Class, Scheduler)}) as the number of new http connections. The supplied scheduler is used to
 * execute the tasks. It is possible, although not typical, to run tasks in the NioSelectorScheduler itself, as it too
 * is a scheduler.
 * 
 * Usage is as follows:
 * <pre>
 *  NioSelectorScheduler nss = new NioSelectorScheduler();
 *  nss.listen(8080, MySessionTask.class, Scheduler.getDefaultScheduler();
 *  
 *  class MySessionTask extends SessionTask {
 *  ...
 *  }
 * </pre>
 *  @see SessionTask 
 */
public class NioSelectorScheduler {
    //TODO: Fix hardcoding
    public static int         LISTEN_BACKLOG  = 1000;

    private Selector           sel;
    /* 
     * The thread in which the selector runs. THe NioSelectorScheduler only runs one thread,
     * unlike typical schedulers that manage a pool of threads.
     */
    private SelectorThread     selectorThread;
    
    /**
     * SessionTask registers its endpoint with the selector by sending a SockEvent
     * message on this mailbox. 
     */
    final Mailbox<SockEvent> regbox = new Mailbox<SockEvent>(1000);
    AtomicBoolean update = new AtomicBoolean();
    final private Task regtask;
    volatile boolean running = true;
    
    /**
     * @throws IOException
     */
    public NioSelectorScheduler() throws IOException {
        sel = Selector.open();
        selectorThread = new SelectorThread();
        selectorThread.start();
        regtask = new RegistrationTask();
        regtask.start();
    }

    public int listen(int port,SessionFactory factory, Scheduler sockTaskScheduler) throws IOException {
        ListenTask t = new ListenTask(port);
        t.factory = factory;
        return listen(t,sockTaskScheduler);
    }

    public int listen(int port,Class<? extends SessionTask> sockTaskClass,Scheduler sockTaskScheduler) throws IOException {
        ListenTask t = new ListenTask(port);
        t.sessionClass = sockTaskClass;
        return listen(t,sockTaskScheduler);
    }
    private int listen(ListenTask t,Scheduler sockTaskScheduler) {
        t.setScheduler(sockTaskScheduler);
        t.start();
        return t.port;
    }

    public void shutdown() { running = false; sel.wakeup(); }
    
    class SelectorThread extends Thread {
        public SelectorThread() {
            super("KilimSelector"+":"+Thread.currentThread().getId());
        }

        @Override
        public void run() {
            while (true) {
                int n;
                try {
                    if (!running) {
                        Iterator<SelectionKey> it = sel.keys().iterator();
                        while (it.hasNext()) {
                            SelectionKey sk = it.next();
                            sk.cancel();
                            Object o = sk.attachment();
                            if (o instanceof SockEvent  &&   ((SockEvent)o).ch instanceof ServerSocketChannel) {
                                // TODO FIX: Need a proper, orderly shutdown procedure for tasks. This closes down the task
                                // irrespective of the thread it may be running on. Terrible.
                                try {
                                    ((ServerSocketChannel)((SockEvent)o).ch).close();
                                } catch (IOException ignore) {}
                            }
                        }
                        break;
                    }
                    // thread safe - addTask latches update with a wakeup
                    if (update.get()) n = sel.selectNow();
                    else n = sel.select();
                } catch (IOException ignore) { n = 0; ignore.printStackTrace(); }
                if (n > 0) {
                    Iterator<SelectionKey> it = sel.selectedKeys().iterator();
                    while (it.hasNext()) {
                        SelectionKey sk = it.next();
                        it.remove();
                        Object o = sk.attachment();
                        sk.interestOps(0);
                        assert(o instanceof SockEvent);
                        SockEvent ev = (SockEvent) o;
                        ev.replyTo.putnb(ev);
                    }
                }
                if (update.getAndSet(false))
                    regtask.run();
            }
        }
    }

    public interface SessionFactory {
        public SessionTask get() throws Exception;
    }
    class ListenTask extends SessionTask {
        Class<? extends SessionTask> sessionClass;
        SessionFactory               factory;
        ServerSocketChannel          ssc;
        int                          port;

        ListenTask(int port) throws IOException {
            this.port = port;
            this.ssc = ServerSocketChannel.open();
            ssc.socket().setReuseAddress(true);
            ssc.socket().bind(new InetSocketAddress(port), LISTEN_BACKLOG);
            ssc.configureBlocking(false);
            endpoint = new EndPoint(NioSelectorScheduler.this,ssc);
        
            // if port is automatically assigned then retrieve actual value
            if(port == 0) {
                this.port = ssc.socket().getLocalPort();
            };    
        }

        public String toString() {
            return "ListenTask: " + port;
        }

        @Override
        public void execute() throws Pausable, Exception {
            int n = 0;
            while (true) {
                SocketChannel ch = ssc.accept();
                if (this.scheduler.isShutdown()) {
                    ssc.close();
                    break;
                }
                if (ch == null) {
                    endpoint.pauseUntilAcceptable();
                } else {
                    ch.socket().setTcpNoDelay(true);
                    ch.configureBlocking(false);
                    SessionTask task = (factory==null) ? sessionClass.newInstance() : factory.get();
                    task.endpoint = new EndPoint(NioSelectorScheduler.this,ch);
                    n++;
                    task.start();
                }
            }
        }
    }

    public class RegistrationTask extends Task {
        private RegistrationTask() {}
        public void wake() {
            update.set(true);
            if (Thread.currentThread() != selectorThread)
                sel.wakeup();
        }
        public void execute() throws Pausable, Exception {
            while (true) {
                SockEvent ev = regbox.get();
                SelectionKey sk = ev.ch.register(sel, ev.interestOps);
                sk.attach(ev);
            }
        }
    }
}
