/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import kilim.analysis.MethodFlow;

public class TestBasicBlock extends Base {

    @Override
    protected void setUp() throws Exception {
        cache("kilim.test.ex.ExBasicBlock");
    }

    public void testNumFlows() {
        assertEquals(getFlows().size(), 8);
    }

    private void checkSize(String methodName, int expectedSize) {
        MethodFlow f = getFlow(methodName);
        if (f == null)
            return;
        if (f.getBasicBlocks().size() != expectedSize) {
            fail("Method " + methodName + ": expected flow size = "
                    + expectedSize + ", instead got "
                    + f.getBasicBlocks().size());
        }
    }

    public void testNoopSize() {
        checkSize("noop", 1);
    }

    public void testLoopSize() {
        checkSize("loop", 4);
    }

    public void testExceptionSize() {
        checkSize("exception", 6);
    }

    public void testNestedSize() {
        checkSize("nestedloop", 6);
    }

    public void testComplexSize() {
        checkSize("complex", 12);
    }

    public void testNoopCov() {
        checkCov("noop");
    }

    public void testLoopCov() {
        checkCov("loop");
    }

    public void testExceptionCov() {
        checkCov("exception");
    }

    public void testNestedCov() {
        checkCov("nestedloop");
    }

    public void testComplexCov() {
        checkCov("complex");
    }
}
