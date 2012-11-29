/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.analysis;
import static kilim.Constants.D_FIBER;
import static kilim.Constants.D_INT;
import static kilim.Constants.D_VOID;
import static kilim.Constants.FIBER_CLASS;
import static kilim.Constants.TASK_CLASS;
import static kilim.analysis.VMType.TOBJECT;
import static kilim.analysis.VMType.loadVar;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.RETURN;

import java.util.ArrayList;
import java.util.List;

import kilim.Constants;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;

/**
 * This class takes the basic blocks from a MethodFlow and generates 
 * all the extra code to support continuations.  
 */

public class MethodWeaver {

    private ClassWeaver           classWeaver;

    private MethodFlow            methodFlow;

    private boolean               isPausable;

    private int                   maxVars;

    private int                   maxStack;

    /**
     * The last parameter to a pausable method is a Fiber ref. The rest of the
     * code doesn't know this because we do local surgery, and so is likely to
     * stomp on the corresponding local var. We need to save this in a slot
     * beyond (the original) maxLocals that is a safe haven for keeping the
     * fiberVar.
     */
    private int                   fiberVar;
    private int                   numWordsInSig;
    private ArrayList<CallWeaver> callWeavers = new ArrayList<CallWeaver>(5);

    MethodWeaver(ClassWeaver cw, MethodFlow mf) {
        this.classWeaver = cw;
        this.methodFlow = mf;
        isPausable = mf.isPausable();
        fiberVar =  methodFlow.maxLocals;
        maxVars = fiberVar + 1;
        maxStack = methodFlow.maxStack + 1; // plus Fiber 
        if (!mf.isAbstract()) {
            createCallWeavers();
        }
    }

    
    public void accept(ClassVisitor cv) {
        MethodFlow mf = methodFlow;
        String[] exceptions = ClassWeaver.toStringArray(mf.exceptions);
        String desc = mf.desc;
        String sig = mf.signature;
        if (mf.isPausable()) {
            desc = desc.replace(")", D_FIBER + ')');
            if (sig != null)
                sig = sig.replace(")", D_FIBER + ')');
        }
        MethodVisitor mv = cv.visitMethod(mf.access, mf.name, desc, sig, exceptions);
        if (!mf.isAbstract()) {
            if (mf.isPausable()) {
                accept(mv);
            } else {
                mf.accept(mv);
            }
        }
    }
    
    void accept(MethodVisitor mv) {
        visitAttrs(mv);
        visitCode(mv);
        mv.visitEnd();
    }

    private void visitAttrs(MethodVisitor mv) {
        MethodFlow mf = methodFlow;
        // visits the method attributes
        int i, j, n;
        if (mf.annotationDefault != null) {
            AnnotationVisitor av = mv.visitAnnotationDefault();
            MethodFlow.acceptAnnotation(av, null, mf.annotationDefault);
            av.visitEnd();
        }
        n = mf.visibleAnnotations == null ? 0 : mf.visibleAnnotations.size();
        for (i = 0; i < n; ++i) {
            AnnotationNode an = (AnnotationNode) mf.visibleAnnotations.get(i);
            an.accept(mv.visitAnnotation(an.desc, true));
        }
        n = mf.invisibleAnnotations == null ? 0
                : mf.invisibleAnnotations.size();
        for (i = 0; i < n; ++i) {
            AnnotationNode an = (AnnotationNode) mf.invisibleAnnotations.get(i);
            an.accept(mv.visitAnnotation(an.desc, false));
        }
        n = mf.visibleParameterAnnotations == null ? 0
                : mf.visibleParameterAnnotations.length;
        for (i = 0; i < n; ++i) {
            List<?> l = mf.visibleParameterAnnotations[i];
            if (l == null) {
                continue;
            }
            for (j = 0; j < l.size(); ++j) {
                AnnotationNode an = (AnnotationNode) l.get(j);
                an.accept(mv.visitParameterAnnotation(i, an.desc, true));
            }
        }
        n = mf.invisibleParameterAnnotations == null ? 0
                : mf.invisibleParameterAnnotations.length;
        for (i = 0; i < n; ++i) {
            List<?> l = mf.invisibleParameterAnnotations[i];
            if (l == null) {
                continue;
            }
            for (j = 0; j < l.size(); ++j) {
                AnnotationNode an = (AnnotationNode) l.get(j);
                an.accept(mv.visitParameterAnnotation(i, an.desc, false));
            }
        }
        n = mf.attrs == null ? 0 : mf.attrs.size();
        for (i = 0; i < n; ++i) {
            mv.visitAttribute((Attribute) mf.attrs.get(i));
        }
    }

    private void visitCode(MethodVisitor mv) {
        mv.visitCode();
        visitTryCatchBlocks(mv);
        visitInstructions(mv);
        visitLineNumbers(mv);
        visitLocals(mv);
        mv.visitMaxs(maxStack, maxVars);
    }
  
    // TODO Fix up locals and line numbers.
    private void visitLocals(MethodVisitor mv) {
        for (Object l: methodFlow.localVariables) {
            ((LocalVariableNode)l).accept(mv);
        }
    }

    private void visitLineNumbers(MethodVisitor mv) {
        for (Object l: methodFlow.lineNumbers) {
            ((LineNumberNode)l).accept(mv);
        }
    }

    private void visitInstructions(MethodVisitor mv) {
        //TODO gen code for pausable JSRs 
        genPrelude(mv);
        MethodFlow mf = methodFlow;
        BasicBlock lastBB = null;
        for (BasicBlock bb : mf.getBasicBlocks()) {
            int from = bb.startPos;
            
            if (bb.isPausable() && bb.startFrame != null) {
                genPausableMethod(mv, bb);
                from = bb.startPos + 1; // first instruction is consumed
            } else if (bb.isCatchHandler()) {
                List<CallWeaver> cwList = getCallsUnderCatchBlock(bb);
                if (cwList != null) {
                    genException(mv, bb, cwList);
                    from = bb.startPos + 1; // first instruction is consumed
                } // else no different from any other block
            }
            int to = bb.endPos;
            for (int i = from; i <= to; i++) {
                Label l = mf.getLabelAt(i);
                if (l != null) {
                    mv.visitLabel(l);
                }
                bb.getInstruction(i).accept(mv);
            }
            lastBB = bb;
        }
        if (lastBB != null) {
            Label l = methodFlow.getLabelAt(lastBB.endPos+1);
            if (l != null) {
                mv.visitLabel(l);
            }
        }
    }

    private List<CallWeaver> getCallsUnderCatchBlock(BasicBlock catchBB) {
        List<CallWeaver> cwList = null; // create it lazily
        for (CallWeaver cw: callWeavers) {
            for (Handler h: cw.bb.handlers) {
                if (h.catchBB == catchBB) {
                    if (cwList == null) {
                        cwList = new ArrayList<CallWeaver>(callWeavers.size()); 
                    }
                    if (!cwList.contains(cw)) {
                    cwList.add(cw);
                }
            }
        }
        }
        return cwList;
    }

    /**
     * For a method invocation f(...), this method assumes that the arguments to
     * the call have already been pushed in. We need to push in the Fiber as the
     * final argument, make the call, then add the code for post-calls, then
     * leave it to visitInstructions() to resume visiting the remaining
     * instructions in the block
     * 
     * <pre>
     *  F_CALL:
     *    aload &lt;fiberVar&gt;
     *    invokevirtual fiber.down() ;; returns Fiber
     *    ... invoke ....
     *    aload &lt;fiberVar&gt;
     *    ... post call code
     *  F_RESUME: 
     * </pre>
     * 
     * @param bb
     * The BasicBlock that contains the pausable method invocation as the first
     * instruction
     * @param mv
     */
    private void genPausableMethod(MethodVisitor mv, BasicBlock bb) {
        CallWeaver caw = null;
        if (bb.isGetCurrentTask()) {
            genGetCurrentTask(mv, bb);
            return;
        }
        for (CallWeaver cw : callWeavers) {
            if (cw.getBasicBlock() == bb) {
                caw = cw;
                break;
            }
        }
        caw.genCall(mv);
        caw.genPostCall(mv);
    }
    
    /*
     * The Task.getCurrentTask() method is marked pausable to force
     * the caller to be pausable too. But the method doesn't really
     * pause; it merely looks up the task from the fiber. This is a
     * special case where the call to getCurrentTask is replaced by
     * <pre>
     *   load fiberVar
     *   getfield task
     * @param mv
     */
    void genGetCurrentTask(MethodVisitor mv, BasicBlock bb) {
        mv.visitLabel(bb.startLabel);
        loadVar(mv, TOBJECT, getFiberVar());
        mv.visitFieldInsn(GETFIELD, FIBER_CLASS, "task", Constants.D_TASK);
    }

    private boolean hasGetCurrentTask() {
        MethodFlow mf = methodFlow;
        for (BasicBlock bb : mf.getBasicBlocks()) {
            if (!bb.isPausable() || bb.startFrame==null) continue;
            if (bb.isGetCurrentTask()) return true;
        }
        return false;
    }
    private void createCallWeavers() {
        MethodFlow mf = methodFlow;
        for (BasicBlock bb : mf.getBasicBlocks()) {
            if (!bb.isPausable() || bb.startFrame==null) continue;
            // No prelude needed for Task.getCurrentTask(). 
            if (bb.isGetCurrentTask()) continue; 
            CallWeaver cw = new CallWeaver(this, bb);
            callWeavers.add(cw);
        }
    }

    /**
     * 
     * Say there are two invocations to two pausable methods obj.f(int)
     * (virtual) and fs(double) (a static call) ; load fiber from last arg, and
     * save it in a fresh register ; lest it gets stomped on. This is because we
     * only patch locally, and don't change the other instructions.
     * 
     * <pre>
     *     aload lastVar
     *     dup
     *     astore fiberVar 
     *     switch (fiber.pc) { 
     *       default: 0: START 
     *       1: F_PASS_DOWN 
     *       2: FS_PASS_DOWN 
     *     }
     * </pre>
     */
    private void genPrelude(MethodVisitor mv) {
        assert isPausable : "MethodWeaver.genPrelude called for nonPausable method";
        if (callWeavers.size() == 0 && (!hasGetCurrentTask())) {
            return; // No pausable methods, no getCurrentTask.  Prelude is not needed at all. 
        }
        
        MethodFlow mf = methodFlow;
        // load fiber from last var
        int lastVar = getFiberArgVar();
       
        mv.visitVarInsn(ALOAD, lastVar);
        if (lastVar < fiberVar) {
            if (callWeavers.size() > 0) {
                mv.visitInsn(DUP); // for storing into fiberVar
            }
            mv.visitVarInsn(ASTORE, getFiberVar());
        }
        
        if (callWeavers.size() == 0) {
          // No pausable method calls, but Task.getCurrentTask() is present. 
          // We don't need the rest of the prelude.
          return; 
        }

        mv.visitFieldInsn(GETFIELD, FIBER_CLASS, "pc", D_INT);
        // The prelude doesn't need more than two words in the stack.
        // The callweaver gen* methods may need more. 
        ensureMaxStack(2);

        // switch stmt
        Label startLabel = mf.getOrCreateLabelAtPos(0);
        Label errLabel = new Label();
        
        Label[] labels = new Label[callWeavers.size() + 1];
        labels[0] = startLabel;
        for (int i = 0; i < callWeavers.size(); i++) {
            labels[i + 1] = new Label();
        }
//        mv.visitTableSwitchInsn(0, callWeavers.size(), startLabel, labels);
        mv.visitTableSwitchInsn(0, callWeavers.size(), errLabel, labels);
        
        mv.visitLabel(errLabel);
        mv.visitMethodInsn(INVOKESTATIC, FIBER_CLASS, "wrongPC", "()V");
        // Generate pass through down code, one for each pausable method
        // invocation
        int last = callWeavers.size() - 1;
        for (int i = 0; i <= last; i++) {
            CallWeaver cw = callWeavers.get(i);
            mv.visitLabel(labels[i+1]);
            cw.genRewind(mv);
        }
        mv.visitLabel(startLabel);
    }

    boolean isStatic() {
        return methodFlow.isStatic();
    }

    int getFiberArgVar() {
        int lastVar = getNumWordsInSig();
        if (!isStatic()) {
            lastVar++;
        }
        return lastVar;
    }

    /*
     * The number of words in the argument; doubles/longs occupy
     * two local vars.
     */
    int getNumWordsInSig() {
        if (numWordsInSig != -1) {
            String[]args = TypeDesc.getArgumentTypes(methodFlow.desc);
            int size = 0;
            for (int i = 0; i < args.length; i++) {
                size += TypeDesc.isDoubleWord(args[i]) ? 2 : 1;
            }
            numWordsInSig = size;
        }
        return numWordsInSig;
    }

    /**
     * Generate code for only those catch blocks that are reachable
     * from one or more pausable blocks. fiber.pc tells us which
     * nested call possibly caused an exception, fiber.status tells us
     * whether there is any state that needs to be restored, and
     * fiber.curState gives us access to that state. 
     * 
     * ; Figure out which pausable method could have caused this.
     * 
     * switch (fiber.upEx()) {
     *    0: goto NORMAL_EXCEPTION_HANDLING;
     *    2: goto RESTORE_F
     * }
     * RESTORE_F:
     *   if (fiber.curStatus == HAS_STATE) {
     *      restore variables from the state. don't restore stack
     *      goto NORMAL_EXCEPTION_HANDLING
     *   }
     * ... other RESTOREs
     * 
     * NORMAL_EXCEPTION_HANDLING:
     */
    private void genException(MethodVisitor mv, BasicBlock bb, List<CallWeaver> cwList) {
        mv.visitLabel(bb.startLabel);
        Label resumeLabel = new Label();
        VMType.loadVar(mv, VMType.TOBJECT, getFiberVar());
        mv.visitMethodInsn(INVOKEVIRTUAL, FIBER_CLASS, "upEx", "()I");
        // fiber.pc is on stack
        Label[] labels = new Label[cwList.size()];
        int[] keys = new int[cwList.size()];
        for (int i = 0; i < cwList.size(); i++) {
            labels[i] = new Label();
            keys[i] = callWeavers.indexOf(cwList.get(i)) + 1;
        }
        
        mv.visitLookupSwitchInsn(resumeLabel, keys, labels);
        int i = 0;
        for (CallWeaver cw: cwList) {
            if (i > 0) {
                // This is the jump (to normal exception handling) for the previous
                // switch case.
                mv.visitJumpInsn(GOTO, resumeLabel);
            }
            mv.visitLabel(labels[i]);
            cw.genRestoreEx(mv, labels[i]);
            i++;
        }
        
        // Consume the first instruction because we have already consumed the
        // corresponding label. (The standard visitInstructions code does a 
        // visitLabel before visiting the instruction itself)
        mv.visitLabel(resumeLabel);
        bb.getInstruction(bb.startPos).accept(mv);
    }
    
    int getFiberVar() {
        return fiberVar; // The first available slot
    }

    void visitTryCatchBlocks(MethodVisitor mv) {
        MethodFlow mf = methodFlow;
        ArrayList<BasicBlock> bbs = mf.getBasicBlocks();
        ArrayList<Handler> allHandlers = new ArrayList<Handler>(bbs.size() * 2);
        for (BasicBlock bb : bbs) {
            allHandlers.addAll(bb.handlers);
        }
        allHandlers = Handler.consolidate(allHandlers);
        for (Handler h : allHandlers) {
            mv.visitTryCatchBlock(mf.getLabelAt(h.from), mf.getOrCreateLabelAtPos(h.to+1), h.catchBB.startLabel, h.type);
        }
    }

    void ensureMaxVars(int numVars) {
        if (numVars > maxVars) {
            maxVars = numVars;
        }
    }

    void ensureMaxStack(int numStack) {
        if (numStack > maxStack) {
            maxStack = numStack;
        }
    }

    int getPC(CallWeaver weaver) {
        for (int i = 0; i < callWeavers.size(); i++) {
            if (callWeavers.get(i) == weaver)
                return i + 1;
        }
        assert false : " No weaver found";
        return 0;
    }

    public String createStateClass(ValInfoList valInfoList) {
        return classWeaver.createStateClass(valInfoList);
    }
    
    void makeNotWovenMethod(ClassVisitor cv, MethodFlow mf) {
        if (classWeaver.isInterface()) return;
        // Turn of abstract modifier
        int access = mf.access;
        access &= ~Constants.ACC_ABSTRACT;
        MethodVisitor mv = cv.visitMethod(access, mf.name, mf.desc, 
                mf.signature, ClassWeaver.toStringArray(mf.exceptions));
        mv.visitCode();
        visitAttrs(mv);
        mv.visitMethodInsn(INVOKESTATIC, TASK_CLASS, "errNotWoven", "()V");
        
        String rdesc = TypeDesc.getReturnTypeDesc(mf.desc);
        // stack size depends on return type, because we want to load
        // a constant of the appropriate size on the stack for 
        // the corresponding xreturn instruction.
        int stacksize = 0;
        if (rdesc != D_VOID) {
            // ICONST_0; IRETURN or ACONST_NULL; ARETURN etc.
            stacksize = TypeDesc.isDoubleWord(rdesc) ? 2 : 1;
            int vmt = VMType.toVmType(rdesc);
            mv.visitInsn(VMType.constInsn[vmt]);
            mv.visitInsn(VMType.retInsn[vmt]);
        } else {
            mv.visitInsn(RETURN);
        }
        
        int numlocals;
        if ((mf.access & Constants.ACC_ABSTRACT) != 0) {
            // The abstract method doesn't contain the number of locals required to hold the
            // args, so we need to calculate it.
            numlocals = getNumWordsInSig() + 1 /* fiber */;
            if (!mf.isStatic()) numlocals++;
        } else {
            numlocals = mf.maxLocals + 1;
        }
        mv.visitMaxs(stacksize, numlocals);
        mv.visitEnd();
    }
}


