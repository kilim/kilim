/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.analysis;
import static kilim.Constants.D_UNDEFINED;
import static kilim.Constants.D_NULL;

import java.util.Arrays;

/**
 * A SSA value that represents all objects produced at a particular 
 * location in the code. Value objects are used by dataflow analysis
 * (@see BasicBlock)
 * 
 */
public class Value {
    public static Object NO_VAL = new Object();
    public static Value V_UNDEFINED = new Value(0, D_UNDEFINED, NO_VAL); 
    
    private String typeDesc;
    
    private Object constVal;
    
    private int  numSites;
    private int[] sites;
    
    public int getNumSites() {return numSites;}
    
    public int[] getCreationSites() {return sites;}
    
    public String getTypeDesc() {return typeDesc;}
    
    public Object getConstVal() {return constVal;}
    
    private Value(int aPos, String aDesc, Object aConst) {
        sites = new int[2];
        numSites = 1;
        sites[0] = aPos;
        typeDesc = aDesc;
        constVal = aConst;
        //System.out.println("V[" + aPos + ":" + aDesc + ((aConst == NO_VAL) ? "" : (": " + aConst)) + "]");
    }
    
    private Value(int newNumSites, int[] newSites, String newType, Object newConst) {
        Arrays.sort(newSites, 0, newNumSites);
        numSites = newNumSites;
        sites = newSites;
        typeDesc = newType;
        constVal = newConst;
        /*//debug
        StringBuilder sb = new StringBuilder(80);
        sb.append("V[");
        for (int i = 0; i < newNumSites; i++) {
            if (i > 0) sb.append(",");
            sb.append(newSites[i]);
        }
        sb.append(":").append(newType).append(":");
        if (newConst != NO_VAL) {
            sb.append(": ").append(newConst.toString());
        }
        sb.append("]");
        System.out.println(sb);
        */
    }

    /**
     * Produces a new value (if necessary), if the instructions are different or 
     * the types are different. The types are merged to form a least common
     * upper bound, and the instruction sets are unioned.
     * @param vb
     * @return this if the result of the merge is no different, or the new value
     */
    public Value merge(Value other) {
        int[] newSites = new int[this.numSites + other.numSites];
        for (int i = 0; i < newSites.length; i++) newSites[i] = -1;
        int newNumSites = mergeSites(newSites, other);
        String newType;
        try {
            newType = TypeDesc.mergeType(this.typeDesc, other.typeDesc);
        } catch (IncompatibleTypesException e) {
            newType = D_UNDEFINED;
        }
        Object newConst = (constVal.equals(other.constVal)) ? constVal : NO_VAL;
        if (newNumSites != numSites || newType != typeDesc) {
            return new Value(newNumSites, newSites, newType, newConst);
        } else {
            return this; // no change
        }
    }
    
    private int mergeSites(int[]newSites, Value other) {
        int uniqueNumSites = 0;
        for (int i = 0; i < numSites; i++) {
            uniqueNumSites += addTo(newSites, sites[i]);
        }
        for (int i = 0; i < other.numSites; i++) {
            uniqueNumSites += addTo(newSites, other.sites[i]);
        }
        return uniqueNumSites;
    }
    
    
    private int addTo(int[] newSites, int site) {
        for (int i = 0; i < newSites.length; i++) {
            int s = newSites[i];
            if (s == -1) {
                newSites[i] = site;
                return 1; // added an element 
            }
            if (s == site) return 0; // added no elements
        }
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        // TODO FIXME : This is WRONG. Two values can be created at the same site when
        // entering a method (all incoming parameter values are given location 0). 
        // That would make two distinct params with the same type equal.
        if (this == obj) return true;
        Value other = (Value)obj;
        if (this.typeDesc.equals(other.typeDesc) &&
                this.constVal.equals(other.constVal) &&
                        this.numSites == other.numSites) {
            // Check sites
            for (int i = 0; i < this.numSites; i++) {
                if (sites[i] != other.sites[i]) {
                    return false;
                }
            }
                return true;
            }
        return false;
    }

    @Override
    public int hashCode() {
        int h = typeDesc.hashCode();
        for (int i = 0; i < numSites; i++) {
            h ^= sites[i];
        }
        return h;
    }

    public static Value make(int pos, String desc) {
        return new Value(pos, desc, NO_VAL);
    }
    
    public static Value make(int pos, String desc, Object aConstVal) {
        return new Value(pos, desc, aConstVal);
    }

    public boolean isCategory2() {
        return category() == 2;
    }
    
    public boolean isCategory1() {
        return category() == 1;
    }
    
    @Override
    public String toString() {
        if (numSites == 0 && typeDesc == D_UNDEFINED) return "undef";
        StringBuffer sb = new StringBuffer(40);
        sb.append(typeDesc).append('[');
        for (int i = 0; i < numSites; i++) {
            if (i > 0) sb.append(' ');
            sb.append(sites[i]);
        }
        sb.append(']');
        if (constVal != NO_VAL) {
            sb.append(" == ").append(constVal.toString());
        }
        return sb.toString();
    }

    public boolean isConstant() {
        return constVal != NO_VAL || typeDesc == D_NULL;
    }

    public int category() {
        return TypeDesc.isDoubleWord(typeDesc) ? 2 : 1;
    }
    
}
