/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim.analysis;
import java.lang.reflect.Method;

import kilim.Constants;
import kilim.NotPausable;
import kilim.Pausable;

import org.objectweb.asm.Type;
/**
 * Utility class to check if a method has been marked pausable
 *
 */
public class Detector {
    public static final int                   METHOD_NOT_FOUND         = 0;
    public static final int                   PAUSABLE_METHOD_FOUND    = 1;
    public static final int                   METHOD_NOT_PAUSABLE      = 2;

    // Note that we don't have the kilim package itself in the following list.
    static final String[]                     STANDARD_DONT_CHECK_LIST = {
            "java.", "javax."                                         };

    public static boolean isPausable(String className, String methodName,
            String desc) {
        return getPausableStatus(className, methodName, desc) == PAUSABLE_METHOD_FOUND;
    }

    /**
     * @return one of METHOD_NOT_FOUND, PAUSABLE_METHOD_FOUND, METHOD_NOT_PAUSABLE
     */
    
    public static int getPausableStatus(String className, String methodName,
            String desc) 
    {
        int ret = METHOD_NOT_FOUND;
        if (methodName.endsWith("init>")) {
            return METHOD_NOT_PAUSABLE; // constructors are not pausable.
        }
        className = className.replace('/', '.');
        try {
            Class cl = Class.forName(className);
            Method m = findMethod(cl, methodName, desc);
            if (m != null) {
                for (Class c: m.getExceptionTypes()) {
                    if (NotPausable.class.isAssignableFrom(c)) {
                        return METHOD_NOT_PAUSABLE;
                    }
                    if (Pausable.class.isAssignableFrom(c)) {
                        return PAUSABLE_METHOD_FOUND;
                    }
                }
                return METHOD_NOT_PAUSABLE;
            }
         } catch (ClassNotFoundException ignore) {
             
         } catch (VerifyError ve) {
             return AsmDetector.getPausableStatus(className, methodName, desc);
         }
         return ret;
    }
    
    public static Method findMethod(Class cl, String methodName, String desc) {
        if (cl == null) return null;
        Method m = findMethodInHierarchy(cl, methodName, desc);
        if (m == null) {
            cl = Object.class;
            for (Method om : cl.getDeclaredMethods()) {
                if (om.getName().equals(methodName) && Type.getMethodDescriptor(om).equals(desc)) {
                    return om;
                }
            }
        }
        return m;
    }
    
    public static Method findMethodInHierarchy(Class cl, String methodName,
            String desc) {
        if (cl == null)  return null;
        
        for (Method om : cl.getDeclaredMethods()) {
            if (om.getName().equals(methodName) && Type.getMethodDescriptor(om).equals(desc)) {
                if (om.isBridge()) continue;
                return om;
            }
        }

        if (cl == Object.class)
            return null;

        Method m = findMethodInHierarchy(cl.getSuperclass(), methodName, desc);
        if (m != null)
            return m;
        for (Class ifcl : cl.getInterfaces()) {
            m = findMethodInHierarchy(ifcl, methodName, desc);
            if (m != null)
                return m;
        }
        return null;
    }

    public static String D_FIBER_ = Constants.D_FIBER + ")";
    private static String statusToStr(int st) {
        switch (st) {
        case METHOD_NOT_FOUND : return "not found";
        case PAUSABLE_METHOD_FOUND : return "pausable";
        case METHOD_NOT_PAUSABLE : return "not pausable";
        default: throw new AssertionError("Unknown status");
        }
    }
}
