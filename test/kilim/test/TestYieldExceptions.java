/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import junit.framework.TestCase;

public class TestYieldExceptions extends TestCase {
    /*
     * exception thrown in a pausable method. The
     * catch handler does not make any pausable calls 
     */
    public void testOrdinaryCatch()  throws Exception {
        TestYield.runTask(new kilim.test.ex.ExCatch(0));
    }
    
    /*
     * pausable method throws an exception and the
     * catch handler makes a pausable call as well 
     */
    public void testPausableCatch() throws Exception {
        TestYield.runTask(new kilim.test.ex.ExCatch(1));
    }
    
    /*
     * catch handler throws and catches another exception
     */ 
    public void testNestedException() throws Exception {
        TestYield.runTask(new kilim.test.ex.ExCatch(2));
    }
    
    /*
     * try/finally surrounds try/catch. lots of exceptions
     * and pauses all around
     */ 
    public void testTryCatchFinally() throws Exception {
        TestYield.runTask(new kilim.test.ex.ExCatch(3));
    }
    
    
    public void testPausableBlocksBeforeCatch() throws Exception {
        TestYield.runTask(new kilim.test.ex.ExCatch(4));
    }
}
