/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim.analysis;
import static kilim.Constants.ALOAD_0;
import static kilim.Constants.ASTORE_0;
import static kilim.Constants.DLOAD_0;
import static kilim.Constants.DSTORE_0;
import static kilim.Constants.D_BOOLEAN;
import static kilim.Constants.D_BYTE;
import static kilim.Constants.D_CHAR;
import static kilim.Constants.D_DOUBLE;
import static kilim.Constants.D_FIBER;
import static kilim.Constants.D_FLOAT;
import static kilim.Constants.D_INT;
import static kilim.Constants.D_LONG;
import static kilim.Constants.D_NULL;
import static kilim.Constants.D_OBJECT;
import static kilim.Constants.D_SHORT;
import static kilim.Constants.D_STATE;
import static kilim.Constants.D_VOID;
import static kilim.Constants.D_UNDEFINED;
import static kilim.Constants.FIBER_CLASS;
import static kilim.Constants.FLOAD_0;
import static kilim.Constants.FSTORE_0;
import static kilim.Constants.ILOAD_0;
import static kilim.Constants.ISTORE_0;
import static kilim.Constants.LLOAD_0;
import static kilim.Constants.LSTORE_0;
import static kilim.Constants.STATE_CLASS;
import static kilim.analysis.VMType.TOBJECT;
import static kilim.analysis.VMType.loadVar;
import static kilim.analysis.VMType.storeVar;
import static kilim.analysis.VMType.toVmType;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.DCONST_1;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.DSTORE;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.FCONST_1;
import static org.objectweb.asm.Opcodes.FCONST_2;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.FSTORE;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.I2B;
import static org.objectweb.asm.Opcodes.I2C;
import static org.objectweb.asm.Opcodes.I2S;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_M1;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.LCONST_1;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.LSTORE;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.SIPUSH;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * This class produces all the code associated with a specific pausable method
 * invocation. There are three distinct chunks of code.
 * <dl>
 * <li> <b>Rewind</b>: At the beginning of the method, right after the opening
 * switch statement (switch fiber.pc), which is the "rewind" portion. This stage
 * pushes in (mostly) dummy values on the stack and jumps to the next method
 * invocation in the cycle. </li>
 * 
 * <li> <b>Call</b>: The actual call. We push fiber as the last argument to the
 * pausable method (by calling fiber.down()), before making the call. </li>
 * 
 * <li> <b>Post-call</b> The bulk of the code produced by this object. </li>
 * </ol>
 * <p>
 * 
 * An explanation of some terms used in the code may be useful.
 * 
 * Much of this code concerns itself with storing and retrieving values from/to
 * the stack and the local variables. The stack consists of three logical parts:<br>
 * 
 * <pre>
 *   +--------------+----------------------+---------------+
 *   | Stack bottom | callee obj reference | args for call | (Top of stack)
 *   +--------------+----------------------+---------------+
 * </pre>
 * 
 * The callee's object reference and the arguments at the top of stack are
 * consumed by the method invocation. If the call is static, there is no object
 * reference, of course. The bottom of the stack (which may also be empty)
 * refers to the part of the stack that is left over once the args are consumed.
 * This is the part that we need to save if we have to yield.
 * <p>
 * 
 * As for the local variables, we are interested in saving var 0 (the "this"
 * pointer) and all other variables that the flow analysis has shown to be
 * live-in, that is, is used downstream of the call. Typically, only about 30%
 * of all available vars are actually used downstream, so we use the rest for
 * temporary storage.
 * <p>
 * 
 * @see #genRewind(MethodVisitor)
 * @see #genCall(MethodVisitor)
 * @see #genPostCall(MethodVisitor)

 * 
 */

public class CallWeaver {
    /**
     * The parent method-weaver responsible for writing the whole method
     */
    private MethodWeaver methodWeaver;

    /**
     * The basic block that calls the pausable method
     */
    BasicBlock           bb;

    private Label        resumeLabel;

    Label                callLabel;

    private ValInfoList  valInfoList;

    /**
     * varUsage[i] is true if the i-th var is taken. We don't touch the first
     * maxLocals vars. It is used for minimizing usage of extra vars.
     */
    BitSet               varUsage;

    /**
     * number of local registers required. 
     */
    int                  numVars;

    /** The canoncial name of the state class responsible for storing
     * the state (@see kilim.State)
     */
    private String       stateClassName;

    /** Memoized version of getNumArgs() */
    int                  numArgs = -1;

    public CallWeaver(MethodWeaver mw, BasicBlock aBB) {
        methodWeaver = mw;
        bb = aBB;
        callLabel = bb.startLabel;
        varUsage = new BitSet(2 * bb.flow.maxLocals);
        resumeLabel = bb.flow.getLabelAt(bb.startPos + 1);
        if (resumeLabel == null)
            resumeLabel = new Label();
        assignRegisters();
        stateClassName = createStateClass();
        methodWeaver.ensureMaxStack(getNumBottom() + 3); // 
    }

    /**
     * The basic block's frame tells us the number of parameters in the stack
     * and which local variables are needed later on.
     * 
     * If the method is pausable, we'll need the bottom part of the stack and
     * the object reference from the top part of the stack to be able to resume
     * it. We don't need to worry about the arguments, because they will be
     * saved (if needed) in the _called_ method's state.
     * 
     * The "this" arg (var0) is given special treatment. It is always saved in
     * all states, so it doesn't count as "data".
     */
    private void assignRegisters() {
        Frame f = bb.startFrame;
        MethodWeaver mw = methodWeaver;
        varUsage.set(mw.getFiberVar());
        numVars = mw.getFiberVar() + 1; // knowing fiberVar is beyond anything
                                        // that's used
        mw.ensureMaxVars(numVars);
        Usage u = bb.usage;
        valInfoList = new ValInfoList();

        /*
         * Create ValInfos for each Value that needs to be saved (all live-in
         * vars (except var 0, if not static) and all stack bottom vars count,
         * except if they are duplicates of earlier ones or are constants which
         * can be reproduced in bytecode itself.
         * 
         * Process local vars before the stack, so that we can figure out which
         * elements of the stack are duplicates. Saving the stack requires more
         * processing, and the more we can reduce it, the better.
         */

        // ensure we don't touch any of the locals in the range that the
        // original
        // code knows about.
        varUsage.set(0, f.getMaxLocals());

        int i = bb.flow.isStatic() ? 0 : 1;
        for (; i < f.getMaxLocals(); i++) {
            Value v = f.getLocal(i);
            if (u.isLiveIn(i)) {
                if (!(v.isConstant() || valInfoList.contains(v))) {
                    ValInfo vi = new ValInfo(v);
                    vi.var = i;
                    valInfoList.add(vi);
                }
            }
        }

        /*
         * All stack values at the bottom (those not consumed by the called
         * method) will have to be saved, if they are are not already accounted
         * for or they are constants.
         */
        int numBottom = getNumBottom();
        for (i = 0; i < numBottom; i++) {
            Value v = f.getStack(i);
            if (!(v.isConstant() || valInfoList.contains(v))) {
                ValInfo vi = new ValInfo(v);
                valInfoList.add(vi);
            }
        }
        Collections.sort(valInfoList); // sorts by type and var
        int fieldNum = 0;
        for (ValInfo vi : valInfoList) {
            vi.fieldName = "f" + fieldNum++;
        }
    }

    int getStackLen() {
        return bb.startFrame.getStackLen();
    }

    /**
     * The total number consumed by the call, including its object reference
     */
    int getNumArgs() {
        if (numArgs == -1) {
            numArgs = TypeDesc.getNumArgumentTypes(getMethodInsn().desc)
                    + (isStaticCall() ? 0 : 1);
        }
        return numArgs;
    }

    final boolean isStaticCall() {
        return getMethodInsn().getOpcode() == INVOKESTATIC;
    }

    final MethodInsnNode getMethodInsn() {
        return (MethodInsnNode) bb.getInstruction(bb.startPos);
    }

    int getNumBottom() {
        return getStackLen() - getNumArgs();
    }

    /**
     * <pre>
     * The following template is produced in the method's prelude for each
     * pausable method. 
     * F_REWIND: 
     *   for each bottom stack operand
     *      introduce a dummy constant of the appropriate type.
     *         (iconst_0, aconst_null, etc.)
     *      if the call is not static, 
     *         we need the called object's object reference
     *         ask the next state in the fiber's list
     *   goto F_CALL: // jump to the invocation site.
     * </pre>
     * 
     * @param mv
     */
    void genRewind(MethodVisitor mv) {
        Frame f = bb.startFrame;
        
        // The last parameter to the method is fiber, but the original
        // code doesn't know that and will use up that slot as it
        // pleases. If we are going to jump directly to the basicblock
        // bb, we'll have to make sure it has some dummy value of that
        // type going in just to keep the verifier happy. 

        for (int i = methodWeaver.getFiberArgVar(); i < f.getMaxLocals(); ) {
            Value v = f.getLocal(i);
            if (v.getTypeDesc() != D_UNDEFINED) {
//            if (u.isLiveIn(i)) {
                int vmt = toVmType(v.getTypeDesc());
                mv.visitInsn(VMType.constInsn[vmt]);
                storeVar(mv, vmt, i);
            }
            i += v.isCategory2() ? 2 : 1;
        }

        // store dummy values in stack. The constants have to correspond
        // to the types of each of the bottom elements.
        int numBottom = getNumBottom();

        // spos == stack pos. Note that it is incremented beyond the
        // 'for' loop below.
        int spos;
        for (spos = 0; spos < numBottom; spos++) {
            Value v = f.getStack(spos);
            if (v.isConstant()) {
                mv.visitInsn(VMType.constInsn[VMType.toVmType(v.getTypeDesc())]);
            } else {
                ValInfo vi = valInfoList.find(v);
                mv.visitInsn(VMType.constInsn[vi.vmt]);
            }
        }
        if (!isStaticCall()) {
            // The next element in the stack after numBottom is the object
            // reference for the callee. This can't be a dummy because it 
            // is needed by the invokevirtual call. If this reference
            // is found in the local vars, we'll use it, otherwise we need
            // to dip into the next activation frame in the fiber to
            // retrieve it.
            Value v = f.getStack(numBottom);
            if (!methodWeaver.isStatic() && f.getLocal(0) == v) {
                // "this" is already properly initialized.
                mv.visitInsn(ALOAD_0);
            } else {
                loadVar(mv, TOBJECT, methodWeaver.getFiberVar());
                mv.visitMethodInsn(INVOKEVIRTUAL, FIBER_CLASS, "getCallee", "()Ljava/lang/Object;");
                mv.visitTypeInsn(CHECKCAST, getReceiverTypename());
            }
            spos++;
        }

        int len = f.getStackLen();
        // fill rest of stack with dummy args
        for (; spos < len; spos++) {
            Value v = f.getStack(spos);
            int vmt = VMType.toVmType(v.getTypeDesc());
            mv.visitInsn(VMType.constInsn[vmt]);
        }

        mv.visitJumpInsn(GOTO, callLabel);
    }

    /**
     * Before we make the target call, we need to call fiber.down(), to update
     * it on the depth of the stack. genPostCall subsequently arranges to call
     * fiber.up(). We also need to push the fiber as the last argument to the
     * pausable method. We accomplish both of these objectives by having
     * fiber.down() do its book-keeping and return fiber, which is left on the
     * stack before the call is made.
     * 
     * <pre>
     *             
     *            F_CALL: 
     *               push fiber.down()
     *               invoke[virtual|static] classname/method modifiedDesc 
     * </pre>
     * 
     * @param mv
     */
    static String fiberArg = D_FIBER + ')';
    void genCall(MethodVisitor mv) {
        mv.visitLabel(callLabel);
        loadVar(mv, TOBJECT, methodWeaver.getFiberVar());
        mv.visitMethodInsn(INVOKEVIRTUAL, FIBER_CLASS, "down", "()" + D_FIBER);
        MethodInsnNode mi = getMethodInsn();
        if (mi.desc.indexOf(fiberArg) == -1) {
            // Don't add another fiberarg if it already has one. It'll already
            // have one if we have copied jsr instructions and modified the 
            // same instruction node earlier. 
            mi.desc = mi.desc.replace(")", fiberArg);
        }
        mi.accept(mv);
    }
    
    /**
     * After the pausable method call is over, we have four possibilities. The
     * called method yielded, or it returned normally. Orthogonally, we have
     * saved state or not. fiber.up() returns the combined status
     * 
?     * <pre>
     *                     
     * switch (fiber.up()) {
     *    default:
     *    0: goto RESUME; // Not yielding , no State --  resume normal operations
     *    1: goto RESTORE; // Not yielding, has State -- restore state before resuming
     *    2: goto SAVE; // Yielding, no state -- save state before unwinding stack
     *    3: goto UNWIND // Yielding, has state -- nothing to do but unwind.
     * }
     * SAVE:
     *     ...
     *     xRETURN
     * UNWIND:
     *     ...
     *     xRETURN
     * RESTORE:
     *     ...
     *     // fall through to RESUME
     * RESUME:
     *     ... original code
     * </pre>
     * 
     * @param mv
     * 
     */
    void genPostCall(MethodVisitor mv) {
        loadVar(mv, TOBJECT, methodWeaver.getFiberVar());
        mv.visitMethodInsn(INVOKEVIRTUAL, FIBER_CLASS, "up", "()I");
        Label restoreLabel = new Label();
        Label saveLabel = new Label();
        Label unwindLabel = new Label();
        Label[] labels = new Label[] { resumeLabel, restoreLabel, saveLabel,
                unwindLabel };
        mv.visitTableSwitchInsn(0, 3, resumeLabel, labels);
        genSave(mv, saveLabel);
        genUnwind(mv, unwindLabel);
        genRestore(mv, restoreLabel);
        mv.visitLabel(resumeLabel);
    }

    /**
     * Code for the case where we are yielding, and we have state built up from
     * a previous call. There's nothing meaningful to do except keep the
     * verifier happy. Pop the bottom stack, then return a dummy return value
     * (if this method -- note: not the called method -- returns a value)
     * 
     * @param mv
     */
    private void genUnwind(MethodVisitor mv, Label unwindLabel) {
        mv.visitLabel(unwindLabel);
        // After the call returns, the stack would be left with numBottom plus
        // return value

        // pop callee's dummy return value first
        String rdesc = getReturnType();
        if (rdesc != D_VOID) {
            mv.visitInsn(TypeDesc.isDoubleWord(rdesc) ? POP2 : POP);
        }
        // pop numbottom
        int i = getNumBottom() - 1; // the numBottom'th element
        Frame f = bb.startFrame;
        for (; i >= 0; i--) {
            mv.visitInsn(f.getStack(i).isCategory1() ? POP : POP2);
        }

        // Now for the return value of this (the calling) method
        rdesc = TypeDesc.getReturnTypeDesc(bb.flow.desc);
        if (rdesc != D_VOID) {
            // ICONST_0; IRETURN or ACONST_NULL; ARETURN etc.
            int vmt = VMType.toVmType(rdesc);
            mv.visitInsn(VMType.constInsn[vmt]);
            mv.visitInsn(VMType.retInsn[vmt]);
        } else {
            mv.visitInsn(RETURN);
        }
    }

    private String getReturnType() {
        return TypeDesc.getReturnTypeDesc(getMethodInsn().desc);
    }

    /**
     * Yielding, but state hasn't been captured yet. We create a state object
     * and save each object in valInfoList in its corresponding field.
     * 
     * Note that we save each stack item into a scratch register before loading
     * it into a field. The reason is that we need to get the State ref under
     * the stack item before we can do a putfield. The alternative is to load
     * the State item, then do a swap or a dup_x2;pop (depending on the value's
     * category). We'll go with the earlier approach because stack manipulations
     * don't seem to perform as well in the current crop of JVMs.
     * 
     * @param mv
     */
    private void genSave(MethodVisitor mv, Label saveLabel) {
        mv.visitLabel(saveLabel);

        Frame f = bb.startFrame;
        // pop return value if any.
        String retType = getReturnType(); 
        if (retType != D_VOID) {
            // Yielding, so the return value from the called
            // function is a dummy value
            mv.visitInsn(TypeDesc.isDoubleWord(retType) ? POP2 : POP);
        }
        /*
         * Instantiate state class. Call one of new xxxState(this, pc, fiber),
         * or new xxxState(pc, fiber) depending whether this method is static or
         * not. Note that are not referring to the called method.
         * 
         * pc, "the program counter" is merely the index of the call weaver in
         * the method weaver's list. This allows us to do a switch in the
         * method's entry.
         */
        mv.visitTypeInsn(NEW, stateClassName);
        mv.visitInsn(DUP); // 
        // call constructor
        mv.visitMethodInsn(INVOKESPECIAL, stateClassName, "<init>", "()V");
        // save state in register
        int stateVar = allocVar(1);
        storeVar(mv, TOBJECT, stateVar);
        // state.self = this if the current executing method isn't static
        if (!bb.flow.isStatic()) {
            loadVar(mv, TOBJECT, stateVar);
            mv.visitInsn(ALOAD_0); // for state.self == this
            mv.visitFieldInsn(PUTFIELD, STATE_CLASS, "self", D_OBJECT);
        }
        int pc = methodWeaver.getPC(this);
        loadVar(mv, TOBJECT, stateVar); // state.pc
        if (pc < 6) {
            mv.visitInsn(ICONST_0 + pc);
        } else {
            mv.visitIntInsn(BIPUSH, pc);
        }
        mv.visitFieldInsn(PUTFIELD, STATE_CLASS, "pc", D_INT);

        // First save bottom stack into state
        int i = getNumBottom() - 1;
        for (; i >= 0; i--) {
            Value v = f.getStack(i);
            ValInfo vi = valInfoList.find(v);
            if (vi == null) {
                // it must be a constant or a duplicate, which is why we don't
                // have any information on it. just pop it.
                mv.visitInsn(v.category() == 2 ? POP2 : POP);
            } else {
                /**
                 * xstore <scratchVar> ; send stack elem to some local var 
                 * aload <stateVar>    ; load state 
                 * xload <scratchVar>  ; get stack elem back 
                 * putfield ...        ; state.fx =
                 */
                int var = allocVar(vi.val.category());
                storeVar(mv, vi.vmt, var);
                loadVar(mv, VMType.TOBJECT, stateVar);
                loadVar(mv, vi.vmt, var);
                mv.visitFieldInsn(PUTFIELD, stateClassName, vi.fieldName, vi.fieldDesc());
                releaseVar(var, vi.val.category());
            }
        }

        // Now load up registers into fields
        int fieldNum = 0;
        for (ValInfo vi : valInfoList) {
            // Ignore values on stack
            if (vi.var == -1)
                continue;
            // aload state var
            // xload <var>
            loadVar(mv, TOBJECT, stateVar);
            loadVar(mv, vi.vmt, vi.var);
            mv.visitFieldInsn(PUTFIELD, stateClassName, vi.fieldName, vi.fieldDesc());
            fieldNum++;
        }

        // Fiber.setState(state);
        loadVar(mv, TOBJECT, methodWeaver.getFiberVar());
        loadVar(mv, TOBJECT, stateVar);
        mv.visitMethodInsn(INVOKEVIRTUAL, FIBER_CLASS, "setState", "("
                + D_STATE + ")V");
        releaseVar(stateVar, 1);
        // Figure out the return type of the calling method and issue the
        // appropriate xRETURN instruction
        retType = TypeDesc.getReturnTypeDesc(bb.flow.desc);
        if (retType == D_VOID) {
            mv.visitInsn(RETURN);
        } else {
            int vmt = VMType.toVmType(retType);
            // ICONST_0, DCONST_0 etc.
            mv.visitInsn(VMType.constInsn[vmt]);
            // IRETURN, DRETURN, etc.
            mv.visitInsn(VMType.retInsn[vmt]);
        }
    }

    /**
     * Not yielding (resuming normally), but have stored state. We need to
     * restore from state before resuming. This is slightly more work than
     * saving state, because we have to restore constants and duplicates too.
     * <b>
     * 
     * Note that the return value (if any) has a real value, which is why it
     * needs to be saved away before we can get access to the bottom elements to
     * pop them out.
     * 
     * <pre>
     *           If there is anything at the bottom save return value in scratch register
     *                 pop unnecessary bottom stuff.
     *          
     *           load fiber.curState 
     *           cast to specific state (if necessary) 
     *           astore in scratch &lt;stateVar&gt;
     *          
     *          for each value in frame.var 
     *               if val is constant or is in valInfoList, 
     *                   push constant or load field (appropriately) 
     *          for each value in bottom stack
     *                 restore value similar to above 
     *          restore return value if any from scratch register
     * </pre>
     */
    private void genRestore(MethodVisitor mv, Label restoreLabel) {
        mv.visitLabel(restoreLabel);
        Frame f = bb.startFrame;
        int numBottom = getNumBottom();
        int retVar = -1;
        int retctype = -1;
        if (numBottom > 0) {
            // We have dummy values sitting in the stack. pop 'em.
            // But first, check if we have a real return value on top
            String retType = getReturnType();
            if (retType != D_VOID) {
                // .. yep. save it to scratch register
                retctype = VMType.toVmType(retType);
                retVar = allocVar(VMType.category[retctype]);
                storeVar(mv, retctype, retVar);
            }
            // pop dummy values from stack bottom
            for (int i = numBottom-1; i >= 0; i--) {
                Value v = f.getStack(i);
                int insn = v.isCategory1() ? POP : POP2;
                mv.visitInsn(insn);
            }
        }
        // Restore variables from state
        int stateVar = -1;
        if (valInfoList.size() > 0) {
            stateVar = allocVar(1);
        }
        genRestoreVars(mv, stateVar);

        // Now restore the bottom values in the stack from state
        for (int i = 0; i < numBottom; i++) {
            Value v = f.getStack(i);
            if (v.isConstant()) {
                loadConstant(mv, v);
            } else {
                ValInfo vi = valInfoList.find(v);
                if (vi.var == -1) {
                    loadVar(mv, TOBJECT, stateVar);
                    mv.visitFieldInsn(GETFIELD, stateClassName, vi.fieldName, vi.fieldDesc());
                    checkcast(mv, v);
                } else {
                    // this stack value is a duplicate of a local var, which has
                    // already been loaded and is of the right type
                    loadVar(mv, vi.vmt, vi.var);
                }
            }
        }

        // restore the saved return value, if any
        // But we would have popped and saved the return value only
        // if there were any dummy values in the stack bottom to be
        // cleared out. If not, we wouldn't have bothered
        // popping the return value in the first place.
        if (numBottom > 0) {
            if (retVar != -1) {
                loadVar(mv, retctype, retVar);
            }
        }
        releaseVar(stateVar, 1);
        if (retctype != -1) {
            releaseVar(retVar, VMType.category[retctype]);
        }
        // Fall through to the resumeLabel in genNY_NS, so no goto required.
    }

    void genRestoreEx(MethodVisitor mv, Label restoreLabel) {
        mv.visitLabel(restoreLabel);
        int stateVar = -1;
        if (valInfoList.size() > 0) {
            stateVar = allocVar(1);
        }
        genRestoreVars(mv, stateVar);
        releaseVar(stateVar, 1);
    }

    /*
     */
    private void genRestoreVars(MethodVisitor mv, int stateVar) {
        Frame f = bb.startFrame;

        if (valInfoList.size() > 0) {
            // need to have state in a local variable
            loadVar(mv, TOBJECT, methodWeaver.getFiberVar());
            mv.visitFieldInsn(GETFIELD, FIBER_CLASS, "curState", D_STATE);
            if (!stateClassName.equals(STATE_CLASS)) {
                mv.visitTypeInsn(CHECKCAST, stateClassName);
            }
            storeVar(mv, TOBJECT, stateVar);
        }

        // no need to restore "this" if it's already there.
        Usage u = bb.usage;
        int len = f.getMaxLocals();
        int i = bb.flow.isStatic() ? 0 : 1;
        for (; i < len; i++) {
            if (!u.isLiveIn(i))
                continue;
            Value v = f.getLocal(i);
            int vmt = VMType.toVmType(v.getTypeDesc());
            if (v.isConstant()) {
                loadConstant(mv, v);
            } else {
                ValInfo vi = valInfoList.find(v);
                if (vi.var == i) {
                    // load val from state
                    loadVar(mv, TOBJECT, stateVar);
                    mv.visitFieldInsn(GETFIELD, stateClassName, vi.fieldName, vi.fieldDesc());
                    checkcast(mv, v); // don't need to do this in the constant case
                } else {
                    // It is a duplicate of another var. No need to load this var from stack
                    assert vi.var < i;
                    loadVar(mv, vi.vmt, vi.var);
                }
            }
            // Convert types from vm types to value's real type, if necessary
            // store into local
            storeVar(mv, vmt, i);
        }
        releaseVar(stateVar, 1);
    }

    private String getReceiverTypename() {
        MethodInsnNode min = getMethodInsn();
        return min.owner;
    }

    /**
     * We have loaded a value of one of the five VM types into the stack and we
     * need to cast it to the value's type, if necessary
     * 
     * @param mv
     * @param v
     */
    private void checkcast(MethodVisitor mv, Value v) {
        String valType = v.getTypeDesc();
        int vmt = VMType.toVmType(valType);
        switch (vmt) {
            case VMType.TOBJECT:
                if (valType == D_OBJECT || valType == D_NULL) {
                    return;
                }
                mv.visitTypeInsn(CHECKCAST, TypeDesc.getInternalName(valType));
                break;
            case VMType.TINT:
                if (valType == D_INT)
                    return;
                int insn = 0;
                if (valType == D_SHORT)
                    insn = I2S;
                else if (valType == D_BYTE)
                    insn = I2B;
                else if (valType == D_CHAR)
                    insn = I2C;
                else
                    assert valType == D_BOOLEAN;
                mv.visitInsn(insn);
                break;
            default:
                break;
        }
    }

    private void loadConstant(MethodVisitor mv, Value v) {
        if (v.getTypeDesc() == D_NULL) {
            mv.visitInsn(ACONST_NULL);
            return;
        }
        Object c = v.getConstVal();
        if (c instanceof Integer) {
            int i = (Integer) c;
            if (i > -1 && i <= 5) {
                mv.visitInsn(i + 1 + ICONST_M1);
                return;
            } else if (i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) {
                mv.visitIntInsn(BIPUSH, i);
                return;
            } else if (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) {
                mv.visitIntInsn(SIPUSH, i);
                return;
            }
        } else if (c instanceof Float) {
            Float f = ((Float) c).floatValue();
            int insn = 0;
            if (f == 0.0)
                insn = FCONST_0;
            else if (f == 1.0)
                insn = FCONST_1;
            else if (f == 2.0)
                insn = FCONST_2;
            if (insn != 0) {
                mv.visitInsn(insn);
                return;
            }
        } else if (c instanceof Long) {
            Long l = ((Long) c).longValue();
            int insn = 0;
            if (l == 0L)
                insn = LCONST_0;
            else if (l == 1L)
                insn = LCONST_1;
            if (insn != 0) {
                mv.visitInsn(insn);
                return;
            }
        } else if (c instanceof Double) {
            Double d = ((Double) c).doubleValue();
            int insn = 0;
            if (d == 0.0)
                insn = DCONST_0;
            else if (d == 1.0)
                insn = DCONST_1;
            if (insn != 0) {
                mv.visitInsn(insn);
                return;
            }
        }
        // No special constants found.
        mv.visitLdcInsn(c);
    }

    private String createStateClass() {
        return valInfoList.size() == 0 ? STATE_CLASS :  
            methodWeaver.createStateClass(valInfoList);
    }

    private int allocVar(int size) {
        int var;
        for (var = 0;; var++) {
            // if the var'th local is not set (if double, check the next word
            // too)
            if (!varUsage.get(var)) {
                if (size == 1 || !varUsage.get(var + 1)) {
                    break;
                }
            }
        }
        varUsage.set(var);
        if (size == 2) {
            varUsage.set(var + 1);
            methodWeaver.ensureMaxVars(var + 2); // var is 0-based
        } else {
            methodWeaver.ensureMaxVars(var + 1);
        }
        return var;
    }

    private void releaseVar(int var, int size) {
        if (var == -1)
            return;
        varUsage.clear(var);
        if (size == 2) {
            varUsage.clear(var + 1);
        }
    }

    BasicBlock getBasicBlock() {
        return bb;
    }
}

class ValInfo implements Comparable<ValInfo> {
    /**
     * The var to which the value belongs. It remains undefined if it is a stack
     * item.
     */
    int    var = -1;

    /**
     * The value to hold. This gives us information about the type, whether the
     * value is duplicated and whether it is a constant value.
     */
    Value  val;

    /**
     * The type of value boiled down to one of the canonical types.
     */
    int    vmt;

    /**
     * Names of the fields in the state var: "f0", "f1", etc, according to their
     * position in the call weaver's valInfoList.
     */
    String fieldName;

    ValInfo(Value v) {
        val = v;
        vmt = VMType.toVmType(v.getTypeDesc());
    }

    String fieldDesc() {
        return VMType.fieldDesc[vmt];
    }

    public int compareTo(ValInfo that) {
        if (this == that)
            return 0;
        if (this.vmt < that.vmt)
            return -1;
        if (this.vmt > that.vmt)
            return 1;
        if (this.var < that.var)
            return -1;
        if (this.var > that.var)
            return 1;
        return 0;
    }
}

class ValInfoList extends ArrayList<ValInfo> {
    private static final long serialVersionUID = -2339264992519046024L;

    public ValInfo find(Value v) {
        int i = indexOf(v);
        return (i == -1) ? null : get(i);
    }

    public int indexOf(Value v) {
        int len = size();
        for (int i = 0; i < len; i++) {
            if (get(i).val == v)
                return i;
        }
        return -1;
    }

    public boolean contains(Value v) {
        return indexOf(v) != -1;
    }

}

class VMType {

    static final int      TOBJECT   = 0;

    static final int      TINT      = 1;

    static final int      TLONG     = 2;

    static final int      TDOUBLE   = 3;

    static final int      TFLOAT    = 4;

    static final int[]    constInsn = { ACONST_NULL, ICONST_0, LCONST_0,
            DCONST_0, FCONST_0     };

    static final int[]    loadInsn  = { ALOAD, ILOAD, LLOAD, DLOAD, FLOAD };

    static final int[]    retInsn   = { ARETURN, IRETURN, LRETURN, DRETURN,
            FRETURN                };


    static final int[]    ldInsn    = { ALOAD_0, ILOAD_0, LLOAD_0, DLOAD_0,
            FLOAD_0                };

    static final int[]    stInsn    = { ASTORE_0, ISTORE_0, LSTORE_0, DSTORE_0,
            FSTORE_0               };

    static final int[]    storeInsn = { ASTORE, ISTORE, LSTORE, DSTORE, FSTORE };

    static final String[] fieldDesc = { D_OBJECT, D_INT, D_LONG, D_DOUBLE,
            D_FLOAT                };

    static final String[] abbrev    = { "O", "I", "L", "D", "F" };

    static final int[]    category  = { 1, 1, 2, 2, 1 };

    static int toVmType(String type) {
        switch (type.charAt(0)) {
            case 'Z':
            case 'B':
            case 'C':
            case 'S':
            case 'I':
                return TINT;

            case 'D':
                return TDOUBLE;
            case 'F':
                return TFLOAT;
            case 'J':
                return TLONG;

            case 'N': // null
            case 'A': // catch handler return address 
            case 'L': // normal type
            case '[': // array
                return TOBJECT;

            default:
                assert false : "Type " + type + " not handled";
        }
        return ' ';
    }

    static void loadVar(MethodVisitor mv, int vmt, int var) {
        assert var >= 0 : "Got var = " + var;
        if (var < 4) {
            // short instructions like ALOAD_n exist for n = 0 .. 4
            mv.visitInsn(ldInsn[vmt] + var);
        } else {
            mv.visitVarInsn(loadInsn[vmt], var);
        }
    }

    static void storeVar(MethodVisitor mv, int vmt, int var) {
        assert var >= 0;
        if (var < 4) {
            // short instructions like ALOAD_n exist for n = 0 .. 4
            mv.visitInsn(stInsn[vmt] + var);
        } else {
            mv.visitVarInsn(storeInsn[vmt], var);
        }
    }
}