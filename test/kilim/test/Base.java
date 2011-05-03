/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import java.util.ArrayList;

import junit.framework.TestCase;
import kilim.analysis.BasicBlock;
import kilim.analysis.ClassFlow;
import kilim.analysis.MethodFlow;
import kilim.mirrors.Detector;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class Base extends TestCase {
    private static ArrayList<MethodFlow> stflows;
    private static String                lastClassName = null;

    protected void cache(String className) throws Exception {
        if (lastClassName != className) {
            ClassFlow cf = new ClassFlow(className, Detector.DEFAULT);
            stflows = cf.analyze(/* forceAnalysis = */true);
            lastClassName = className;
        }
    }

    protected MethodFlow getFlow(String methodName) {
        for (int i = 0; i < stflows.size(); i++) {
            MethodFlow flow = stflows.get(i);
            if (flow.name.equals(methodName)) {
                return flow;
            }
        }
        fail("No method called " + methodName);
        return null;
    }

    /**
     * Returns the first basic block in the flow that has a method invocation of
     * <methodName>
     */
    protected BasicBlock getBBForMethod(MethodFlow flow, String methodName) {
        for (BasicBlock bb : flow.getBasicBlocks()) {
            AbstractInsnNode ainode = bb.getInstruction(bb.startPos);
            if (ainode instanceof MethodInsnNode
                    && ((MethodInsnNode) ainode).name.equals(methodName)) {
                return bb;
            }
        }
        fail("No method invocation found for " + methodName);
        return null;
    }

    protected ArrayList<MethodFlow> getFlows() {
        return stflows;
    }

    protected void checkCov(String methodName) {
        MethodFlow flow = getFlow(methodName);
        if (flow == null)
            return;
        ArrayList<BasicBlock> bbs = flow.getBasicBlocks();
        // Verify that all instructions are covered and that the only ones that
        // aren't are labelnodes. Also verify that there are no overlaps.
        int size = flow.instructions.size();
        boolean coverage[] = new boolean[size];
        for (int i = 0; i < size; i++) {
            coverage[i] = false;
        }
        for (BasicBlock bb : bbs) {
            /*
             * if (bb.startFrame == null) { fail("BB doesn't have a starting
             * frame"); return; }
             */
            int end = bb.endPos;
            for (int i = bb.startPos; i <= end; i++) {
                if (coverage[i]) {
                    fail("BasicBlock overlap");
                    return;
                }
                coverage[i] = true;
            }
        }
        for (int i = 0; i < size; i++) {
            if (!coverage[i]) {
                fail("Instruction " + i + " not covered");
                return;
            }
        }
    }

}
