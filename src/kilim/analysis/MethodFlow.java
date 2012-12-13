/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.analysis;
import static kilim.Constants.NOT_PAUSABLE_CLASS;
import static kilim.Constants.PAUSABLE_CLASS;
import static kilim.analysis.BasicBlock.COALESCED;
import static kilim.analysis.BasicBlock.ENQUEUED;
import static kilim.analysis.BasicBlock.INLINE_CHECKED;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_VOLATILE;
import static org.objectweb.asm.Opcodes.JSR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

import kilim.KilimException;
import kilim.mirrors.Detector;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;


/** 
 * This represents all the basic blocks of a method. 
 */
public class MethodFlow extends MethodNode {
    
	
    /**
     * The classFlow to which this methodFlow belongs
     */
    
    ClassFlow                  classFlow;
    
    /**
     * Maps instructions[i] to Label or null (if no label). Note that
     * LabelInsnNodes are not accounted for here because they themselves are not
     * labelled.
     */
    
    private ArrayList<Label>           posToLabelMap;
    
    /**
     * Reverse map of posToLabelMap. Maps Labels to index within
     * method.instructions.
     */
    private HashMap<Label, Integer>    labelToPosMap;
    
    /**
     * Maps labels to BasicBlocks
     */
    private HashMap<Label, BasicBlock> labelToBBMap;
    
    /**
     * The list of basic blocks, in the order in which they occur in the class file.
     * Maintaining this order is important, because we'll use it to drive duplication (in case
     * of JSRs) and also while writing out the class file.
     */
    private BBList      basicBlocks;
    
    private PriorityQueue<BasicBlock>          workset;
    
    private boolean hasPausableAnnotation;
    private boolean suppressPausableCheck;

    private List<MethodInsnNode> pausableMethods = new LinkedList<MethodInsnNode>();
    
	private final Detector detector;
    
    public MethodFlow(
            ClassFlow classFlow,
            final int access,
            final String name,
            final String desc,
            final String signature,
            final String[] exceptions,
            final Detector detector) {
        super(access, name, desc, signature, exceptions);
        this.classFlow = classFlow;
        this.detector = detector;
        int numInstructions = instructions.size();
        posToLabelMap = new ArrayList<Label>(numInstructions);
        for (int i = numInstructions - 1 ; i >= 0; i--) {
            posToLabelMap.add(null);
        }
        labelToPosMap = new HashMap<Label, Integer>(numInstructions * 2);
        labelToBBMap = new HashMap<Label, BasicBlock>(numInstructions);
        if (exceptions != null && exceptions.length > 0) {
            for (String e: exceptions) { 
                if (e.equals(PAUSABLE_CLASS)) {
                    hasPausableAnnotation = true;
                    break;
                } else if (e.equals(NOT_PAUSABLE_CLASS)) {
                    suppressPausableCheck = true;
                }
            }
        }
    }
    
    public void analyze() throws KilimException {
        buildBasicBlocks();
        if (basicBlocks.size() == 0) return;
        consolidateBasicBlocks();
        assignCatchHandlers();
        inlineSubroutines();
        doLiveVarAnalysis();
        dataFlow();
        this.labelToBBMap = null; // we don't need this mapping anymore
    }
    
    public void verifyPausables() throws KilimException {
        // If we are looking at a woven file, we don't need to verify
        // anything
        if (classFlow.isWoven || suppressPausableCheck) return;
        
        if (!hasPausableAnnotation && !pausableMethods.isEmpty()) {
            String msg;
            String name = toString(classFlow.getClassName(),this.name,this.desc);   
            if (this.name.endsWith("init>")) {
                msg = "Constructor " + name + " calls pausable methods:\n";
            } else { 
                msg = name + " should be marked pausable. It calls pausable methods\n";
            }
            for (MethodInsnNode min: pausableMethods) {
                msg += toString(min.owner, min.name, min.desc) + '\n';
            }
            throw new KilimException(msg);
        }
        if (classFlow.superName != null) {
            checkStatus(classFlow.superName, name, desc);
        }
        if (classFlow.interfaces != null) {
            for (Object ifc: classFlow.interfaces) {
                checkStatus((String) ifc, name, desc);
            }
        }
    }

    private void checkStatus(String superClassName, String methodName, String desc) throws KilimException {
        int status = detector.getPausableStatus(superClassName, methodName, desc);
        if ((status == Detector.PAUSABLE_METHOD_FOUND && !hasPausableAnnotation)) {
            throw new KilimException("Base class method is pausable, derived class is not: " +
                    "\nBase class = " + superClassName +
                    "\nDerived class = " + this.classFlow.name +
                    "\nMethod = " + methodName + desc);
        } 
        if (status == Detector.METHOD_NOT_PAUSABLE && hasPausableAnnotation) {
            throw new KilimException("Base class method is not pausable, but derived class is: " +
                    "\nBase class = " + superClassName +
                    "\nDerived class = " + this.classFlow.name +
                    "\nMethod = " + methodName + desc);           
        }
    }

    private String toString(String className, String methName, String desc) {
        return className.replace('/', '.') + '.' + methName + desc;
    }
    
    @Override
    public void visitLabel(Label label) {
//        if (hasPausableAnnotation)
            setLabel(instructions.size(), label);
//        else
//            super.visitLabel(label);
    }
    
    /*
    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationVisitor av = super.visitAnnotation(desc, visible);
        if (visible && desc.equals(D_PAUSABLE)) {
            hasPausableAnnotation = true;
        }
        return av;
    }
*/
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc) {
        super.visitMethodInsn(opcode, owner, name, desc);
        // The only reason for adding to pausableMethods is to create a BB for pausable
        // method call sites. If the class is already woven, we don't need this 
        // functionality.
        if (!classFlow.isWoven) {
            int methodStatus = detector.getPausableStatus(owner, name, desc);
            if (methodStatus == Detector.PAUSABLE_METHOD_FOUND) {
                MethodInsnNode min = (MethodInsnNode)instructions.get(instructions.size()-1);
                pausableMethods.add(min);
            }
        }
    }
    
    private void inlineSubroutines() throws KilimException {
        markPausableJSRs();
        while (true) {
            ArrayList<BasicBlock> newBBs = null;
            for (BasicBlock bb: basicBlocks) {
                if (bb.hasFlag(INLINE_CHECKED)) continue;
                bb.setFlag(INLINE_CHECKED);
                if (bb.lastInstruction() == JSR) {
                    newBBs = bb.inline();
                    if (newBBs != null) {
                        break;
                    }
                }
            }
            if (newBBs == null) { 
                break;
            }
            int id = basicBlocks.size();
            for (BasicBlock bb: newBBs) {
                bb.setId(id++);
                basicBlocks.add(bb);
            }
        }
        // If there are any pausable subroutines, modify the JSRs/RETs to
        // GOTOs
        for (BasicBlock bb: basicBlocks) {
            bb.changeJSR_RET_toGOTOs();
        }
        
    }
    
    private void markPausableJSRs() throws KilimException {
        for (BasicBlock bb: basicBlocks) {
            bb.checkPausableJSR();
        }
    }
    
    
    boolean isPausableMethodInsn(MethodInsnNode min) {
        return pausableMethods.contains(min);
    }
    
    @Override
    public String toString() {
        ArrayList<BasicBlock> ret = getBasicBlocks();
        Collections.sort(ret);
        return ret.toString();
    }
    
    public BBList getBasicBlocks() {
        return basicBlocks;
    }
    
    @SuppressWarnings("unchecked")
    private void assignCatchHandlers() {
        ArrayList<TryCatchBlockNode> tcbs = (ArrayList<TryCatchBlockNode>) tryCatchBlocks;
        /// aargh. I'd love to create an array of Handler objects, but generics
        // doesn't care for it.
        if (tcbs.size() == 0) return;
        ArrayList<Handler> handlers= new ArrayList<Handler>(tcbs.size());
        
        for (int i = 0; i < tcbs.size(); i++) {
            TryCatchBlockNode tcb = tcbs.get(i);
            handlers.add(new Handler(
                    getLabelPosition(tcb.start),
                    getLabelPosition(tcb.end) - 1, // end is inclusive
                    tcb.type, 
                    getOrCreateBasicBlock(tcb.handler)));
        }
        for (BasicBlock bb : basicBlocks) {
            bb.chooseCatchHandlers(handlers);
        }
    }
    
    void buildBasicBlocks() {
        // preparatory phase
        int numInstructions = instructions.size(); 
        basicBlocks = new BBList();
        // Note: i modified within the loop
        for (int i = 0; i < numInstructions; i++) {
            Label l = getOrCreateLabelAtPos(i);
            BasicBlock bb = getOrCreateBasicBlock(l);
            i = bb.initialize(i); // i now points to the last instruction in bb. 
            basicBlocks.add(bb);
        }
    }
    
    /**
     * In live var analysis a BB asks its successor (in essence) about which
     * vars are live, mixes it with its own uses and defs and passes on a
     * new list of live vars to its predecessors. Since the information
     * bubbles up the chain, we iterate the list in reverse order, for
     * efficiency. We could order the list topologically or do a depth-first
     * spanning tree, but it seems like overkill for most bytecode
     * procedures. The order of computation doesn't affect the correctness;
     * it merely changes the number of iterations to reach a fixpoint.
     */
    private void doLiveVarAnalysis() {
        ArrayList<BasicBlock> bbs = getBasicBlocks();
        Collections.sort(bbs); // sorts in increasing startPos order
        
        boolean changed;
        do {
            changed = false;
            for (int i = bbs.size() - 1; i >= 0; i--) {
                changed = bbs.get(i).flowVarUsage() || changed;
            }
        } while (changed);
    }
    
    /**
     * In the first pass (buildBasicBlocks()), we create BBs whenever we
     * encounter a label. We don't really know until we are done with that
     * pass whether a label is the target of a branch instruction or it is
     * there because of an exception handler. See coalesceWithFollowingBlock()
     * for more detail.  
     */
    private void consolidateBasicBlocks() {
        BBList newBBs = new BBList(basicBlocks.size());
        int pos = 0;
        for (BasicBlock bb: basicBlocks) {
            if (!bb.hasFlag(COALESCED)) {
                bb.coalesceTrivialFollowers();
                // The original bb's followers should have been marked as processed.
                bb.setId(pos++);  
                newBBs.add(bb);
            }
        }
        basicBlocks = newBBs;
        assert checkNoBasicBlockLeftBehind();
    }
    
    private boolean checkNoBasicBlockLeftBehind() { // like "no child left behind"
        ArrayList<BasicBlock> bbs = basicBlocks;
        HashSet<BasicBlock> hs = new HashSet<BasicBlock>(bbs.size() * 2);
        hs.addAll(bbs);
        int prevBBend = -1;
        for (BasicBlock bb: bbs) {
            assert bb.isInitialized() : "BB not inited: " + bb;
            assert bb.startPos == prevBBend + 1;
            for (BasicBlock succ: bb.successors) {
                assert succ.isInitialized() : "Basic block not inited. Succ of " + bb;
                assert hs.contains(succ) : 
                    "BB not found:\n" + succ; 
            }
            prevBBend = bb.endPos;
        }
        assert bbs.get(bbs.size()-1).endPos == instructions.size()-1;
        return true;
    }
    
    private void dataFlow() {
        workset = new PriorityQueue<BasicBlock>(instructions.size(), new BBComparator());
        //System.out.println("Method: " + this.name);
        BasicBlock startBB = getBasicBlocks().get(0);
        assert startBB != null : "Null starting block in flowTypes()";
        startBB.startFrame = new Frame(classFlow.getClassDescriptor(), this);
        enqueue(startBB);
        
        while (!workset.isEmpty()) {
            BasicBlock bb = dequeue();
            bb.interpret();
        }
    }
    
    void setLabel(int pos, Label l) {
        for (int i = pos - posToLabelMap.size() + 1; i >= 0; i--) {
            //pad with nulls ala perl
            posToLabelMap.add(null);
        }
        assert posToLabelMap.get(pos) == null;
        posToLabelMap.set(pos, l);
        labelToPosMap.put(l, pos);
    }
    
    Label getOrCreateLabelAtPos(int pos) {
        Label ret = null;
        if (pos < posToLabelMap.size()) {
            ret = posToLabelMap.get(pos);
        }
        if (ret == null) {
            ret = new Label();
            setLabel(pos, ret);
        }
        return ret;
    }
    
    int getLabelPosition(Label l) {
        return labelToPosMap.get(l);
    }
    
    BasicBlock getOrCreateBasicBlock(Label l) { 
        BasicBlock ret = labelToBBMap.get(l);
        if (ret == null) {
            ret = new BasicBlock(this, l);
            Object oldVal = labelToBBMap.put(l, ret);
            assert oldVal == null : "Duplicate BB created at label";
        }
        return ret;
    }

    BasicBlock getBasicBlock(Label l) { 
        return labelToBBMap.get(l);
    }

    private BasicBlock dequeue() {
        BasicBlock bb = workset.poll();
        bb.unsetFlag(ENQUEUED);
        return bb;
    }
    
    void enqueue(BasicBlock bb) {
        assert bb.startFrame != null : "Enqueued null start frame";
        if (!bb.hasFlag(ENQUEUED)) {
            workset.add(bb);
            bb.setFlag(ENQUEUED);
        }
    }

    public Label getLabelAt(int pos) {
        return  (pos < posToLabelMap.size()) ? posToLabelMap.get(pos) : null;
    }

    void addInlinedBlock(BasicBlock bb) {
        bb.setId(basicBlocks.size());
        basicBlocks.add(bb);
    }

    @Override
    /**
     * Copied verbatim from MethodNode except for the instruction processing.
     * Unlike MethodNode, we don't keep LabelNodes inline, so we need to 
     * do visitLabel ourselves.
     * 
     * @param mv a method visitor.
     */
    public void accept(final MethodVisitor mv) {
        // visits the method attributes
        int i, j, n;
        if (annotationDefault != null) {
            AnnotationVisitor av = mv.visitAnnotationDefault();
            acceptAnnotation(av, null, annotationDefault);
            av.visitEnd();
        }
        n = visibleAnnotations == null ? 0 : visibleAnnotations.size();
        for (i = 0; i < n; ++i) {
            AnnotationNode an = (AnnotationNode) visibleAnnotations.get(i);
            an.accept(mv.visitAnnotation(an.desc, true));
        }
        n = invisibleAnnotations == null ? 0 : invisibleAnnotations.size();
        for (i = 0; i < n; ++i) {
            AnnotationNode an = (AnnotationNode) invisibleAnnotations.get(i);
            an.accept(mv.visitAnnotation(an.desc, false));
        }
        n = visibleParameterAnnotations == null
                ? 0
                : visibleParameterAnnotations.length;
        for (i = 0; i < n; ++i) {
            List<?> l = visibleParameterAnnotations[i];
            if (l == null) {
                continue;
            }
            for (j = 0; j < l.size(); ++j) {
                AnnotationNode an = (AnnotationNode) l.get(j);
                an.accept(mv.visitParameterAnnotation(i, an.desc, true));
            }
        }
        n = invisibleParameterAnnotations == null
                ? 0
                : invisibleParameterAnnotations.length;
        for (i = 0; i < n; ++i) {
            List<?> l = invisibleParameterAnnotations[i];
            if (l == null) {
                continue;
            }
            for (j = 0; j < l.size(); ++j) {
                AnnotationNode an = (AnnotationNode) l.get(j);
                an.accept(mv.visitParameterAnnotation(i, an.desc, false));
            }
        }
        n = attrs == null ? 0 : attrs.size();
        for (i = 0; i < n; ++i) {
            mv.visitAttribute((Attribute) attrs.get(i));
        }
        // visits the method's code
        if (instructions.size() > 0) {
            mv.visitCode();
            // visits try catch blocks
            for (i = 0; i < tryCatchBlocks.size(); ++i) {
                ((TryCatchBlockNode) tryCatchBlocks.get(i)).accept(mv);
            }
            // visits instructions
            for (i = 0; i < instructions.size(); ++i) {
                Label l = getLabelAt(i);
                if (l != null) {
                    mv.visitLabel(l);
                }
                ((AbstractInsnNode) instructions.get(i)).accept(mv);
            }
            Label l = getLabelAt(instructions.size());
            if (l != null) {
                mv.visitLabel(l);
            }
            // visits local variables
            n = localVariables == null ? 0 : localVariables.size();
            for (i = 0; i < n; ++i) {
                ((LocalVariableNode) localVariables.get(i)).accept(mv);
            }
            // visits line numbers
            n = lineNumbers == null ? 0 : lineNumbers.size();
            for (i = 0; i < n; ++i) {
                ((LineNumberNode) lineNumbers.get(i)).accept(mv);
            }
            // visits maxs
            mv.visitMaxs(maxStack, maxLocals);
        }
        mv.visitEnd();
    }

    public int getNumArgs() {
        int ret = TypeDesc.getNumArgumentTypes(desc);
        if (!isStatic()) ret++;
        return ret;
    }
    
    public boolean isPausable() {
        return hasPausableAnnotation;
    }
    
    public void setPausable(boolean isPausable) {
        hasPausableAnnotation = isPausable;
    }

    public static void acceptAnnotation(final AnnotationVisitor av, final String name,
            final Object value) {
        if (value instanceof String[]) {
            String[] typeconst = (String[]) value;
            av.visitEnum(name, typeconst[0], typeconst[1]);
        } else if (value instanceof AnnotationNode) {
            AnnotationNode an = (AnnotationNode) value;
            an.accept(av.visitAnnotation(name, an.desc));
        } else if (value instanceof List<?>) {
            AnnotationVisitor v = av.visitArray(name);
            List<?> array = (List<?>) value;
            for (int j = 0; j < array.size(); ++j) {
                acceptAnnotation(v, null, array.get(j));
            }
            v.visitEnd();
        } else {
            av.visit(name, value);
        }
    }

    public boolean isAbstract() {
        return ((this.access & Opcodes.ACC_ABSTRACT) != 0);
    }
    public boolean isStatic() {
        return ((this.access & ACC_STATIC) != 0);
    }

    public boolean isBridge() {
        return ((this.access & ACC_VOLATILE) != 0);
    }

	public Detector detector() {
		return this.classFlow.detector();
}


}


