/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.nio;

import java.io.IOException;
import kilim.Task;

public class SessionTask extends Task {
    public EndPoint endpoint;

    public void close() {
        if (endpoint != null) {
            IOException ex = endpoint.close2();
            if (ex != null) Sched.log(getScheduler(),ex);
        }
    }
    private static class Sched extends kilim.Scheduler {
        static void log(kilim.Scheduler sched,Object obj) { logRelay(sched,obj); }
    }
}
