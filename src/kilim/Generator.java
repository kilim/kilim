/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A Generator, from the caller's perspective, looks like a normal iterator 
 * that produces values.  Because a standard iterator's next() method 
 * must return every time, the programmer is forced to manage the stack 
 * explicitly. The Generator class instead allows one to write a
 * task with an automatically managed stack and couple it to an 
 * iterator interface. 
 *  
 * For example:
 * 
 * <pre>
 * class StringGenerator extends Generator<String>{
 *   public void execute() throws Pausable {
 *       while (!done) {
 *           String s = getNextWord(); // this can pause
 *           yield(s);  
 *       }
 *   }
 *   private String getNextWord() throws Pausable {
 *   }
 * }
 * 
 * 
 * </pre>
 *  @see kilim.examples.Fib, kilim.examples.Tree
 */

public class Generator<T> extends Task implements Iterator<T>, Iterable<T> {
    T nextVal;

    public boolean hasNext() {
        if (nextVal == null) {
            if (isDone())
                return false;
            _runExecute(null);
            return nextVal != null;
        } else {
            return true;
        }
    }

    public T next() {
        T ret;
        if (nextVal != null) {
            ret = nextVal;
            nextVal = null;
            return ret;
        }
        if (isDone()) {
            throw new NoSuchElementException();
        }
        _runExecute(null);
        ret = nextVal;
        nextVal = null;
        return ret;
    }

    public void remove() {
        throw new AssertionError("Not Supported");
    }

    public Iterator<T> iterator() {
        return this;
    }

    public void yield(T val) throws Pausable {
        nextVal = val;
        Task.yield();
    }
}
