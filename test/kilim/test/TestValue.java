/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import kilim.analysis.Value;
import junit.framework.TestCase;
import static kilim.Constants.*;

public class TestValue extends TestCase {
    public void testSameSiteMerge() {
        Value v = Value.make(10, D_STRING);
        v = v.merge(Value.make(20, D_OBJECT));
        Value oldV = v;
        for (int i = 0; i < 10; i++) {
            v = v.merge(Value.make(10, D_STRING));
        }
        assertSame(oldV, v);
    }
    
    public void testDifferentSitesMerge() {
        Value v1 = Value.make(2, D_INT);
        Value v2 = Value.make(3, D_INT);
        Value v3 = Value.make(5, D_INT);
        Value v = v1.merge(v2);
        v = v.merge(v3);
        assertTrue(v.getNumSites() == 3);
        int[] sites = v.getCreationSites();
        int prod = 1;
        for (int i = 0; i < 3; i++) {
            prod *= sites[i];
        }
        assertTrue(prod == 30);
        
        Value oldV = v;
        
        // Ensure order of merges don't matter
        v = v3.merge(v2);
        v = v.merge(v1);
        assertEquals(v, oldV);
    }
    
    
    public void testTypeMerge() {
        Value v1 = Value.make(2, "Lkilim/test/ex/ExC;");
        Value v2 = Value.make(3, "Lkilim/test/ex/ExD;");
        Value v3 = Value.make(5, "Lkilim/test/ex/ExA;");
        
        Value v = v1.merge(v1);
        assertSame(v, v1);
        
        v = v1.merge(v2);
        assertEquals("Lkilim/test/ex/ExA;", v.getTypeDesc());
        v = v3.merge(v2);
        assertEquals("Lkilim/test/ex/ExA;", v.getTypeDesc());
        
        Value v4 = Value.make(7, D_INT);;
        v = v3.merge(v4);
        assertSame(D_UNDEFINED, v.getTypeDesc());
    }
    
    public void testConstMerge() {
        Value v1 = Value.make(99, D_STRING, "String1");
        Value v2 = Value.make(100, D_STRING, new String("String1")); // create a new String 
        Value v= v1.merge(v2);
        assertTrue(v.getConstVal().equals("String1"));
        v = v1.merge(Value.make(101, D_STRING, "Some other string"));
        assertTrue(v.getConstVal().equals(Value.NO_VAL));
    }
}
