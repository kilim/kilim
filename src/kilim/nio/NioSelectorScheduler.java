/* Copyright (c) 2006, Sriram Srinivasan
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
import java.util.Iterator;
import java.util.LinkedList;

import kilim.Mailbox;
import kilim.Pausable;
import kilim.RingQueue;
import kilim.Scheduler;
import kilim.Task;
import kilim.http.IntList;

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
public class NioSelectorScheduler extends Scheduler {
    //TODO: Fix hardcoding
    public static int         LISTEN_BACKLOG  = 1000;

    public Selector           sel;
    /* 
     * The thread in which the selector runs. THe NioSelectorScheduler only runs one thread,
     * unlike typical schedulers that manage a pool of threads.
     */
    public SelectorThread     selectorThread;
    
    /**
     * SessionTask registers its endpoint with the selector by sending a SockEvent
     * message on this mailbox. 
     */
    public Mailbox<SockEvent> registrationMbx = new Mailbox<SockEvent>(1000);
    

    /**
     * @throws IOException
     */
    public NioSelectorScheduler() throws IOException {
        this.sel = Selector.open();
        selectorThread = new SelectorThread(this);
        selectorThread.start();
        Task t = new RegistrationTask(registrationMbx, sel);
        t.setScheduler(this);
        t.start();
    }

    public void listen(int port, Class<? extends SessionTask> sockTaskClass, Scheduler sockTaskScheduler)
            throws IOException {
        Task t = new ListenTask(port, this, sockTaskClass);
        t.setScheduler(this);
        t.start();
    }

    @Override
    public void schedule(Task t) {
        addRunnable(t);
        if (Thread.currentThread() != selectorThread) {
            sel.wakeup();
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        sel.wakeup();
    }
    
    synchronized void addRunnable(Task t) {
        runnableTasks.put(t);
    }

    synchronized RingQueue<Task> swapRunnables(RingQueue<Task> emptyRunnables) {
        RingQueue<Task> ret = runnableTasks;
        runnableTasks = emptyRunnables;
        return ret;
    }

    static class SelectorThread extends Thread {
        NioSelectorScheduler _scheduler;

        public SelectorThread(NioSelectorScheduler scheduler) {
            super("KilimSelector");
            _scheduler = scheduler;
        }

        @Override
        public void run() {
            Selector sel = _scheduler.sel;
            RingQueue<Task> runnables = new RingQueue<Task>(100); // to swap with scheduler
            while (true) {
                int n;
                try {
                    if (_scheduler.isShutdown()) {
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
                    if (_scheduler.numRunnables() > 0) {
                        n = sel.selectNow();
                    } else {
                        n = sel.select();
                    }
                } catch (IOException ignore) {
                    n = 0;
                    ignore.printStackTrace();
                }
                if (n > 0) {
                    Iterator<SelectionKey> it = sel.selectedKeys().iterator();
                    while (it.hasNext()) {
                        SelectionKey sk = it.next();
                        it.remove();
                        Object o = sk.attachment();
                        sk.interestOps(0);
                        if (o instanceof SockEvent) {
                            SockEvent ev = (SockEvent) o;
                            ev.replyTo.putnb(ev);
                        } else if (o instanceof Task) {
                            Task t = (Task) o;
                            t.resume();
                        }
                    }
                }
                runnables.reset();
                runnables = _scheduler.swapRunnables(runnables);
                // Now execute all runnables inline
                // if (runnables.size() == 0) {
                // System.out.println("IDLE");
                // }
                while (runnables.size() > 0) {
                    Task t = runnables.get();
                    t._runExecute(null);
                    // If task calls Task.yield, it would have added itself to scheduler already.
                    // If task's pauseReason is YieldToSelector, then nothing more to do.
                    // Task should be registered for the appropriate Selector op.
                    // In all other cases, (Task.sleep(), Mailbox.get() etc.), unregister
                    // the channel
                    if (t instanceof SessionTask) {
                        SessionTask st = (SessionTask) t;
                        if (st.isDone()) {
                            st.close();
                        }
                    }
                }
            }
        }
    }

    public synchronized int numRunnables() {
        return runnableTasks.size();
    }

    public static class ListenTask extends SessionTask {
        Class<? extends SessionTask> sessionClass;
        ServerSocketChannel          ssc;
        int                          port;

        public ListenTask(int port, NioSelectorScheduler selScheduler, Class<? extends SessionTask> sessionClass)
                throws IOException {
            this.port = port;
            this.sessionClass = sessionClass;
            this.ssc = ServerSocketChannel.open();
            ssc.socket().setReuseAddress(true);
            ssc.socket().bind(new InetSocketAddress(port), LISTEN_BACKLOG); //
            ssc.configureBlocking(false);
            setEndPoint(new EndPoint(selScheduler.registrationMbx, ssc));
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
                    SessionTask task = sessionClass.newInstance();
                    try {
                        EndPoint ep = new EndPoint(this.endpoint.sockEvMbx, ch);
                        task.setEndPoint(ep);
                        n++;
                        // System.out.println("Num sessions created:" + n);
                        task.start();
                    } catch (IOException ioe) {
                        ch.close();
                        System.err.println("Unable to start session:");
                        ioe.printStackTrace();
                    }
                }
            }
        }
    }

    public static class RegistrationTask extends Task {
        Mailbox<SockEvent> mbx;
        Selector           selector;

        public RegistrationTask(Mailbox<SockEvent> ambx, Selector asel) {
            mbx = ambx;
            selector = asel;
        }

        @Override
        public void execute() throws Pausable, Exception {
            while (true) {
                SockEvent ev = mbx.get();
                SelectionKey sk = ev.ch.register(selector, ev.interestOps);
                sk.attach(ev);
            }
        }
    }
}