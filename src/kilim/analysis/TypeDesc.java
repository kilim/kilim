/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.analysis;

import static kilim.Constants.D_BOOLEAN;
import static kilim.Constants.D_BYTE;
import static kilim.Constants.D_CHAR;
import static kilim.Constants.D_DOUBLE;
import static kilim.Constants.D_FLOAT;
import static kilim.Constants.D_INT;
import static kilim.Constants.D_LONG;
import static kilim.Constants.D_NULL;
import static kilim.Constants.D_OBJECT;
import static kilim.Constants.D_SHORT;
import static kilim.Constants.D_STRING;
import static kilim.Constants.D_UNDEFINED;

import java.lang.reflect.Field;
import java.util.HashMap;

import kilim.Constants;
import kilim.mirrors.ClassMirrorNotFoundException;
import kilim.mirrors.Detector;

import org.objectweb.asm.Type;

/**
 * A utility class that provides static methods for interning type strings and merging type
 * descriptors.
 * 
 */
public class TypeDesc {
    static final HashMap<String, String> knownTypes = new HashMap<String, String>(30);

    static {
        Field[] fields = Constants.class.getFields();
        try {
            for (int i = 0; i < fields.length; i++) {
                Field f = fields[i];
                if (f.getName().startsWith("D_")) {
                    String val = (String) f.get(null);
                    knownTypes.put(val, val);
                }
            }
        } catch (IllegalAccessException iae) {
            iae.printStackTrace();
        }
        knownTypes.put("java/lang/Object", D_OBJECT);
        knownTypes.put("java/lang/String", D_STRING);
    }

    static boolean isDoubleWord(String desc) {
        return (desc == D_DOUBLE || desc == D_LONG);
    }

    public static String getInterned(String desc) {
        String ret = knownTypes.get(desc);
        if (ret == null) {
            switch (desc.charAt(0)) {
            case 'L':
            case '[':
                return desc;
            default:
                return "L" + desc + ';';
            }
        } else {
            return ret;
        }
    }

    public static String getReturnTypeDesc(String desc) {
        return getInterned(desc.substring(desc.indexOf(")") + 1));
    }

    static boolean isSingleWord(String desc) {
        return !isDoubleWord(desc);
    }

    public static String getComponentType(String t) {
        if (t.charAt(0) != '[') {
            throw new InternalError("Can't get component type of " + t);
        }
        return getInterned(t.substring(1));
    }

    public static String getTypeDesc(Object object) {
        if (object instanceof Integer)
            return D_INT;
        if (object instanceof Long)
            return D_LONG;
        if (object instanceof Float)
            return D_FLOAT;
        if (object instanceof Double)
            return D_DOUBLE;
        if (object instanceof String)
            return D_STRING;
        if (object instanceof Boolean)
            return D_BOOLEAN;
        if (object instanceof Type)
            return TypeDesc.getInterned(((Type) object).getDescriptor());
        throw new InternalError("Unrecognized ldc constant: " + object);
    }

    private static int typelen(char[] buf, int off) {
        int start = off;
        switch (buf[off]) {
        case 'L':
            while (buf[off++] != ';') {}
            return off - start;
        case 'B':
        case 'C':
        case 'D':
        case 'F':
        case 'I':
        case 'J':
        case 'S':
        case 'Z':
        case 'V':
            return 1;
        case '[':
            return typelen(buf, off + 1) + 1;
        default:
            throw new InternalError("Unknown descriptor type");
        }
    }

    public static String[] getArgumentTypes(String methodDescriptor) {
        char[] buf = methodDescriptor.toCharArray();
        int size = getNumArgumentTypes(buf);
        String[] args = new String[size];
        size = 0;
        int off = 1;
        while (buf[off] != ')') {
            int len = typelen(buf, off);
            args[size] = getInterned(new String(buf, off, len));
            off += len;
            size += 1;
        }
        return args;
    }

    public static int getNumArgumentTypes(String desc) {
        return getNumArgumentTypes(desc.toCharArray());
    }

    public static int getNumArgumentTypes(char[] buf) {
        int off = 1;
        int size = 0;
        while (true) {
            if (buf[off] == ')') {
                break;
            }
            off += typelen(buf, off);
            size++;
        }
        return size;
    }

    /**
     * Given two type descriptors, it returns an appropriate merge: 1) If they are Array types, the
     * result is a an array of the merged component types 2) If they are ref types, it returns the
     * least common super type. If one of them is an interface, the result is D_OBJECT 3) All other
     * types must match exactly in order to not raise an error.
     */

    public static String mergeType(String a, String b) throws IncompatibleTypesException {
        // given: a and b are different.
        if (a == D_UNDEFINED)
            return b;
        if (b == D_UNDEFINED)
            return a;
        char ac = a.charAt(0);
        char bc = b.charAt(0);
        if (a == D_NULL) {
            assert b == D_NULL || bc == 'L' || bc == '[' : "merging NULL type with non ref type: "
                    + b;
            return b;
        }
        if (b == D_NULL) {
            assert b == D_NULL || bc == 'L' || bc == '[' : "merging NULL type with non ref type: "
                    + a;
            return a;
        }
        if (a == b || a.equals(b))
            return a;
        switch (ac) {
        case 'N': // D_NULL
            if (bc == 'L')
                return b;
            break;
        case 'L':
            if (bc == 'L') {
                return commonSuperType(a, b);
            } else if (bc == 'N') {
                return a;
            } else if (bc == '[') {
                return D_OBJECT; // common supertype of Ref and ArrayRef
            }
            break;
        case '[':
            if (bc == '[') {
                try {
                    return "["
                            + mergeType(TypeDesc.getComponentType(a), TypeDesc.getComponentType(b));
                } catch (IncompatibleTypesException ite) {
                    // The component types are incompatible, but two disparate arrays still
                    // inherit from Object
                    return D_OBJECT;
                }
            } else if (bc == 'L') {
                return D_OBJECT; // common supertype of Ref and ArrayRef
            }
            break;
        case 'I':
        case 'Z':
        case 'B':
        case 'C':
        case 'S':
            // all int types are interchangeable
            switch (bc) {
            case 'I':
            case 'Z':
            case 'B':
            case 'C':
            case 'S':
                return D_INT;
            }
            break;
        }
        throw new IncompatibleTypesException("" + a + "," + b);
    }

    static String JAVA_LANG_OBJECT = "java.lang.Object";

    // public for testing purposes
    public static String commonSuperType(String oa, String ob) {
        try {
            if (oa == D_OBJECT || ob == D_OBJECT)
                return D_OBJECT;
            if (oa.equals(ob))
                return oa;

            String lub = Detector.getDetector().commonSuperType(oa, ob);

            return lub;

        } catch (ClassMirrorNotFoundException cnfe) {
            throw new InternalError(cnfe.getMessage());
        }
    }

    public static boolean isIntType(String typeDesc) {
        return (typeDesc == D_INT || typeDesc == D_CHAR || typeDesc == D_SHORT
                || typeDesc == D_BYTE || typeDesc == D_BOOLEAN);
    }

    public static boolean isRefType(String typeDesc) {
        char c = typeDesc.charAt(0);
        return typeDesc == D_NULL || c == '[' || c == 'L';
    }

    public static String getInternalName(String desc) {
        if (desc.charAt(0) == 'L') {
            return desc.substring(1, desc.length() - 1);
        } else {
            assert desc.charAt(0) == '[' : "Unexpected internal name " + desc;
            return desc;
        }
    }

    // public static void main(String[] args) throws Exception {
    // System.out.println(mergeType("Lkilim/test/ex/ExC;", "Lkilim/test/ex/ExD;"));
    // }

}