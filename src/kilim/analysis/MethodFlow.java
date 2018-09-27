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
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeMap;

import kilim.KilimException;
import kilim.mirrors.Detector;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
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
     * Maps instructions[i] to LabelNode or null (if no label). Note that
     * LabelInsnNodes are not accounted for here because they themselves are not
     * labelled.
     */
    
    private ArrayList<LabelNode>           posToLabelMap;
    
    /**
     * Reverse map of posToLabelMap. Maps Labels to index within
     * method.instructions.
     */
    private HashMap<LabelNode, Integer>    labelToPosMap;
    
    /**
     * Maps labels to BasicBlocks
     */
    private HashMap<LabelNode, BasicBlock> labelToBBMap;
    
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
    
    final Detector detector;

    private TreeMap<Integer, LineNumberNode> lineNumberNodes = new TreeMap<Integer, LineNumberNode>();

    private HashMap<Integer, FrameNode> frameNodes = new HashMap<Integer, FrameNode>();

    private boolean hasPausableInvokeDynamic;
    
    /** copy of handlers provided by asm - null after being assigned to the BBs */
    ArrayList<Handler> origHandlers;
    
    /** stored value of the initial born variables */
    private BitSet firstBorn;
    
    public MethodFlow(
            ClassFlow classFlow,
            final int access,
            final String name,
            final String desc,
            final String signature,
            final String[] exceptions,
            final Detector detector) {
        super(Opcodes.ASM7_EXPERIMENTAL, access, name, desc, signature, exceptions);
        this.classFlow = classFlow;
        this.detector = detector;
        posToLabelMap = new ArrayList<LabelNode>();
        labelToPosMap = new HashMap<LabelNode, Integer>();
        labelToBBMap = new HashMap<LabelNode, BasicBlock>();

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

    public void restoreNonInstructionNodes() {
        InsnList newinsns = new InsnList();
        int sz = instructions.size();
        for (int i = 0; i < sz; i++) {
            LabelNode l = getLabelAt(i);
            if (l != null) {
                newinsns.add(l);
            }
            LineNumberNode ln = lineNumberNodes.get(i);
            if (ln != null) {
                newinsns.add(ln);
            }
            AbstractInsnNode ain = instructions.get(i);
            newinsns.add(ain);
        }
        
        LabelNode l = getLabelAt(sz);
        if (l != null) {
            newinsns.add(l);
        }
        LineNumberNode ln = lineNumberNodes.get(sz);
        if (ln != null) {
            newinsns.add(ln);
        }
        super.instructions = newinsns;
    }

    
    public void analyze() throws KilimException {
        preAssignCatchHandlers();
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
        
        String methodText = toString(classFlow.getClassName(),this.name,this.desc);
        boolean ctor = this.name.endsWith("init>");

        // constructors cannot be pausable because they must begin with the super call
        // meaning the weaver is unable to inject the preamble
        // and a super with side effects would get called multiple times
        // refuse to weave them
        if (ctor & hasPausableAnnotation)
            throw new KilimException("Constructors cannot be declared Pausable: " + methodText + "\n");

        if (!hasPausableAnnotation && !pausableMethods.isEmpty()) {
            String msg = ctor
                    ? "Constructor " + methodText + " illegally calls pausable methods:\n"
                    : methodText + " should be marked pausable. It calls pausable methods\n";
            for (MethodInsnNode min: pausableMethods)
                msg += toString(min.owner, min.name, min.desc) + '\n';
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
    public void visitMethodInsn(int opcode, String owner, String name, String desc,boolean itf) {
        super.visitMethodInsn(opcode, owner, name, desc, itf);
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
    
    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
        if (!classFlow.isWoven) {
            if (bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")) {
                Handle lambdaBody = (Handle)bsmArgs[1];
                String lambdaDesc = lambdaBody.getDesc();
                if (detector.isPausable(lambdaBody.getOwner(), lambdaBody.getName(), lambdaDesc)) {
                    hasPausableInvokeDynamic = true;
                }
            }
        }
        super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
    }
    
    @Override
    public void visitLabel(Label label) {
        setLabel(instructions.size(), super.getLabelNode(label));
    }
    
    @Override
    public void visitLineNumber(int line, Label start) {
        LabelNode ln = getLabelNode(start);
        lineNumberNodes.put(instructions.size(), new LineNumberNode(line, ln));
    }

    void visitLineNumbers(MethodVisitor mv) {
        for (LineNumberNode node : lineNumberNodes.values()) {
            mv.visitLineNumber(node.line, node.start.getLabel());
        }
    }

    
    @Override
    public void visitFrame(int type, int nLocal, Object[] local, int nStack,
            Object[] stack) {
        frameNodes.put(instructions.size(), new FrameNode(type, nLocal, local, nStack, stack));
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

    private void preAssignCatchHandlers() {
        @SuppressWarnings("unchecked")
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
        Collections.sort(handlers, Handler.startComparator());
        origHandlers = handlers;
        buildHandlerMap();
    }
    private void assignCatchHandlers() {
        if (origHandlers==null) return;
        for (BasicBlock bb : basicBlocks) {
            bb.chooseCatchHandlers(origHandlers);
        }
        origHandlers = null;
        handlerMap = null;
    }
    
    private int [] handlerMap;
    private void buildHandlerMap() {
        handlerMap = new int[instructions.size()];
        for (int ki=0; ki < handlerMap.length; ki++) handlerMap[ki] = -1;
        int ki = 0;
        for (int kh=0; kh < origHandlers.size(); kh++) {
            Handler ho = origHandlers.get(kh);
            for (; ki <= ho.from; ki++)
                handlerMap[ki] = kh;
        }
    }

    /** return the next handler.from >= start, else -1 - valid only until catch handlers are assigned */
    int mapHandler(int start) {
        if (handlerMap==null || start >= handlerMap.length) return -1;
        int map = handlerMap[start];
        return map < 0 ? -1 : origHandlers.get(map).from;
    }
    
    
    void buildBasicBlocks() {
        // preparatory phase
        int numInstructions = instructions.size(); 
        basicBlocks = new BBList();
        // Note: i modified within the loop
        for (int i = 0; i < numInstructions; i++) {
            LabelNode l = getOrCreateLabelAtPos(i);
            BasicBlock bb = getOrCreateBasicBlock(l);
            i = bb.initialize(i); // i now points to the last instruction in bb. 
            basicBlocks.add(bb);
        }
    }

    private boolean calcBornOnce() {
        BBList bbs = getBasicBlocks();
        LinkedList<BasicBlock> pending = new LinkedList();
        boolean changed = false, first = true;
        pending.add(bbs.get(0));
        bbs.get(0).usage.evalBornIn(null,new BitSet());
        while (! pending.isEmpty()) {
            BasicBlock bb = pending.pop();
            if (bb.visited) continue;
            bb.visited = true;
            for (Handler ho : bb.handlers) {
                changed |= ho.catchBB.usage.evalBornIn(bb.usage,null);
                pending.addFirst(ho.catchBB);
            }
            BitSet combo = bb.usage.getCombo();
            if (first)
                combo.or(firstBorn);
            for (BasicBlock so : bb.successors) {
                changed |= so.usage.evalBornIn(bb.usage,combo);
                pending.addFirst(so);
            }
            first = false;
        }
        return changed;
    }
    
    private void calcBornUsage() {
        // defined vars don't mix into handlers, so do 2 passes
        //   propogate predecessor to successor (def,born) and handler (born)
        //   mix def into born
        while (true)
            if (! calcBornOnce()) break;
        getBasicBlocks().get(0).usage.initBorn(firstBorn);
        for (BasicBlock bb : getBasicBlocks())
            bb.usage.mergeBorn();
    }
    
    /** print the bit by bit liveness data after calculation */
    public static boolean debugPrintLiveness = false;
    
    /**
     * In live var analysis a BB asks its successor (in essence) about which
     * vars are live, mixes it with its own uses and defs and passes on a
     * new list of live vars to its predecessors. Since the information
     * bubbles up the chain, we iterate the list in reverse order, for
     * efficiency. We could order the list topologically or do a depth-first
     * spanning tree, but it seems like overkill for most bytecode
     * procedures. The order of computation doesn't affect the correctness;
     * it merely changes the number of iterations to reach a fixpoint.
     * 
     * the algorithm has been updated to track vars that been born, ie def'd by a parameter
     */
    private void doLiveVarAnalysis() {
        ArrayList<BasicBlock> bbs = getBasicBlocks();
        Collections.sort(bbs); // sorts in increasing startPos order
        
        firstBorn = setArgsBorn(bbs.get(0));
        
        boolean changed;
        calcBornUsage();
        do {
            changed = false;
            for (int i = bbs.size() - 1; i >= 0; i--) {
                changed = bbs.get(i).flowVarUsage() || changed;
            }
        } while (changed);
        if (debugPrintLiveness) printUsage(bbs);
    }

    private BitSet setArgsBorn(BasicBlock bb) {
        BitSet born = new BitSet(bb.flow.maxLocals);
        int offset = 0;
        if (!isStatic())
            born.set(offset++);
        for (String arg : TypeDesc.getArgumentTypes(desc)) {
            born.set(offset);
            offset += TypeDesc.isDoubleWord(arg) ? 2 : 1;
        }
        return born;
    }
    
    void printUsage(ArrayList<BasicBlock> bbs) {
        System.out.println(name);
        if (bbs==null) bbs = getBasicBlocks();
        for (BasicBlock bb : bbs) {
            Usage uu = bb.usage;
            String range = String.format("%4d %4d: ",bb.startPos,bb.endPos);
            System.out.print(range + uu.toStringBits("  "));
            System.out.println(" -- " + bb.printGeniology());
        }
        System.out.format("\n\n");
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
                assert succ.isInitialized() : "Basic block not inited: " + succ +"\nSuccessor of " + bb;
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
    
    void setLabel(int pos, LabelNode l) {
        for (int i = pos - posToLabelMap.size() + 1; i >= 0; i--) {
            // pad with nulls ala perl
            posToLabelMap.add(null);
        }
        posToLabelMap.set(pos, l);
        labelToPosMap.put(l, pos);
    }
    
    LabelNode getOrCreateLabelAtPos(int pos) {
        LabelNode ret = null;
        if (pos < posToLabelMap.size()) {
            ret = posToLabelMap.get(pos);
        }
        if (ret == null) {
            ret = new LabelNode();
            setLabel(pos, ret);
        }
        return ret;
    }
    
    int getLabelPosition(LabelNode l) {
        return labelToPosMap.get(l);
    }
    
    BasicBlock getOrCreateBasicBlock(LabelNode l) {
        BasicBlock ret = labelToBBMap.get(l);
        if (ret == null) {
            ret = new BasicBlock(this, l);
            Object oldVal = labelToBBMap.put(l, ret);
            assert oldVal == null : "Duplicate BB created at label";
        }
        return ret;
    }

    BasicBlock getBasicBlock(LabelNode l) { 
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

    public LabelNode getLabelAt(int pos) {
        return  (pos < posToLabelMap.size()) ? posToLabelMap.get(pos) : null;
    }

    void addInlinedBlock(BasicBlock bb) {
        bb.setId(basicBlocks.size());
        basicBlocks.add(bb);
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


    public void resetLabels() {
        for (int i = 0; i < posToLabelMap.size(); i++) {
            LabelNode ln = posToLabelMap.get(i);
            if (ln != null) {
                ln.resetLabel();
            }
        }
    }
        

    boolean needsWeaving() {
        return isPausable() || hasPausableInvokeDynamic;
    }


}


