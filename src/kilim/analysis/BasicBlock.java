/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim.analysis;
import static kilim.Constants.D_ARRAY_BOOLEAN;
import static kilim.Constants.D_ARRAY_BYTE;
import static kilim.Constants.D_ARRAY_CHAR;
import static kilim.Constants.D_ARRAY_DOUBLE;
import static kilim.Constants.D_ARRAY_FLOAT;
import static kilim.Constants.D_ARRAY_INT;
import static kilim.Constants.D_ARRAY_LONG;
import static kilim.Constants.D_ARRAY_SHORT;
import static kilim.Constants.D_BOOLEAN;
import static kilim.Constants.D_BYTE;
import static kilim.Constants.D_CHAR;
import static kilim.Constants.D_DOUBLE;
import static kilim.Constants.D_FLOAT;
import static kilim.Constants.D_INT;
import static kilim.Constants.D_LONG;
import static kilim.Constants.D_NULL;
import static kilim.Constants.D_RETURN_ADDRESS;
import static kilim.Constants.D_SHORT;
import static kilim.Constants.D_VOID;
import static kilim.Constants.TASK_CLASS;
import static kilim.Constants.THROWABLE_CLASS;
import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import kilim.KilimException;
import kilim.mirrors.Detector;

import org.objectweb.asm.Label;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * A basic block is a contiguous set of instructions that has one label at the
 * first instruction and a transfer-of-control instruction at the very end. A
 * transfer-of-control instruction includes all branching instructions that have
 * labelled targets (IF_x, GOTO, and JSR) and the rest (ATHROW, xRETURN, RET).
 * There can be no target labels in the middle of a basic block; in other words,
 * you can't jump into the middle of a basic block. This is the standard
 * definition; we make a few changes.
 * 
 * <dl>
 * <li>
 * We create BasicBlocks whenever we encounter a label (in a linear
 * scanning of a method's instructions. Some labels are meant for catch
 * handlers and debug (line number) information only; they are not the
 * target of a branching instruction, but we don't know that in the
 * first pass. We coalesce those BasicBlocks that merely follow
 * another, provided the preceding BB is the only preceder. Note that
 * blocks connected with a GOTO can't coalesce because they are not
 * likely to be contiguous, even if they obey the constraint of a
 * single edge. We also don't coalesce blocks starting with a pausable method
 * invocation with their predecessor, because we need these blocks to
 * tell us about downstream usage of local vars to help us generate
 * optimal continuations. </li>

 * 
 * <li> All catch handlers that intersect a basic block are treated as
 * successors to the block, for the purposes of liveness analysis.
 * 
 * <li> Subroutines (targets of JSR) are treated specially. We inline all JSR
 * calls, including nested JSRs, to simplify liveness analysis. In this phase, a
 * JSR/RET is treated the same as a GOTO sub followed by a GOTO to the caller.
 * During the weaving phase, we ignore the inlining information if the
 * subroutine doesn't have any pausable methods. If it does, then we spit out
 * duplicate code, complete with GOTOs as described above. This allows us to
 * jump in the middle of a "finally" block during rewinding.
 * 
 * Note: The JVM reference doesn't specify the boundaries of a JSR instruction;
 * in other words, there is no definitive way of saying which blocks belong to a
 * subroutine. This code treats the set of all nodes reachable via branching
 * instructions from the subroutine's entry point. (exception catch blocks don't
 * count) </li>
 * </dl>
 */
public class BasicBlock implements Comparable<BasicBlock> {

    /**
     * A number handed out in increasing order of starting position, to ease
     * sorting as well as for debug information
     */
    public int                    id;

    /*
     * One of the bit flags above.
     */
    int                           flags;

    /*
     * Used by the flow analysis algorithm to mark this BB as enqueued for
     * processing
     */
    static final int              ENQUEUED           = 1;

    /*
     * Used by the JSR inlining process to signify that a subroutine (a JSR
     * target) has been claimed by a corresponding call. All other JSR calls
     * pointing to this subroutine have to make their own duplicates.
     */
    static final int              SUBROUTINE_CLAIMED = 1 << 1;
    /*
     * Flag used by the consolidation process to avoid processing this block
     * again.
     */
    static final int              COALESCED          = 1 << 2;
    /*
     * Set if this BB contains a call to a pausable method
     */
    static final int              PAUSABLE           = 1 << 4;
    /*
     * Set if this block is the entry point to a subroutine and the target of
     * one or more JSR instructions
     */
    static final int              IS_SUBROUTINE      = 1 << 5;
    /*
     * Set if this block belongs to a subroutine
     */
    static final int              SUB_BLOCK          = 1 << 6;
    /*
     * Set by the subroutine inlining phase to avoid rechecking this BB.
     */
    static final int              INLINE_CHECKED     = 1 << 7;

    /*
     * Set for the entry point to a subroutine that contains a pausable
     * method. The entry point is the target of a JSR instruction.
     */
    static final int              PAUSABLE_SUB           = 1 << 8;
    /**
     * The flow to which this BB belongs.
     */
    public MethodFlow             flow;

    /**
     * The label that starts this BB. In some cases we create a label where it
     * didn't exist originally (after a jmp instruction, for example). This
     * allows us a unique indexing scheme.
     */
    public Label                  startLabel;

    /**
     * Start and end points (both inclusive) in the current method's list of
     * instructions (this.flow.instructions)
     */
    public int                    startPos = -1;

    public int                    endPos = -1;

    /**
     * List of successors (follower and all branch targets). Should be null 
     */
    public ArrayList<BasicBlock>  successors         = new ArrayList<BasicBlock>(3);

    public ArrayList<Handler>     handlers           = new ArrayList<Handler>(2);

    int                           numPredecessors;

    /**
     * usage initially contains the usage of local variables in this block
     * (without reference to any other block). After flow analysis it contains
     * the combined effect of this and all downstream blocks
     */
    public Usage                  usage;

    /**
     * A cached version of all sucessors' usage, successors being catch handlers
     * and real successors.
     */
    ArrayList<Usage>      succUsage;

    /**
     * The frame at the BB's entry point. It changes when propagating changes
     * from its predeccessors, until there's a fixed point.
     */
    public Frame                  startFrame;

    /*
     * If this BB is a catch block (the entry point to a series of catch handler
     * blocks, it contains the type of the exception
     */

    String                caughtExceptionType;

    /*
     * The BB that follows this BB. Is null if the last instruction is a GOTO or
     * THROW or RETURN or RET. The follower is also part of the successors list.
     */
    BasicBlock            follower;

    /*
     * sa subroutine, subBlocks contains the list of BBs that belong to it.
     */
    ArrayList<BasicBlock> subBlocks;
    
    public BasicBlock(MethodFlow aflow, Label aStartLabel) {
        flow = aflow;
        startLabel = aStartLabel;
        usage = new Usage(aflow.maxLocals);
        successors = new ArrayList<BasicBlock>(2);
    }

    Detector detector() {
    	return flow.detector();
    }
    
    /**
     * Absorb as many instructions until the next label or the next transfer of
     * control instruction. In the first pass we may end up creating many many
     * BBs because there may be a lot of non-target labels (esp. when debug
     * information is available). The constraints are as follows:
     *   1. A transfer of control instruction must be the last instruction. It 
     *      may also be the first (and only) instruction
     *   2. A labeled instruction must be the first instruction in a BB. It
     *      may optionally be the last (and only) instruction
     *   3. A pausable method is treated like a labeled instruction, and is 
     *      given a label if there isn't one already. Constraint 2 applies.
     */
    @SuppressWarnings("unchecked")
    int initialize(int pos) {
        AbstractInsnNode ain;
        startPos = pos;

        BasicBlock bb;
        boolean endOfBB = false;
        boolean hasFollower = true;
        int size = flow.instructions.size();
        for (; pos < size; pos++) {
            if (pos > startPos && flow.getLabelAt(pos) != null) {
                pos--;
                hasFollower = true;
                endOfBB = true;
                break;
            }
            ain = getInstruction(pos);
            int opcode = ain.getOpcode();
            switch (opcode) {
                case ALOAD:
                case ILOAD:
                case LLOAD:
                case FLOAD:
                case DLOAD:
                    usage.read(((VarInsnNode) ain).var);
                    break;

                case ISTORE:
                case LSTORE:
                case FSTORE:
                case DSTORE:
                case ASTORE:
                    usage.write(((VarInsnNode) ain).var);
                    break;
                    
                case IINC:
                    int v = ((IincInsnNode)ain).var;
                    usage.read(v);
                    usage.write(v);
                    break;

                case IFEQ:
                case IFNE:
                case IFLT:
                case IFGE:
                case IFGT:
                case IFLE:
                case IFNULL:
                case IFNONNULL:
                case IF_ICMPEQ:
                case IF_ICMPNE:
                case IF_ICMPLT:
                case IF_ICMPGE:
                case IF_ICMPGT:
                case IF_ICMPLE:
                case IF_ACMPEQ:
                case IF_ACMPNE:
                case JSR:
                case GOTO:
                    Label l = ((JumpInsnNode) ain).label;
                    bb = flow.getOrCreateBasicBlock(l);
                    if (opcode == JSR) {
                        bb.setFlag(IS_SUBROUTINE);
                        hasFollower = false;
                    }
                    addSuccessor(bb);
                    if (opcode == GOTO) {
                        hasFollower = false;
                    }
                    endOfBB = true;
                    break;

                case RET:
                case IRETURN:
                case LRETURN:
                case FRETURN:
                case DRETURN:
                case ARETURN:
                case RETURN:
                case ATHROW:
                    hasFollower = false;
                    endOfBB = true;
                    break;

                case TABLESWITCH:
                case LOOKUPSWITCH:
                    Label defaultLabel;
                    List<Label> otherLabels;
                    if (opcode == TABLESWITCH) {
                        defaultLabel = ((TableSwitchInsnNode) ain).dflt;
                        otherLabels = ((TableSwitchInsnNode) ain).labels;
                    } else {
                        defaultLabel = ((LookupSwitchInsnNode) ain).dflt;
                        otherLabels = ((LookupSwitchInsnNode) ain).labels;
                    }
                    for (Iterator<Label> it = otherLabels.iterator(); it.hasNext();) {
                        l = (Label) it.next();
                        addSuccessor(flow.getOrCreateBasicBlock(l));
                    }
                    addSuccessor(flow.getOrCreateBasicBlock(defaultLabel));
                    endOfBB = true;
                    hasFollower = false;
                    break;

                case INVOKEVIRTUAL:
                case INVOKESTATIC:
                case INVOKEINTERFACE:
                case INVOKESPECIAL:
                    if (flow.isPausableMethodInsn((MethodInsnNode) ain)) {
                        if (pos == startPos) {
                            setFlag(PAUSABLE);
                        } else {
                            l = flow.getOrCreateLabelAtPos(pos);
                            bb = flow.getOrCreateBasicBlock(l);
                            bb.setFlag(PAUSABLE);
                            addSuccessor(bb);
                            pos--; // don't consume this instruction
                            hasFollower = true;
                            endOfBB = true;
                        }
                    }
                    break;

                default:
                	if (opcode >= 26 && opcode <= 45)
                    	throw new IllegalStateException("instruction variants not expected here");
                	
                    break;
            }

            if (endOfBB) break;
        }
        endPos = pos;
        if (hasFollower && (pos + 1) < flow.instructions.size()) {
            // add the following basic block as a successor
            Label l = flow.getOrCreateLabelAtPos(pos + 1);
            bb = flow.getOrCreateBasicBlock(l);
            addFollower(bb);
        }

        return pos;
    }

    void addFollower(BasicBlock bb) {
        this.follower = bb;
        addSuccessor(bb);
    }

    void addSuccessor(BasicBlock bb) {
        if (!successors.contains(bb)) { 
            this.successors.add(bb); 
            bb.numPredecessors++;
        }
    }

    public Usage getVarUsage() {
        return usage;
    }

    int lastInstruction() {
        AbstractInsnNode ainode = getInstruction(endPos);
        return ainode.getOpcode();
    }
    
    /*
     * Blocks connected by an edge are candidates for coalescing if: <dl> <li>
     * There is a single edge between the two and neither has any other edges.
     * </li>
     * 
     * <li> The edge connecting the two is not because of a GOTO. We only want
     * those where one block falls into the other. The reason is that each block
     * marks out a *contiguous* range of instructions. Most compilers would have
     * gotten rid of this unnecessary jump anyway. </li>
     * 
     * <li> The successor block doesn't begin with a method call that we are
     * interested in (like pausable methods). This is a boundary we are
     * interested in maintaining in subsequent processing. </li>
     * 
     * </dl>
     */
    void coalesceTrivialFollowers() {
        while (true) {
            if (successors.size() == 1) {
                BasicBlock succ = successors.get(0);
                if (succ.numPredecessors == 1 && lastInstruction() != GOTO && lastInstruction() != JSR
                        && !succ.isPausable()) {
                    // successor can be merged
                    // absorb succesors and usage mask
                    this.successors = succ.successors;
                    this.follower = succ.follower;
                    this.usage.absorb(succ.usage);
                    this.endPos = succ.endPos;
                    succ.setFlag(COALESCED);
                    // mark succ so it doesn't get visited. This block's merk remains 0. We'll let the outer driver
                    // loop to
                    // revisit this block and its new successors
                    continue;
                }
            }
            break;
        }

    }

    // Made public for testing purposes
    public void setFlag(int bitFlag) {
        flags |= bitFlag;
    }

    public void unsetFlag(int bitFlag) {
        flags &= ~bitFlag;
    }

    public boolean hasFlag(int bitFlag) {
        return (flags & bitFlag) != 0;
    }

    public int compareTo(BasicBlock o) {
        if (this.id == o.id) {
            assert this == o; // Just in case we have mistakenly assigned the
            // same id to different BBs
            return 0;
        }
        return this.id < o.id ? -1 : +1;
    }

    /*
     * This is the main workhorse of the flow analysis phase, translating each
     * instruction's effects on the stack and local variables. Unlike the 
     * verifier which tracks the flow  of types, this method tracks values,
     * which allows us to track types as well as the flow of constant values
     * and set the stage for SSA-style optimizations.
     */
    void interpret() {
        Value v, v1, v2, v3, v4;
        Frame frame = startFrame.dup();
        if (isCatchHandler()) {
            // When an exception is thrown, the stack is cleared
            // and the thrown exception is pushed into the stack
            frame.clearStack();
            frame.push(Value.make(startPos, caughtExceptionType));
        } else if (hasFlag(IS_SUBROUTINE)) {
            // The target of a JSR instruction has a JVM-internal
            // return address which we model with a type of its
            // own
            frame.push(Value.make(startPos, D_RETURN_ADDRESS));
        }
        String componentType = null;
        @SuppressWarnings("unused")
        boolean canThrowException = false;
        boolean propagateFrame = true;
        int i = 0;
        try {
            for (i = startPos; i <= endPos; i++) {
                AbstractInsnNode ain = getInstruction(i);
                int opcode = ain.getOpcode();
                int val, var;
                switch (opcode) {
                    case NOP:
                        break;
                    case ACONST_NULL:
                        frame.push(Value.make(i, D_NULL));
                        break;
                        
                    case ICONST_M1:
                    case ICONST_0:
                    case ICONST_1:
                    case ICONST_2:
                    case ICONST_3:
                    case ICONST_4:
                    case ICONST_5:
                        frame.push(Value.make(i, D_INT, new Integer(opcode
                                - ICONST_0)));
                        break;
                        
                        
                    case LCONST_0:
                    case LCONST_1:
                        frame.push(Value.make(i, D_LONG, new Long(opcode - LCONST_0)));
                        break;
                        
                    case ILOAD:
                    case LLOAD:
                    case FLOAD:
                    case DLOAD:
                    case ALOAD:
                        var = ((VarInsnNode)ain).var;
                        v = frame.getLocal(var, opcode);
                        frame.push(v);
                        break;
                        
                    case FCONST_0:
                    case FCONST_1:
                    case FCONST_2:
                        frame.push(Value.make(i, D_FLOAT, new Float(opcode
                                - FCONST_0)));
                        break;
                        
                    case DCONST_0:
                    case DCONST_1:
                        frame.push(Value.make(i, D_DOUBLE, new Double(opcode
                                - DCONST_0)));
                        break;
                        
                        
                    case BIPUSH:
                        val = ((IntInsnNode) ain).operand;
                        frame.push(Value.make(i, D_BYTE, new Integer(val)));
                        break;
                        
                    case SIPUSH:
                        val = ((IntInsnNode) ain).operand;
                        frame.push(Value.make(i, D_SHORT, new Integer(val)));
                        break;
                        
                    case LDC:
                        Object cval = ((LdcInsnNode) ain).cst;
                        frame.push(Value.make(i, TypeDesc.getTypeDesc(cval), cval));
                        break;
                        
                        
                    case IALOAD:
                    case LALOAD:
                    case FALOAD:
                    case DALOAD:
                    case AALOAD:
                    case BALOAD:
                    case CALOAD:
                    case SALOAD:
                        canThrowException = true;
                        frame.popWord(); // pop index
                        v = frame.popWord(); // array ref
                        frame.push(Value.make(i, TypeDesc.getComponentType(v.getTypeDesc()))); // push
                        // component
                        // of
                        // array
                        break;
                        
                    case ISTORE:
                    case LSTORE:
                    case FSTORE:
                    case DSTORE:
                    case ASTORE:
                        v1 = frame.pop();
                        var = ((VarInsnNode) ain).var;
                        frame.setLocal(var, v1);
                        break;
                        
                    case IASTORE:
                    case LASTORE:
                    case FASTORE:
                    case DASTORE:
                    case AASTORE:
                    case BASTORE:
                    case CASTORE:
                    case SASTORE:
                        canThrowException = true;
                        frame.popn(3);
                        break;
                        
                    case POP:
                        frame.popWord();
                        break;
                        
                    case POP2:
                        if (frame.pop().isCategory1()) {
                            frame.popWord();
                        }
                        break;
                        
                    case DUP:
                        // ... w => ... w w
                        v = frame.popWord();
                        frame.push(v);
                        frame.push(v);
                        break;
                        
                    case DUP_X1:
                        // Insert top word beneath the next word
                        // .. w2 w1 => .. w1 w2 w1
                        v1 = frame.popWord();
                        v2 = frame.popWord();
                        frame.push(v1);
                        frame.push(v2);
                        frame.push(v1);
                        break;
                        
                    case DUP_X2:
                        // Insert top word beneath the next two words (or dword)
                        v1 = frame.popWord();
                        v2 = frame.pop();
                        if (v2.isCategory1()) {
                            v3 = frame.pop();
                            if (v3.isCategory1()) {
                                // w3,w2,w1 => w1,w3,w2,w1
                                frame.push(v1);
                                frame.push(v3);
                                frame.push(v2);
                                frame.push(v1);
                                break;
                            }
                        } else {
                            // dw2,w1 => w1,dw2,w1
                            frame.push(v1);
                            frame.push(v2);
                            frame.push(v1);
                            break;
                        }
                        throw new InternalError("Illegal use of DUP_X2");
                        
                    case DUP2:
                        // duplicate top two words (or dword)
                        v1 = frame.pop();
                        if (v1.isCategory1()) {
                            v2 = frame.pop();
                            if (v2.isCategory1()) {
                                // w2,w1 => w2,w1,w2,w1
                                frame.push(v2);
                                frame.push(v1);
                                frame.push(v2);
                                frame.push(v1);
                                break;
                            }
                        } else {
                            // dw1 => dw1,dw1
                            frame.push(v1);
                            frame.push(v1);
                            break;
                        }
                        throw new InternalError("Illegal use of DUP2");
                        
                    case DUP2_X1:
                        // insert two words (or dword) beneath next word
                        v1 = frame.pop();
                        if (v1.isCategory1()) {
                            v2 = frame.pop();
                            if (v2.isCategory1()) {
                                v3 = frame.popWord();
                                // w3,w2,w1 => w2,w1,w3,w2,w1
                                frame.push(v2);
                                frame.push(v1);
                                frame.push(v3);
                                frame.push(v2);
                                frame.push(v1);
                                break;
                            }
                        } else { // TypeDesc.isDoubleWord(t1)
                            // w2,dw1 => dw1,w2,dw1
                            v2 = frame.popWord();
                            frame.push(v1);
                            frame.push(v2);
                            frame.push(v1);
                            break;
                        }
                        throw new InternalError("Illegal use of DUP2_X1");
                    case DUP2_X2:
                        // insert two words (or dword) beneath next two words (or
                        // dword)
                        v1 = frame.pop();
                        if (v1.isCategory1()) {
                            v2 = frame.pop();
                            if (v2.isCategory1()) {
                                v3 = frame.pop();
                                if (v3.isCategory1()) {
                                    v4 = frame.pop();
                                    if (v4.isCategory1()) {
                                        // w4,w3,w2,w1 => w2,w1,w4,w3,w2,w1
                                        frame.push(v2);
                                        frame.push(v1);
                                        frame.push(v4);
                                        frame.push(v3);
                                        frame.push(v2);
                                        frame.push(v1);
                                        break;
                                    }
                                } else { // TypeDesc.isDoubleWord(t3)
                                    // dw3,w2,w1 => w2,w1,dw3,w2,w1
                                    frame.push(v2);
                                    frame.push(v1);
                                    frame.push(v3);
                                    frame.push(v2);
                                    frame.push(v1);
                                    break;
                                }
                            }
                        } else { // TypeDesc.isDoubleWord(t1)
                            v2 = frame.pop();
                            if (v2.isCategory1()) {
                                v3 = frame.pop();
                                if (v3.isCategory1()) {
                                    // w3,w2,dw1 => dw1,w3,w2,dw1
                                    frame.push(v1);
                                    frame.push(v3);
                                    frame.push(v2);
                                    frame.push(v1);
                                    break;
                                }
                            } else {
                                // dw2,dw1 => dw1,dw2,dw1
                                frame.push(v1);
                                frame.push(v2);
                                frame.push(v1);
                                break;
                            }
                        }
                        throw new InternalError("Illegal use of DUP2_X2");
                        
                    case SWAP:
                        // w2, w1 => w1, w2
                        v1 = frame.popWord();
                        v2 = frame.popWord();
                        frame.push(v1);
                        frame.push(v2);
                        break;
                        
                    case IDIV:
                    case IREM:
                    case LDIV:
                    case LREM:
                        frame.pop(); // See next case
                        canThrowException = true;
                        break;
                        
                    case IADD:
                    case LADD:
                    case FADD:
                    case DADD:
                    case ISUB:
                    case LSUB:
                    case FSUB:
                    case DSUB:
                    case IMUL:
                    case LMUL:
                    case FMUL:
                    case DMUL:
                    case FDIV:
                    case DDIV:
                    case FREM:
                    case DREM:
                    case ISHL:
                    case LSHL:
                    case ISHR:
                    case LSHR:
                    case IUSHR:
                    case LUSHR:
                    case IAND:
                    case LAND:
                    case IOR:
                    case LOR:
                    case IXOR:
                    case LXOR:
                        // Binary op.
                        frame.pop();
                        v = frame.pop();
                        // The result is always the same type as the first arg
                        frame.push(Value.make(i, v.getTypeDesc()));
                        break;
                        
                    case LCMP:
                    case FCMPL:
                    case FCMPG:
                    case DCMPL:
                    case DCMPG:
                        frame.popn(2);
                        frame.push(Value.make(i, D_INT));
                        break;
                        
                    case INEG:
                    case LNEG:
                    case FNEG:
                    case DNEG:
                        v = frame.pop();
                        frame.push(Value.make(i, v.getTypeDesc()));
                        break;
                        
                    case IINC:
                        var = ((IincInsnNode) ain).var;
                        frame.setLocal(var, Value.make(i, D_INT));
                        break;
                        
                    case I2L:
                    case F2L:
                    case D2L:
                        frame.pop();
                        frame.push(Value.make(i, D_LONG));
                        break;
                        
                    case I2D:
                    case L2D:
                    case F2D:
                        frame.pop();
                        frame.push(Value.make(i, D_DOUBLE));
                        break;
                        
                    case I2F:
                    case L2F:
                    case D2F:
                        frame.pop();
                        frame.push(Value.make(i, D_FLOAT));
                        break;
                        
                    case L2I:
                    case F2I:
                    case D2I:
                        frame.pop();
                        frame.push(Value.make(i, D_INT));
                        break;
                        
                    case I2B:
                        frame.popWord();
                        frame.push(Value.make(i, D_BOOLEAN));
                        break;
                        
                    case I2C:
                        frame.popWord();
                        frame.push(Value.make(i, D_CHAR));
                        break;
                        
                    case I2S:
                        frame.popWord();
                        frame.push(Value.make(i, D_SHORT));
                        break;
                        
                    case IFEQ:
                    case IFNE:
                    case IFLT:
                    case IFGE:
                    case IFGT:
                    case IFLE:
                    case IFNULL:
                    case IFNONNULL:
                        frame.popWord();
                        break;
                        
                    case IF_ICMPEQ:
                    case IF_ICMPNE:
                    case IF_ICMPLT:
                    case IF_ICMPGE:
                    case IF_ICMPGT:
                    case IF_ICMPLE:
                    case IF_ACMPEQ:
                    case IF_ACMPNE:
                        frame.popn(2);
                        break;
                        
                    case GOTO:
                    case JSR: // note: the targetBB pushes the return address
                        // itself
                        // because it is marked with isSubroutine
                    case RET:
                        break;
                        
                    case TABLESWITCH:
                    case LOOKUPSWITCH:
                        frame.pop();
                        break;
                        
                    case IRETURN:
                    case LRETURN:
                    case FRETURN:
                    case DRETURN:
                    case ARETURN:
                    case RETURN:
                        canThrowException = true;
                        if (opcode != RETURN) {
                            frame.pop();
                        }
                        if (frame.stacklen != 0) {
                            throw new InternalError("stack non null at method return");
                        }
                        break;
                        
                    case GETSTATIC:
                        canThrowException = true;
                        v = Value.make(i, TypeDesc.getInterned(((FieldInsnNode) ain).desc));
                        frame.push(v);
                        break;
                        
                    case PUTSTATIC:
                        canThrowException = true;
                        frame.pop();
                        break;
                        
                    case GETFIELD:
                        canThrowException = true;
                        v1 = frame.pop();
                        v = Value.make(i, TypeDesc.getInterned(((FieldInsnNode) ain).desc));
                        //if (TypeDesc.isRefType(v.getTypeDesc())) {
                        //    System.out.println("GETFIELD " + ((FieldInsnNode)ain).name  + ": " + v + "---->" + v1);
                        //}
                        frame.push(v);
                        break;
                        
                    case PUTFIELD:
                        canThrowException = true;
                        v1 = frame.pop();
                        v = frame.pop();
                        //if (TypeDesc.isRefType(v.getTypeDesc())) {
                        //    System.out.println("PUTFIELD " + ((FieldInsnNode)ain).name  + ": " + v + " ----> " + v1);
                        //}
                        break;
                        
                    case INVOKEVIRTUAL:
                    case INVOKESPECIAL:
                    case INVOKESTATIC:
                    case INVOKEINTERFACE:
                        // pop args, push return value
                        MethodInsnNode min = ((MethodInsnNode) ain);
                        String desc = min.desc;
                        if (flow.isPausableMethodInsn(min) && frame.numMonitorsActive > 0) {
                            throw new KilimException("Error: Can not call pausable nethods from within a synchronized block\n" +
                                    "Caller: " + this.flow.classFlow.name.replace('/', '.') + "." + this.flow.name + this.flow.desc +
                                    "\nCallee: " + ((MethodInsnNode)ain).name); 
                        }
                        canThrowException = true;
                        frame.popn(TypeDesc.getNumArgumentTypes(desc));
                        if (opcode != INVOKESTATIC) {
                            v = frame.pop(); // "this" ref
                            //assert checkReceiverType(v, min) : "Method " + flow.name + " calls " + min.name + " on a receiver with incompatible type " + v.getTypeDesc() ;
                        }
                        desc = TypeDesc.getReturnTypeDesc(desc);
                        if (desc != D_VOID) {
                            frame.push(Value.make(i, desc));
                        }
                        break;
                        
                    case NEW:
                        canThrowException = true;
                        v = Value.make(i, TypeDesc.getInterned(((TypeInsnNode) ain).desc));
                        frame.push(v);
                        break;
                        
                    case NEWARRAY:
                        canThrowException = true;
                        frame.popWord();
                        int atype = ((IntInsnNode) ain).operand;
                        String t;
                        switch (atype) {
                            case T_BOOLEAN:
                                t = D_ARRAY_BOOLEAN;
                                break;
                            case T_CHAR:
                                t = D_ARRAY_CHAR;
                                break;
                            case T_FLOAT:
                                t = D_ARRAY_FLOAT;
                                break;
                            case T_DOUBLE:
                                t = D_ARRAY_DOUBLE;
                                break;
                            case T_BYTE:
                                t = D_ARRAY_BYTE;
                                break;
                            case T_SHORT:
                                t = D_ARRAY_SHORT;
                                break;
                            case T_INT:
                                t = D_ARRAY_INT;
                                break;
                            case T_LONG:
                                t = D_ARRAY_LONG;
                                break;
                            default:
                                throw new InternalError("Illegal argument to NEWARRAY: "
                                        + atype);
                        }
                        frame.push(Value.make(i, t));
                        break;
                    case ANEWARRAY:
                        canThrowException = true;
                        frame.popWord();
                        componentType = TypeDesc.getInterned(((TypeInsnNode) ain).desc);
                        v = Value.make(i, TypeDesc.getInterned("[" + componentType));
                        frame.push(v);
                        break;
                        
                    case ARRAYLENGTH:
                        canThrowException = true;
                        frame.popWord();
                        frame.push(Value.make(i, D_INT));
                        break;
                        
                    case ATHROW:
                        canThrowException = true;
                        frame.pop();
                        propagateFrame = false;
                        break;
                        
                    case CHECKCAST:
                        canThrowException = true;
                        frame.pop();
                        v = Value.make(i, TypeDesc.getInterned(((TypeInsnNode) ain).desc));
                        frame.push(v);
                        break;
                        
                    case INSTANCEOF:
                        canThrowException = true;
                        frame.pop();
                        frame.push(Value.make(i, D_INT));
                        break;
                        
                    case MONITORENTER:
                    case MONITOREXIT:
                        if (opcode == MONITORENTER) {
                            frame.numMonitorsActive++;
                        } else {
                            frame.numMonitorsActive--;
                        }
                        canThrowException = true;
                        frame.pop();
                        canThrowException = true;
                        break;
                        
                    case MULTIANEWARRAY:
                        MultiANewArrayInsnNode minode = (MultiANewArrayInsnNode) ain;
                        int dims = minode.dims;
                        frame.popn(dims);
                        componentType = TypeDesc.getInterned(minode.desc);
                        StringBuffer sb = new StringBuffer(componentType.length()
                                + dims);
                        for (int j = 0; j < dims; j++)
                            sb.append('[');
                        sb.append(componentType);
                        v = Value.make(i, TypeDesc.getInterned(sb.toString()));
                        frame.push(v);
                        break;
                    default:
                        assert false : "Unexpected opcode: " + ain.getOpcode();
                }
            }
            i = -1; // reset for assertion catch block below
            if (propagateFrame) {
                mergeSuccessors(frame);
            }
            if (handlers != null) {
                for (Handler handler : handlers) {
                    handler.catchBB.merge(frame, /* localsOnly= */true); // merge
                    // only
                    // locals
                }
                canThrowException = false;
            }
        } catch (AssertionError ae) {
            System.err.println("**** Assertion Error analyzing " + flow.classFlow.name + "." + flow.name);
            System.err.println("Basic block " + this);
            System.err.println("i = " + i);
            System.err.println("Frame: " + frame);
            throw ae;
        }

    }
 /*
    private boolean checkReceiverType(Value v, MethodInsnNode min) {
        String t = v.getTypeDesc();
        if (t == D_NULL) {
            return true;
        }
        t = TypeDesc.getInternalName(t);
        return detector().getPausableStatus(t, min.name, min.desc)  != Detector.METHOD_NOT_FOUND;
    }
 */
    public boolean isCatchHandler() {
        return caughtExceptionType != null;
    }

    void mergeSuccessors(Frame frame) {
        for (BasicBlock s : successors) {
            s.merge(frame, false);
        }
    }

    /**
     * @param inframe
     * @param localsOnly
     */
    void merge(Frame inframe, boolean localsOnly) {
        boolean enqueue = true;
        if (startFrame == null) {
            startFrame = inframe.dup();
        } else {
            Frame ret;
            // Absorb only those local vars dictacted by usage.in.
            ret = startFrame.merge(inframe, localsOnly, usage);
            if (ret == startFrame) { // no change
                enqueue = false;
            } else {
                startFrame = ret;
            }
        }
        if (enqueue) {
            flow.enqueue(this);
        }
    }

    public void chooseCatchHandlers(ArrayList<Handler> handlerList) {
        for (Handler h : handlerList) {
            if (this == h.catchBB) {
                // This bb is one of the catch handlers
                caughtExceptionType = TypeDesc.getInterned((h.type == null ? THROWABLE_CLASS
                        : h.type));
            } else {
                Range ri = Range.intersect(startPos, endPos, h.from, h.to);
                if (ri != null) {
                    handlers.add(new Handler(ri.from, ri.to, h.type, h.catchBB));
                }
            }
        }
    }

    public AbstractInsnNode getInstruction(int pos) {
        return (AbstractInsnNode) flow.instructions.get(pos);
    }

    public boolean flowVarUsage() {
        // for live var analysis, treat catch handlers as successors too.
        if (succUsage == null) {
            succUsage = new ArrayList<Usage>(successors.size()
                    + handlers.size());
            for (BasicBlock succ : successors) {
                succUsage.add(succ.usage);
            }
            for (Handler h : handlers) {
                succUsage.add(h.catchBB.usage);
            }
        }
        return usage.evalLiveIn(succUsage);
    }

    /**
     * This basic block's last instruction is JSR. This method initiates a
     * subgraph traversal to identify the called subroutine's boundaries and to
     * make all encountered RET instructions point back to this BB's follower,
     * in essence turning it to a goto. The reason for not actually turning it
     * into a GOTO is that if we don't find any pausable methods in a
     * subroutine, then during code generation we'll simply use the original
     * code. The duplication is still required for flow analysis.
     * 
     * The VM spec is fuzzy on what constitutes the boundaries of a subroutine.
     * We consider the following situations invalid, even though the verifier is
     * ok with it: (a) looping back to itself (b) encountering xRETURN in a subroutine
     * 
     * inline() traverses the graph creating copies of BasicBlocks and labels
     * and keeps a mapping between the old and the new. In the second round, it
     * copies instructions translating any that have labels (branch and switch
     * instructions).
     * 
     * @return mapping of orig basic blocks to new.
     * 
     */
    ArrayList<BasicBlock> inline() throws KilimException {
        HashMap<BasicBlock, BasicBlock> bbCopyMap = null;
        HashMap<Label, Label> labelCopyMap = null;
        BasicBlock targetBB = successors.get(0);
        Label returnToLabel = flow.getOrCreateLabelAtPos(endPos+1);
        BasicBlock returnToBB = flow.getOrCreateBasicBlock(returnToLabel);
        boolean isPausableSub = targetBB.hasFlag(PAUSABLE_SUB);

        if (!targetBB.hasFlag(SUBROUTINE_CLAIMED)) {
            // This JSR call gets to claim the subroutine's blocks, so no
            // copying required. If another JSR wants to point to the same
            // subroutine, it'll copy BBs on demand)
            targetBB.setFlag(SUBROUTINE_CLAIMED);
            // Tell the RET blocks about the returnTo address and we are done.
            for (BasicBlock b : targetBB.getSubBlocks()) {
                if (b.lastInstruction() == RET) {
                    assert b.successors.size() == 0 : this.toString();
                    b.addSuccessor(returnToBB);
                }
            }
            return null;
        }
        bbCopyMap = new HashMap<BasicBlock, BasicBlock>(10);
        labelCopyMap = new HashMap<Label, Label>(10);
        successors.clear();
        // first pass
        targetBB.dupBBAndLabels(isPausableSub, bbCopyMap, labelCopyMap, returnToBB);
        addSuccessor(bbCopyMap.get(targetBB));
        // second pass
        return dupCopyContents(isPausableSub, targetBB, returnToBB, bbCopyMap, labelCopyMap);
    }

    void dupBBAndLabels(boolean deepCopy,
            HashMap<BasicBlock, BasicBlock> bbCopyMap,
            HashMap<Label, Label> labelCopyMap, BasicBlock returnToBB)
                                                                      throws KilimException {

        for (BasicBlock orig : getSubBlocks()) {
            BasicBlock dup = new BasicBlock(flow, orig.startLabel); 
            bbCopyMap.put(orig, dup);
            if (deepCopy) {
                // copy labels for each instruction. This copy will be used
                // in dupCopyContents
                for (int i = orig.startPos; i <= orig.endPos; i++) {
                    Label origLabel = flow.getLabelAt(i);
                    if (origLabel != null) {
                        Label l = labelCopyMap.put(origLabel, new Label());
                        assert l == null;
                    }
                }
                // dup.startLabel reset later in dupCopyContents
            } 
        }
    }

    @SuppressWarnings("unchecked")
    static ArrayList<BasicBlock> dupCopyContents(boolean deepCopy,
            BasicBlock targetBB, BasicBlock returnToBB,
            HashMap<BasicBlock, BasicBlock> bbCopyMap,
            HashMap<Label, Label> labelCopyMap) throws KilimException {

        ArrayList<BasicBlock> newBBs = new ArrayList<BasicBlock>(targetBB.getSubBlocks().size());
        for (BasicBlock orig : targetBB.getSubBlocks()) {
            BasicBlock dup = bbCopyMap.get(orig);
            dup.flags = orig.flags;
            dup.caughtExceptionType = orig.caughtExceptionType;
            dup.startPos = orig.startPos;
            dup.endPos = orig.endPos;
            dup.flow = orig.flow;
            dup.numPredecessors = orig.numPredecessors;
            dup.startFrame = null;
            dup.usage = orig.usage.copy();
            dup.handlers = orig.handlers;
            if (orig.follower != null) {
                dup.follower = bbCopyMap.get(orig.follower);
                if (dup.follower == null) {
                    assert dup.lastInstruction() == RET;
                }
            }
            dup.successors = new ArrayList<BasicBlock>(orig.successors.size());
            if (orig.lastInstruction() == RET) {
                dup.addSuccessor(returnToBB);
            } else {
                for (BasicBlock s : orig.successors) {
                    BasicBlock b = bbCopyMap.get(s);
                    dup.addSuccessor(b);
                }
            }

            if (deepCopy) {
                MethodFlow flow = targetBB.flow;
                List instructions = flow.instructions;
                // copy instructions
                dup.startLabel = labelCopyMap.get(orig.startLabel);
                dup.startPos = instructions.size();
                dup.endPos = dup.startPos + (orig.endPos - orig.startPos);
                // Note: last instruction (@endPos) isn't copied in the loop.
                // If it has labels, a new instruction is generated; either
                // way the last instruction is appended separately.
                int i;
                int newPos = instructions.size();
                int end = orig.endPos;

                // create new labels and instructions
                for (i = orig.startPos;  i <= end; i++, newPos++) {
                    Label l = flow.getLabelAt(i);
                    if (l != null) {
                        l = labelCopyMap.get(l);
                        assert l != null;
                        flow.setLabel(newPos, l);
                    }
                    if (i != end) {
                        // last insn gets special treatment
                        instructions.add(instructions.get(i));
                    }
                }
                
                AbstractInsnNode lastInsn = (AbstractInsnNode) instructions.get(orig.endPos);
                Label dupLabel;
                int opcode = lastInsn.getOpcode();
                if (lastInsn instanceof JumpInsnNode) {
                    JumpInsnNode jin = (JumpInsnNode) lastInsn;
                    if (lastInsn.getOpcode() != JSR) {
                        dupLabel = labelCopyMap.get(jin.label);
                        assert dupLabel != null;
                        lastInsn = new JumpInsnNode(lastInsn.getOpcode(), dupLabel);
                    }

                } else if (opcode == TABLESWITCH) {
                    TableSwitchInsnNode tsin = (TableSwitchInsnNode) lastInsn;
                    Label[] labels = new Label[tsin.labels.size()];
                    for (i = 0; i < labels.length; i++) {
                        dupLabel = labelCopyMap.get(tsin.labels.get(i));
                        assert dupLabel != null;
                        labels[i] = dupLabel;
                    }
                    dupLabel = labelCopyMap.get(tsin.dflt);
                    assert dupLabel != null;
                    lastInsn = new TableSwitchInsnNode(tsin.min, tsin.max, dupLabel, labels);
                } else if (opcode == LOOKUPSWITCH) {
                    LookupSwitchInsnNode lsin = (LookupSwitchInsnNode) lastInsn;
                    Label[] labels = new Label[lsin.labels.size()];
                    for (i = 0; i < labels.length; i++) {
                        dupLabel = labelCopyMap.get(lsin.labels.get(i));
                        assert dupLabel != null;
                        labels[i] = dupLabel;
                    }
                    dupLabel = labelCopyMap.get(lsin.dflt);
                    assert dupLabel != null;
                    int[] keys = new int[lsin.keys.size()];
                    for (i = 0; i < keys.length; i++) {
                        keys[i] = (Integer) lsin.keys.get(i);
                    }
                    lastInsn = new LookupSwitchInsnNode(dupLabel, keys, labels);
                } 
                instructions.add(lastInsn);
                // new handlers
                dup.handlers = new ArrayList<Handler>(orig.handlers.size());
                if (orig.handlers.size() > 0) {
                    for (Handler oh : orig.handlers) {
                        Handler h = new Handler(dup.startPos
                                + (oh.from - orig.startPos), dup.endPos
                                + (oh.to - orig.endPos), oh.type, oh.catchBB);
                        dup.handlers.add(h);
                    }
                }
            }
            newBBs.add(dup);
        }
        return newBBs;
    }

    public BasicBlock getJSRTarget() {
        return lastInstruction() == JSR ? successors.get(0) : null;
    }

    /*
     * Invoked on the subroutine entry point's BB. Returns all the BBs
     * linked to it.
     */
    public ArrayList<BasicBlock> getSubBlocks() throws KilimException {
        if (subBlocks == null) {
            if (!hasFlag(IS_SUBROUTINE))
                return null;
            subBlocks = new ArrayList<BasicBlock>(10);
            Stack<BasicBlock> stack = new Stack<BasicBlock>();
            this.setFlag(SUB_BLOCK);
            stack.add(this);
            while (!stack.isEmpty()) {
                BasicBlock b = stack.pop();
                subBlocks.add(b);
                if (b.lastInstruction() == JSR) {
                    // add the following block, but not its target
                    BasicBlock follower = b.getFollowingBlock();
                    if (!follower.hasFlag(SUB_BLOCK)) {
                        follower.setFlag(SUB_BLOCK);
                        stack.push(follower);
                    }
                    continue;
                }

                for (BasicBlock succ : b.successors) {
                    if (succ == this) {
                        throw new KilimException("JSRs looping back to themselves are not supported");
                    }
                    if (!succ.hasFlag(SUB_BLOCK)) {
                        succ.setFlag(SUB_BLOCK);
                        stack.push(succ);
                    }
                }
            }
            Collections.sort(subBlocks);
        }
        return subBlocks;
    }

    BasicBlock getFollowingBlock() {
        if (follower != null) return follower;
        // otherwise we'll return the next block anyway. This is used
        // to get the block following a JSR instruction, even though 
        // it is not a follower in the control flow sense.
        Label l = flow.getLabelAt(endPos+1);
        assert l != null : "No block follows this block: " + this;
        return flow.getBasicBlock(l);
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer(200);
        sb.append("\n========== BB #").append(id).append("[").append(System.identityHashCode(this)).append("]\n");
        sb.append("method: ").append(this.flow.name).append("\n");
        sb.append("start = ").append(startPos).append(",end = ").append(endPos).append('\n').append("Successors:");
        if (successors.isEmpty())
            sb.append(" None");
        else {
            for (int i = 0; i < successors.size(); i++) {
                BasicBlock succ = successors.get(i);
                sb.append(" ").append(succ.id).append("[").append(System.identityHashCode(succ)).append("]");
            }
        }
        sb.append("\nHandlers:");
        if (handlers.isEmpty())
            sb.append(" None");
        else {
            for (int i = 0; i < handlers.size(); i++) {
                sb.append(" ").append(handlers.get(i).catchBB.id);
            }
        }
        sb.append("\nStart frame:\n").append(startFrame);
        sb.append("\nUsage: ").append(usage);
        return sb.toString();
    }

    public boolean isPausable() {
        return hasFlag(PAUSABLE);
    }

    void setId(int aid) {
        id = aid;
    }

    /*
     * If any BB belonging to a subroutine makes a pausable
     * block, it taints all the blocks within the subroutine's
     * purview as PAUSABLE_SUB
     */
    void checkPausableJSR() throws KilimException {
        BasicBlock sub = getJSRTarget();
        boolean isPausableJSR = false;
        if (sub != null) {
            ArrayList<BasicBlock> subBlocks = sub.getSubBlocks();
            for (BasicBlock b: subBlocks) {
                if (b.hasFlag(PAUSABLE)) {
                    isPausableJSR = true;
                    break;
                }
            }
            if (isPausableJSR) {
                for (BasicBlock b: subBlocks) {
                    b.setFlag(PAUSABLE_SUB);
                }
            }
        }
    }

    void changeJSR_RET_toGOTOs() throws KilimException {
        int lastInsn = getInstruction(endPos).getOpcode(); 
        if (lastInsn == JSR) {
            BasicBlock targetBB = successors.get(0);
            if (!targetBB.hasFlag(PAUSABLE_SUB)) return;
            changeLastInsnToGOTO(targetBB.startLabel);
            successors.clear();
            successors.add(targetBB);

            // change the first ASTORE instruction in targetBB to a NOP
            assert targetBB.getInstruction(targetBB.startPos).getOpcode() == ASTORE;
            targetBB.setInstruction(targetBB.startPos, new NopInsn());
            targetBB.unsetFlag(IS_SUBROUTINE);
        } else if (lastInsn == RET && hasFlag(PAUSABLE_SUB)) {
            changeLastInsnToGOTO(successors.get(0).startLabel);
        }
    }

    @SuppressWarnings("unchecked")
    void setInstruction(int pos, AbstractInsnNode insn) {
        flow.instructions.set(pos, insn);
    }

    void changeLastInsnToGOTO(Label label) {
        setInstruction(endPos, new JumpInsnNode(GOTO, label));
    }

    public boolean isGetCurrentTask() {
        AbstractInsnNode ain = getInstruction(startPos);
        if (ain.getOpcode() == INVOKESTATIC) {
            MethodInsnNode min = (MethodInsnNode)ain;
            return min.owner.equals(TASK_CLASS) && min.name.equals("getCurrentTask");
        }
        return false;
    }

    boolean isInitialized() {
        return startPos >= 0 && endPos >=0; 
    }
}

class BBComparator implements Comparator<BasicBlock> {
    public int compare(BasicBlock o1, BasicBlock o2) {
        if (o1.id == o2.id) {
            return 0;
        }
        return o1.id < o2.id ? -1 : +1;
    }
}
