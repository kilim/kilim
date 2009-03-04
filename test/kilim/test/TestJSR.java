/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import java.util.ArrayList;

import kilim.analysis.BasicBlock;
import kilim.analysis.MethodFlow;

public class TestJSR extends Base {
    public void testJSRSizes() throws Exception {
        String className = "kilim.test.ex.ExJSR";
        try {
            Class.forName(className);
        } catch (ClassNotFoundException cnfe) {
            fail("Please use jasmin to compile " + className);
        } catch (VerifyError e) {
            fail("Verification error for " + className + ": " + e.getMessage());
        }
        cache(className);
        MethodFlow flow = getFlow("simpleJSR");
        assertEquals(3, flow.getBasicBlocks().size());
        flow = getFlow("pausableJSR1");
//        System.out.println(flow.getBasicBlocks());
        assertEquals(4, flow.getBasicBlocks().size());

        flow = getFlow("pausableJSR2");
        ArrayList<BasicBlock> bbs = flow.getBasicBlocks();
        assertEquals(7, bbs.size());
        
        // make sure the blocks are unique
        int flag = 1 << 12;
        for (BasicBlock bb: bbs) {
            assertFalse("BasicBlock list contains duplicates", bb.hasFlag(flag));
            bb.setFlag(flag);
        }
    }
}
