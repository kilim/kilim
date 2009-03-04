/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class All extends TestSuite {
    public static Test suite() {
        TestSuite ret = new All();
        ret.addTestSuite(TestTypeDesc.class);
        ret.addTestSuite(TestUsage.class);
        ret.addTestSuite(TestValue.class);
        ret.addTestSuite(TestFrame.class);
        ret.addTestSuite(TestBasicBlock.class);
        ret.addTestSuite(TestJSR.class);
        ret.addTestSuite(TestFlow.class);
        ret.addTestSuite(TestExprs.class);
        ret.addTestSuite(TestInvalidPausables.class);
        ret.addTestSuite(TestYield.class);
        ret.addTestSuite(TestYieldExceptions.class);
        ret.addTestSuite(TestYieldJSR.class);
        return ret;
    }
}
