/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.analysis;
import static kilim.Constants.THROWABLE_CLASS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Representation for a catch handler. 
 */
public class Handler {
    /**
     * Source offset in method's instruction list
     */
    public int        from;

    /**
     * End offset in method's instruction list
     */
    public int        to;

    /**
     * Exception type
     */
    public String     type;

    /**
     * catch handler's entry point
     */
    public BasicBlock catchBB;

    /** the position of the corresponding entry in the unwoven exception table */    
    public int pos;

    public Handler(int aFrom, int aTo, Handler h) {
        this(aFrom,aTo,h.type,h.catchBB,h.pos);
    }
    
    public Handler(int aFrom,int aTo,String aType,BasicBlock aCatchBB,int pos) {
        from = aFrom;
        to = aTo;
        if (aType == null) {
            // try/finally is compiled with a covering catch handler with
            // type null. It is the same as catching Throwable.
            aType = THROWABLE_CLASS;
        }
        type = aType;
        catchBB = aCatchBB;
        this.pos = pos;
    }
    
    private int comparePos(Handler h) {
        return from < h.from ? -1 : (from == h.from) ? 0 : 1;
    }
    
    public static ArrayList<Handler> consolidate(ArrayList<Handler> list) {
        ArrayList<Handler> newList = new ArrayList<Handler>(list.size());
        outer:
        for (Handler c : list) {
            for (Handler h : newList) {
                // Two options here. Either h is contiguous with c or it isn't. Contiguous
                // means that it has to be the same type and the same catchBB and  
                // from == to+1
                if (c.type.equals(h.type) & c.catchBB==h.catchBB) {
                    if      (h.from==c.to+1) { h.from = c.from; continue outer; }
                    else if (c.from==h.to+1) { h.to   = c.to; continue outer; }
                }
            }
            newList.add(c);
        }
        Collections.sort(newList,resort);
        return newList;
    }

    private static Resort resort = new Resort();
    private static class Resort implements Comparator<Handler> {
        public int compare(Handler o1,Handler o2) {
            return o1.pos < o2.pos ? -1 : o1.pos==o2.pos ? 0:1;
        }
    }
    
    
    
    /** return a Comparator that orders the handlers by start position */
    public static Comparator<Handler> startComparator() { return comp; }
    private static Comp comp = new Comp();
    private static class Comp implements Comparator<Handler> {
        public int compare(Handler o1,Handler o2) {
            return o1.comparePos(o2);
        }
    }
    
}