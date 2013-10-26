/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllWoven extends TestSuite {
    public static Test suite() {
        TestSuite ret = new AllWoven();
        ret.addTestSuite(TestYield.class);
        ret.addTestSuite(TestInterface.class);
        ret.addTestSuite(TestYieldExceptions.class);
        ret.addTestSuite(TestYieldJSR.class);
        ret.addTestSuite(TestMailbox.class);
        ret.addTestSuite(TestLock.class);
        ret.addTestSuite(TestGenerics.class);
        ret.addTestSuite(TestIO.class);
        ret.addTestSuite(TestHTTP.class);
        return ret;
    }
}
