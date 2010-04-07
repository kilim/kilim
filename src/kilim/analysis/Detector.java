/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim.analysis;
import static kilim.Constants.D_OBJECT;

import java.util.ArrayList;

import kilim.Constants;
import kilim.NotPausable;
import kilim.Pausable;
import kilim.mirrors.ClassMirror;
import kilim.mirrors.ClassMirrorNotFoundException;
import kilim.mirrors.MethodMirror;
import kilim.mirrors.Mirrors;
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

    public static final Detector DEFAULT = new Detector(Mirrors.getRuntimeMirrors());

    private final Mirrors mirrors;
    
    public Detector(Mirrors mirrors) {
    	this.mirrors = mirrors;
    	
         NOT_PAUSABLE = mirrors.mirror(NotPausable.class);
         PAUSABLE = mirrors.mirror(Pausable.class);
         OBJECT = mirrors.mirror(Object.class);

	}

    ClassMirror NOT_PAUSABLE, PAUSABLE, OBJECT;
    
	public boolean isPausable(String className, String methodName,
            String desc) {
        return getPausableStatus(className, methodName, desc) == PAUSABLE_METHOD_FOUND;
    }

    /**
     * @return one of METHOD_NOT_FOUND, PAUSABLE_METHOD_FOUND, METHOD_NOT_PAUSABLE
     */
    
    public int getPausableStatus(String className, String methodName,
            String desc) 
    {
        int ret = METHOD_NOT_FOUND;
        if (methodName.endsWith("init>")) {
            return METHOD_NOT_PAUSABLE; // constructors are not pausable.
        }
        className = className.replace('/', '.');
        try {
            ClassMirror cl = mirrors.classForName(className);
            MethodMirror m = findMethod(cl, methodName, desc);
            if (m != null) {
                for (ClassMirror c: m.getExceptionTypes()) {
                    if (NOT_PAUSABLE.isAssignableFrom(c)) {
                        return METHOD_NOT_PAUSABLE;
                    }
                    if (PAUSABLE.isAssignableFrom(c)) {
                        return PAUSABLE_METHOD_FOUND;
                    }
                }
                return METHOD_NOT_PAUSABLE;
            }
         } catch (ClassMirrorNotFoundException ignore) {
             
         } catch (VerifyError ve) {
             return AsmDetector.getPausableStatus(className, methodName, desc, this);
         }
         return ret;
    }
    
    private MethodMirror findMethod(ClassMirror cl, String methodName, String desc) {
        if (cl == null) return null;
        MethodMirror m = findMethodInHierarchy(cl, methodName, desc);
        if (m == null) {
            cl = mirrors.mirror(Object.class);
            for (MethodMirror om : cl.getDeclaredMethods()) {
                if (om.getName().equals(methodName) && om.getMethodDescriptor().equals(desc)) {
                    return om;
                }
            }
        }
        return m;
    }
    
    private MethodMirror findMethodInHierarchy(ClassMirror cl, String methodName,
            String desc) {
        if (cl == null)  return null;
        
        for (MethodMirror om : cl.getDeclaredMethods()) {
            if (om.getName().equals(methodName) && om.getMethodDescriptor().equals(desc)) {
                if (om.isBridge()) continue;
                return om;
            }
        }

        if (OBJECT.equals(cl))
            return null;

        MethodMirror m = findMethodInHierarchy(cl.getSuperclass(), methodName, desc);
        if (m != null)
            return m;
        for (ClassMirror ifcl : cl.getInterfaces()) {
            m = findMethodInHierarchy(ifcl, methodName, desc);
            if (m != null)
                return m;
        }
        return null;
    }

    public static String D_FIBER_ = Constants.D_FIBER + ")";

    @SuppressWarnings("unused")
    private static String statusToStr(int st) {
        switch (st) {
        case METHOD_NOT_FOUND : return "not found";
        case PAUSABLE_METHOD_FOUND : return "pausable";
        case METHOD_NOT_PAUSABLE : return "not pausable";
        default: throw new AssertionError("Unknown status");
        }
    }
    

    static private final ThreadLocal<Detector> DETECTOR = new ThreadLocal<Detector>();
    
    static Detector getDetector() {
    	Detector d = DETECTOR.get();
    	if (d == null) return Detector.DEFAULT;
    	return d;
}

    static Detector setDetector(Detector d) {
    	Detector res = DETECTOR.get();
    	DETECTOR.set(d);
    	return res;
    }

	public String commonSuperType(String oa, String ob) throws ClassMirrorNotFoundException {
        String a = toClassName(oa);
        String b = toClassName(ob);
        
        try {
	        ClassMirror ca = mirrors.classForName(a);
	        ClassMirror cb = mirrors.classForName(b);
	        if (ca.isAssignableFrom(cb)) return oa;
	        if (cb.isAssignableFrom(ca)) return ob;
	        if (ca.isInterface() && cb.isInterface()) {
	            return D_OBJECT; // This is what the java bytecode verifier does
	        }
        } catch (ClassMirrorNotFoundException e) {
        	// try to see if the below works...
        }
        
        ArrayList<String> sca = getSuperClasses(a);
        ArrayList<String> scb = getSuperClasses(b);
        int lasta = sca.size()-1;
        int lastb = scb.size()-1;
        do {
            if (sca.get(lasta).equals(scb.get(lastb))) {
                lasta--;
                lastb--;
            } else {
                break;
            }
        } while (lasta >= 0 && lastb >= 0);
        return toDesc(sca.get(lasta+1));		
	}
	

    public ArrayList<String> getSuperClasses(String cc) throws ClassMirrorNotFoundException {
    	ClassMirror c = mirrors.classForName(cc);
        ArrayList<String> ret = new ArrayList<String>(3);
        while (c != null) {
            ret.add(c.getName());
            c = c.getSuperclass();
        }
        return ret;
        
    }
    
    private static String toDesc(String name) {
        return (name.equals(JAVA_LANG_OBJECT)) ?
                D_OBJECT : "L" + name.replace('.', '/') + ';';
    }

    private static String toClassName(String s) {
        return s.replace('/','.').substring(1,s.length()-1);
    }
    
    static String JAVA_LANG_OBJECT = "java.lang.Object"; 

}
