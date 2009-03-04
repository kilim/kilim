/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

public class TaskDoneReason implements PauseReason {
    Object exitObj;
    TaskDoneReason(Object o) {exitObj = o;}
    
    public boolean isValid(Task t) {
        // When a task is done, it is reason to continue pausing
        return true;
    }
    
    public String toString() {
        return "Done. Exit msg = " + exitObj;
    }
}