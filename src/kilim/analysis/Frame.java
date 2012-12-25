/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.analysis;
import static kilim.Constants.D_DOUBLE;
import static kilim.Constants.D_FLOAT;
import static kilim.Constants.D_LONG;
import static kilim.Constants.D_OBJECT;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.LLOAD;

import org.objectweb.asm.tree.MethodNode;


/**
 * An activation frame.

 *
 */
public class Frame {
    Value[] locals;
    Value[] stack;
    int numMonitorsActive = 0;
    int stacklen = 0;
    
    private Frame(int nLocals, int nStack, boolean init) {
        this.locals = new Value[nLocals];
        if (init) {
            for (int i = 0; i < nLocals; i++) {
                locals[i] = Value.V_UNDEFINED;
            }
        }
        this.stack = new Value[nStack];
    }
    
    public Frame(int nLocals, int nStack) {
        this(nLocals, nStack, true);
    }
    
    /**
     * Merge the local variables and stack from the incoming frame 
     * into the current frame. 
     * @param inframe -- incoming frame
     * @param localsOnly -- true for exception handlers, because the
     *      stack is cleared.
     * @param usage -- Only those locals are merged that are deemed
     *        live (@see Usage#isLiveIn(int)) 
     * @return this, if the merge didn't change anything
     *      or a new Frame if the operation changed a slot on the stack
     *      or a local variable
     */
    public Frame merge(Frame inframe, boolean localsOnly, Usage usage) {
        int slen = stacklen;

        Value[] nst = null; // new stack. allocated if needed
        
        if (!localsOnly) {
            Value[] st = stack;
            Value[] ist = inframe.stack;
            for (int i = 0; i < slen; i++) {
                Value va = st[i];
                Value vb = ist[i];
                if (va == vb || va.equals(vb)) continue;
                Value newval = va.merge(vb);
                if (newval != va) {
                    if (nst == null) nst = dupArray(st);
                    nst[i] = newval;
                }
            }
        }
        
        Value[] lo = locals;
        Value[] ilo = inframe.locals; 
        Value[] nlo = null; // new locals array. allocated if needed
        for (int i = 0; i < lo.length; i++) {
            if (!usage.isLiveIn(i)) continue;
            Value va = lo[i];
            Value vb = ilo[i];
            if (va == vb || va.equals(vb)) continue;
            Value newval = va.merge(vb);
            if (newval != va) {
                if (nlo == null) nlo = dupArray(lo);
                nlo[i] = newval;
            }
        }
        if (nst == null && nlo == null) {
            return this;
        } else {
            // One or both of locals and stacks have new values
            if (nst == null) nst = dupArray(stack);
            if (nlo == null) nlo = dupArray(locals);
            return new Frame(nlo, nst, slen, numMonitorsActive);
        }
    }
    
    public static Value[] dupArray(Value[] a) {
        Value[] ret = new Value[a.length];
        System.arraycopy(a, 0, ret, 0, a.length);
        return ret;
    }
    
    private Frame(Value[] alocals, Value[] astack, int astacklen, int aNumMonitorsActive) {
        this.locals = alocals;
        this.stack = astack;
        this.stacklen = astacklen;
        this.numMonitorsActive = aNumMonitorsActive;
    }
    
    public Frame dup() {
        return new Frame(dupArray(locals), dupArray(stack), stacklen, numMonitorsActive); 
    }
    
    public Frame(String classDesc, MethodNode method) {
        this(method.maxLocals, method.maxStack, false);
        String[] argTypeDescs = TypeDesc.getArgumentTypes(method.desc);
        for (int i = 0; i < method.maxLocals; i++) {
            setLocal(i, Value.V_UNDEFINED);
        }
        int local = 0;
        int paramPos = 100000;
        if ((method.access & ACC_STATIC) == 0) {
            // 0th local is "this"
            setLocal(local++, Value.make(paramPos++,classDesc));
        }
        for (int i = 0; i < argTypeDescs.length; i++) {
            local += setLocal(local, Value.make(paramPos++, argTypeDescs[i]));
        }
        if ((method.access & ACC_SYNCHRONIZED) != 0) {
            numMonitorsActive = 1;
        }
    }
    
    private boolean checkType(String desc) {
        if (desc.equals("Ljava/lang/Object;") && desc != D_OBJECT) return false;
        switch(desc.charAt(0)) {
            case 'L': case 'B': case 'C': case 'D': case 'F': case 'I':
            case 'J': case 'S': case 'Z': case 'N': case '[': case 'A': 
            case 'U':
                return true;
            default: 
                return false;
        }
    }
    
    public int setLocal(int local, Value v) {
        assert checkType(v.getTypeDesc()) : "Invalid type: " + v.getTypeDesc();
        locals[local] = v;
        if (v.isCategory2()) {
            locals[local+1] = v;
            return 2;
        }
        return 1;
    }
    
    public Value getLocal(int local, int opcode) {
        Value v = locals[local];
        String desc = v.getTypeDesc();
        String expected = null;
        switch(opcode) {
            case ILOAD: {
                if (TypeDesc.isIntType(desc)) {
                    return v;
                } else {
                    expected = "int";
                }
                break;
            }
            case LLOAD: {
                if (desc == D_LONG) {
                    return v; 
                } else {
                    expected = "long";
                }
                break;
            }
            case DLOAD: {
                if (desc == D_DOUBLE) {
                    return v;
                } else {
                    expected = "double";
                }
                break;
            }
            case FLOAD: {
                if (desc == D_FLOAT) {
                    return v;
                } else {
                    expected = "float";
                }
                break;
            }
            case ALOAD: {
                if (TypeDesc.isRefType(desc)) { 
                    return v;
                } else {
                    expected = "ref";
                }
            }
        }
        throw new AssertionError("Expected " + expected + " in local# " + local + ", got " + desc);
    }
    
    public Value getLocal(int local) {
        return locals[local];
    }
    
    public Value getStack(int pos) {
        // for testing
        return stack[pos];
    }
    
    public Value push(Value v) {
        assert (v != Value.V_UNDEFINED) : "UNDEFINED type pushed";
        assert checkType(v.getTypeDesc()) : "Invalid type: " + v.getTypeDesc();
        stack[stacklen++] = v;
        return v;
    }
    
    public Value pop() {
        try {
            return stack[--stacklen];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RuntimeException("Verify error. Expected word in stack, but stack is empty");
        }
    }
    
    public Value popWord() {
        Value v = pop();
        assert v.isCategory1() : "double word present where single expected";
        return v;
    }
    
    public void popn(int n) {
        stacklen -= n;
    }
    
    void clearStack() {
        stacklen = 0;
    }
    
    @Override
    public boolean equals(Object other) {
        Frame that = (Frame)other;
        for (int i = 0; i < locals.length; i++) {
            if (!locals[i].equals(that.locals[i])) return false;
        }
        for (int i = 0; i < stacklen; i++) {
            if (!stack[i].equals(that.stack[i])) return false;
        }
        return true;
    }
    
    @Override
    public int hashCode() {
        int hash = 0;
        for (int i = 0; i < this.locals.length;i++) hash ^= this.locals[i].hashCode();
        for (int i = 0; i < this.stacklen;i++) hash ^= this.locals[i].hashCode();
        return hash;
    }
    
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer(100);
        int numDefined = 0;
        sb.append("): ");
        for (int i = 0; i < this.locals.length;i++) {
            Value v = locals[i];
            if (v != Value.V_UNDEFINED) {
                numDefined++;
                sb.append(i).append(':').append(this.locals[i]).append(" ");
            }
        }
        sb.insert(0, numDefined);
        sb.insert(0, "Locals(");
        sb.append("\n").append("Stack(").append(stacklen).append("): ");
        for (int i = 0; i < this.stacklen;i++) {
            sb.append(this.stack[i]).append(" ");
        }
        return sb.toString();
    }

    public int getMaxLocals() {
        return locals.length;
    }
    
    public int getStackLen() {
        return stacklen;
    }
    
}
