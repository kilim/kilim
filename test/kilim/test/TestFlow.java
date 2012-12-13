/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import static kilim.Constants.D_BYTE;
import kilim.analysis.BasicBlock;
import kilim.analysis.Frame;
import kilim.analysis.IncompatibleTypesException;
import kilim.analysis.MethodFlow;
import kilim.analysis.TypeDesc;
import kilim.analysis.Value;

public class TestFlow extends Base {

    @Override
    protected void setUp() throws Exception {
        cache("kilim.test.ex.ExFlow");
    }

    public void testMerge() throws IncompatibleTypesException {
        MethodFlow flow = getFlow("loop");
        if (flow == null)
            return;
        // Make sure the merging is fine. There used to be a bug
        assertEquals("Lkilim/test/ex/ExA;", TypeDesc.mergeType("Lkilim/test/ex/ExC;", "Lkilim/test/ex/ExD;"));
        assertEquals("Lkilim/test/ex/ExA;", TypeDesc.mergeType("Lkilim/test/ex/ExD;", "Lkilim/test/ex/ExC;"));
        BasicBlock bb = getBBForMethod(flow, "join");
        assertTrue(bb != null);
        Frame f = bb.startFrame;
        // Check Locals
        // assertEquals("Lkilim/test/ex/ExFlow;", f.getLocal(0));
        assertEquals("Lkilim/test/ex/ExA;", f.getLocal(1).getTypeDesc());
        // assertSame(D_INT, f.getLocal(2));
        // Check operand stack
        assertSame(D_BYTE, f.getStack(0).getTypeDesc());
        assertEquals("Lkilim/test/ex/ExFlow;", f.getStack(1).getTypeDesc());
        assertEquals("Lkilim/test/ex/ExA;", f.getStack(2).getTypeDesc());
    }

    public void testConstants() throws IncompatibleTypesException {
        MethodFlow flow = getFlow("loop");
        if (flow == null)
            return;
        BasicBlock bb = getBBForMethod(flow, "join");
        Frame f = bb.startFrame;
        assertSame(f.getLocal(2).getConstVal(), Value.NO_VAL);
    }
}
