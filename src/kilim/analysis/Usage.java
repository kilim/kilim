/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.analysis;

import java.util.ArrayList;
import java.util.BitSet;

/**
 * Each BasicBlock owns one instance of Usage. This class maintains, in essence, three vectors of
 * booleans, indexed by the local variable number. Since it is <i>very</i> rare for a method to have
 * more than 31 local variables, the vectors are represented by int bitmaps. For more than this, the
 * basic block creates an instance of BigUsage that is functionally identical (TODO)
 * 
 * Note that we don't need to track usage of operand stack. All elements of the operand stack are
 * always live, and always need to be stored and restored (during stack switching). This is not true
 * of local vars; a var may have a valid value which may not be used downstream, so we track which
 * vars must be taken seriously.
 * 
 * @see BasicBlock
 */
public class Usage {
    /**
     * The number of local vars in the owning BB's frame
     */
    private int nLocals;

    /**
     * bit(i) == 1 (counting from LSB) if the ith local var is live downstream
     */
    private BitSet in;

    /**
     *  born.bit(i) == 1 (from LSB) if the ith var has been defined prior
     *  either as a method parameter or by a write
     *  only vars that have been born can be live
     *  (used to protect against vars that are def'd in a block and used in the catch, eg the exception)
     */
    private BitSet born;

    /**
     * use.bit(i) == 1 (from LSB) if the ith var is read before it has been written. The bit vector
     * as a whole represents the set of vars that the BB needs from its predecessors.
     */
    private BitSet use;

    /**
     * def.bit(i) == 1 (from LSB) if the ith var is written into before it has been read. It
     * represents all the vars that this BB is capable of supplying downstream on its own, hence
     * those vars are not required to be supplied by its predecessors (even if they do supply them,
     * they will be overwritten anyway).
     */
    private BitSet def;

    public Usage(int numLocals) {
        nLocals = numLocals;
        in = new BitSet(numLocals);
        use = new BitSet(numLocals);
        def = new BitSet(numLocals);
        born = new BitSet(numLocals);
    }

    public void read(int var) {
        assert var < nLocals : "local var num=" + var + " exceeds nLocals = " + nLocals;
        if (!def.get(var)) {
            // is not def'd earlier
            use.set(var);
        }
    }

    public void write(int var) {
        assert var < nLocals : "local var num=" + var + " exceeds nLocals = " + nLocals;
        def.set(var);
    }
    public void born(int var) {
        assert var < nLocals : "local var num=" + var + " exceeds nLocals = " + nLocals;
        born.set(var);
    }

    /**
     * return true if var is live at the entrance to this BB.
     */
    public boolean isLiveIn(int var) {
        return in.get(var);
    }

    /**
     * This is the standard liveness calculation (Dragon Book, section 10.6). At each BB (and its
     * corresponding usage), we evaluate "in" using use and def. in = use U (out \ def) where out =
     * U succ.in, for all successors
     * 
     * this algorithm has been modified to treat catch blocks as occurring anywhere
     * ie, that vars defined in a try may never be set
     * however, this only applies to vars that have been defined at least once (ie, born)
     */
    public boolean evalLiveIn(ArrayList<Usage> succUsage,ArrayList<Usage> handUsage) {
        BitSet out = new BitSet(nLocals);
        BitSet old_in = (BitSet) in.clone();
        if (handUsage==null) handUsage = new ArrayList();
        if (succUsage.size() == 0) {
            in = use;
        } else {
            // calculate out = U succ.in
            out = (BitSet) succUsage.get(0).in.clone();
            for (int i = 1; i < succUsage.size(); i++) {
                out.or(succUsage.get(i).in);
            }
            // calc out \ def == out & ~def == ~(out | def)
            // unless a var has been def'd in all catch blocks, assume it may fail to def
            BitSet def1 = (BitSet) def.clone();
            for (Usage usage : handUsage)
                def1.and(usage.def);
            def1.flip(0, nLocals);
            out.and(def1);
            for (Usage usage : handUsage)
                out.or(usage.use);
            // catch block vars may be def'd in this block, but we can't easily know if the
            // def has occurred before the throw
            // if the var has never been def'd (or was a parameter), then it can't be live
            out.and(born);
            out.or(use);
            in = out;
        }
        return !(in.equals(old_in));
    }
    /** 
     *  evolve the born bits a single iteration
     *  ie if a var is def'd in this block, then it is born
     *     and if it is born in this block, then it is born in all successors
     */
    public boolean evalBornIn(ArrayList<Usage> succUsage) {
        boolean changed = false;
        born.or(def);
        for (Usage usage : succUsage) {
            BitSet old = (BitSet) usage.born.clone();
            usage.born.or(born);
            if (!old.equals(usage.born)) changed = true;
        }
        return changed;
    }

    /**
     * Called to coalesce a successor's usage into the current BB. Important: This should be called
     * before live variable analysis begins, because we don't bother merging this.in or this.born.
     */
    void absorb(Usage succ) {
        BitSet b = (BitSet) this.def.clone();
        b.flip(0, nLocals);
        b.and(succ.use);
        this.use.or(b);
        this.def.or(succ.def);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("use");
        printBits(sb, use);
        sb.append("def");
        printBits(sb, def);
        sb.append("in");
        printBits(sb, in);
        sb.append("born");
        printBits(sb, born);
        return sb.toString();
    }
    public String toStringBits(String sep) {
        StringBuffer sb = new StringBuffer();
        printBitsFull(sb,use);
        sb.append("   ");
        printBitsFull(sb,def);
        sb.append("   ");
        printBitsFull(sb,in);
        sb.append("   ");
        printBitsFull(sb,born);
        return sb.toString();
    }

    private void printBits(StringBuffer sb, BitSet b) {
        int numDefined = 0;
        for (int i = 0; i < nLocals; i++) {
            if (b.get(i))
                numDefined++;
        }
        sb.append('(').append(numDefined).append("): ");
        for (int i = 0; i < nLocals; i++) {
            if (b.get(i))
                sb.append(i).append(' ');
        }
        sb.append('\n');
    }

    private void printBitsFull(StringBuffer sb, BitSet b) {
        for (int i = 0; i < nLocals; i++) {
            if (i > 0 && i%10==0) sb.append('.');
            sb.append(b.get(i) ? '1' : '0');
        }
    }
    
    /**
     * This is purely for testing purposes.
     * 
     * @param var
     *            local var index
     */
    public void setLiveIn(int var) {
        in.set(var);
    }
    /**
     * This is purely for testing purposes.
     * 
     * @param var
     *            local var index
     */
    public void setBornIn(int var) {
        born.set(var);
    }

    Usage copy() {
        Usage ret = new Usage(nLocals);
        ret.use = use;
        ret.def = def;
        return ret;
    }
}
