/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

public class YieldReason implements PauseReason {
    public boolean isValid(Task t) {
        // Since a yield is not a reason to continue pausing, return false
        return false;
    }
    @Override
    public String toString() {
        return "yield";
    }
}
