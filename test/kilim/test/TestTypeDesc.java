/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import kilim.analysis.IncompatibleTypesException;
import kilim.analysis.TypeDesc;
import junit.framework.TestCase;
import static kilim.Constants.*;
import java.lang.reflect.*;
import java.util.Arrays;

public class TestTypeDesc extends TestCase {
    public void testInterning() throws Exception {
        // Verify all strings in Constants that start with "D_"
        // are indeed interned.
        Class<?> c = Class.forName("kilim.Constants");
        Field[] fields = c.getFields();
        for (Field f:fields) {
            if (f.getName().startsWith("D_")) {
                String val = f.get(null).toString();
                assertSame(TypeDesc.getInterned(new String(val)), val);
            }
        }
    }
    
    public void testComponentType() {
        assertSame(TypeDesc.getComponentType("[J"), D_LONG);
        assertSame(TypeDesc.getComponentType("[Ljava/lang/String;"), D_STRING);
    }
    
    public void testCommonSuperTypes() {
        // Two interfaces => Object. Checking interning at the same time.
        assertSame(TypeDesc.commonSuperType("Ljava/io/Serializable;", "Ljava/lang/Comparable;"),
                D_OBJECT);
        assertEquals(TypeDesc.commonSuperType("Lkilim/BasicBlock;", 
        "Lkilim/BasicBlock;"), "Lkilim/BasicBlock;");
        assertSame(TypeDesc.commonSuperType("[Z", "[Z"), D_ARRAY_BOOLEAN);
        
        // least upper bound of Field and Method is AccessibleObject
        assertEquals("Ljava/lang/reflect/AccessibleObject;", 
                TypeDesc.commonSuperType("Ljava/lang/reflect/Field;", 
                "Ljava/lang/reflect/Method;"));
        
        // least upper bound of Field and AccessibleObject is AccessibleObject
        assertEquals("Ljava/lang/reflect/AccessibleObject;", 
                TypeDesc.commonSuperType("Ljava/lang/reflect/Field;", 
                "Ljava/lang/reflect/AccessibleObject;"));
        
        // Same as above, but flip the  order to see if it is sensitive.
        assertEquals("Ljava/lang/reflect/AccessibleObject;", 
                TypeDesc.commonSuperType("Ljava/lang/reflect/Field;", 
                "Ljava/lang/reflect/AccessibleObject;"));
        
        assertEquals("Lkilim/test/ex/ExA;", 
                TypeDesc.commonSuperType("Lkilim/test/ex/ExA;", "Lkilim/test/ex/ExD;"));
        
        assertEquals("Lkilim/test/ex/ExA;", 
                TypeDesc.commonSuperType("Lkilim/test/ex/ExD;", "Lkilim/test/ex/ExA;"));
        
        assertEquals("Lkilim/test/ex/ExA;", 
                TypeDesc.commonSuperType("Lkilim/test/ex/ExC;", "Lkilim/test/ex/ExD;"));
        
    }
    
    public void testArray() throws IncompatibleTypesException {
        assertSame(D_OBJECT, 
                TypeDesc.mergeType("Lkilim/test/ex/ExC;", "[Z"));
        
        assertSame(D_OBJECT, 
                TypeDesc.mergeType("[Z", "Lkilim/test/ex/ExC;"));
    }
    
    public void testNull() throws IncompatibleTypesException {
        assertSame(D_NULL, TypeDesc.mergeType(D_NULL, D_NULL));
        assertSame(D_OBJECT, TypeDesc.mergeType(D_OBJECT, D_NULL));
        assertSame(D_OBJECT, TypeDesc.mergeType(D_NULL, D_OBJECT));
    }
    public void testNumArgs() throws IncompatibleTypesException {
        assertTrue(TypeDesc.getNumArgumentTypes("()V") == 0);
        assertTrue(TypeDesc.getNumArgumentTypes("(Ljava/lang/String;[[[ZZBCDSIJF)V") == 10);
    }
    
    public void testReturnType() throws IncompatibleTypesException {
        assertTrue(TypeDesc.getReturnTypeDesc("()V") == D_VOID);
        assertTrue(TypeDesc.getReturnTypeDesc("()[I") == D_ARRAY_INT);
        assertTrue(TypeDesc.getReturnTypeDesc("(IIII)[Ljava/lang/Throwable;").equals("[Ljava/lang/Throwable;"));
    }
    
    public void testArgTypes() throws IncompatibleTypesException {
        String[] types = TypeDesc.getArgumentTypes("([Ljava/lang/String;[[[ZZBCDSIJF)V");
        String[] expected = new String[] {"[Ljava/lang/String;","[[[Z", D_BOOLEAN, D_BYTE,D_CHAR, 
                D_DOUBLE,D_SHORT,D_INT,D_LONG,D_FLOAT};
        assertTrue(Arrays.equals(types, expected));
    }
    
    public void testMerge() throws IncompatibleTypesException {
        // testCommonSuperTypes() has already checked many combinations of 
        // classes, arrays and interfaces. Handle null etc. 
        
        // Null + String => String
        assertSame(D_STRING, TypeDesc.mergeType(D_NULL, D_STRING));
        
        // Null + X == X (order of D_NULL flipped this time)
        assertSame(D_ARRAY_DOUBLE, TypeDesc.mergeType("[D", D_NULL));
        
        // primitive types should return the same
        assertEquals(D_DOUBLE, TypeDesc.mergeType(D_DOUBLE, D_DOUBLE));
        
        // Array + Object -> Array
        assertSame(D_OBJECT, TypeDesc.mergeType("[I", D_OBJECT));
        
        assertSame(D_OBJECT, TypeDesc.mergeType("[I", "[D"));
        
        // common supertype of arrays
        assertEquals("[Ljava/lang/reflect/AccessibleObject;",
                TypeDesc.mergeType("[Ljava/lang/reflect/Field;","[Ljava/lang/reflect/Method;"));
        
        // A inherits from B ==> merge(A[], B[]) = B[]
        assertEquals("[Ljava/lang/reflect/AccessibleObject;", 
                TypeDesc.mergeType("[Ljava/lang/reflect/Method;", "[Ljava/lang/reflect/AccessibleObject;"));
        
        // A inherits from B ==> merge(A[], B[]) = A[]
        assertEquals("[Ljava/lang/reflect/AccessibleObject;", 
                TypeDesc.mergeType("[Ljava/lang/reflect/AccessibleObject;", "[Ljava/lang/reflect/Method;"));
        
    }
    
    public void testInvalidCombinations() {
        assertInvalidCombo("I", D_OBJECT);
        assertInvalidCombo(D_OBJECT, D_INT);
        assertInvalidCombo("Meaningless", D_OBJECT);
    }
    
    private void assertInvalidCombo(String a, String b) {
        try {
            TypeDesc.mergeType(a,b);
            fail("Types '" + a + "' and '" + b + "' aren't supposed to be compatible");
        } catch (IncompatibleTypesException ignore) {
            // Good. It is supposed to fail
        }
    }
}
