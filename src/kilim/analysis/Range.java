/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.analysis;

/**
 * Used by catch handlers to handle overlapping ranges
 *
 */
public class Range {
    int from;
    int to;

    public Range(int aFrom, int aTo) {
        from = aFrom;
        to = aTo;
    }
    
    static Range intersect(int a1, int e1, int a2, int e2) {
        // a2 lies between a1 and e1 or a1 between a2 and e2
        // all comparisons are inclusive of endpoints
        assert a1 <= e1 && a2 <= e2;
        int a;
        if (a1 <= a2 && a2 <= e1) {
            a = a2;
        } else if (a2 <= a1 && a1 <= e2) {
            a = a1; 
        } else {
            return null;
        }
        return new Range(a, e1 < e2 ? e1 : e2);
    }

}
