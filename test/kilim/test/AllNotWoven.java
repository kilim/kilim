/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllNotWoven extends TestSuite {
    public static Test suite() {
        TestSuite ret = new AllNotWoven();
        ret.addTestSuite(TestTypeDesc.class);
        ret.addTestSuite(TestUsage.class);
        ret.addTestSuite(TestValue.class);
        ret.addTestSuite(TestFrame.class);
        ret.addTestSuite(TestBasicBlock.class);
        ret.addTestSuite(TestJSR.class);
        ret.addTestSuite(TestFlow.class);
        ret.addTestSuite(TestExprs.class);
//        ret.addTestSuite(TestInvalidPausables.class);
        ret.addTestSuite(TestDynamicWeaver.class);
        return ret;
    }
}
