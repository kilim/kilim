/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.analysis;
import static kilim.Constants.THROWABLE_CLASS;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Representation for a catch handler. 
 */
public class Handler implements Comparable<Handler> {
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

    public Handler(int aFrom, int aTo, String aType, BasicBlock aCatchBB) {
        from = aFrom;
        to = aTo;
        if (aType == null) {
            // try/finally is compiled with a covering catch handler with
            // type null. It is the same as catching Throwable.
            aType = THROWABLE_CLASS;
        }
        type = aType;
        catchBB = aCatchBB;
    }
    
    public int compareTo(Handler h) {
        int c = this.type.compareTo(h.type);
        if (c != 0) return c;
        
        c = this.catchBB.compareTo(h.catchBB);
        if (c != 0) return c;

        return from < h.from ? -1 : (from == h.from) ? 0 : 1;
    }
    
    public static ArrayList<Handler> consolidate( ArrayList<Handler> list) {
        Collections.sort(list);
        ArrayList<Handler> newList = new ArrayList<Handler>(list.size());
        Handler cur = null;
        for (Handler h: list) {
            if (cur == null) {
                cur = h;
                newList.add(cur);
                continue;
            } 
            // Two options here. Either h is contiguous with c or it isn't. Contiguous
            // means that it has to be the same type and the same catchBB and  
            // from == to+1
            if (cur.type.equals(h.type) && (cur.catchBB == h.catchBB) && (h.from == cur.to + 1)) {
                cur.to = h.to;
            } else {
                cur = h;
                newList.add(cur);
            }
        }
        return newList;
    }

}