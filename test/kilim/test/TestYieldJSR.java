/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import junit.framework.TestCase;

public class TestYieldJSR extends TestCase {
 
    /*
     * Ordinary jsr call. No inlining should happen
     */
    public void testNonPausableJSR() throws Exception {
        TestYield.runTask("kilim.test.ex.ExYieldSub", 0);
    }

    /*
     * Single jsr call to a subroutine that calls Task.sleep. 
     */
    public void testSinglePausableJSR() throws Exception {
        TestYield.runTask("kilim.test.ex.ExYieldSub", 1);
    }

    /*
     * jsr sub1, jsr sub2 , jsr sub1 in sequence. Tests inlining
     * (because sub1 is called twice), and tests whether stack
     * and locals are preserved.
     */
    public void testMultiplePausableJSRs() throws Exception {
        TestYield.runTask("kilim.test.ex.ExYieldSub", 2);
    }
    
    /* jsr sub1, jsr sub2, jsr sub1, jsr sub2, where sub2 is pausable
     * and sub1 is not. Only calls to sub2 should be inlined. We have
     * no automated way of checking this, but the behavior can certainly
     * be tested.
     */
    public void testMixedJSRs() throws Exception {
        TestYield.runTask("kilim.test.ex.ExYieldSub", 3);
    }

}
