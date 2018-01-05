/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

/**
 * @see kilim.Task#informOnExit(Mailbox)
 */
public class ExitMsg<TT> {
    public final Task<TT> task; // exiting task
    public final TT result; // contains Throwable if exitCode == 1
    
    public ExitMsg(Task<TT> t,TT res) {
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
