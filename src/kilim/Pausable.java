/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

public class Pausable extends Exception {
    private static final long serialVersionUID = 1L;
    private Pausable() {}
    private Pausable(String msg) {}

    
    public interface Spawn<TT> {
        TT execute() throws Pausable, Exception;
    }
    public interface Fork {
        void execute() throws Pausable, Exception;
    }
    public interface Fork1<AA> {
        void execute(AA arg1) throws Pausable, Exception;
    }

}

