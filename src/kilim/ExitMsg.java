/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

/**
 * @see kilim.Task#informOnExit(Mailbox)
 */
public class ExitMsg {
    public final Task task; // exiting task
    public final Object result; // contains Throwable if exitCode == 1
    
    public ExitMsg(Task t, Object res) {
        task = t;
        result = res;
    }
    
    public String toString() {
        return "exit(" + task.id + "), result = " + result;
    }
    
    @Override
    public int hashCode() {
        return task.id;
    }
    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }
}
