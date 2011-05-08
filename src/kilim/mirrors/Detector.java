/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim.mirrors;

import static kilim.Constants.D_OBJECT;

import java.util.ArrayList;

import kilim.Constants;
import kilim.NotPausable;
import kilim.Pausable;
import kilim.analysis.AsmDetector;

/**
 * Utility class to check if a method has been marked pausable
 * 
 */
public class Detector {
    public static final int METHOD_NOT_FOUND_OR_PAUSABLE = 0; // either not found, or not pausable if found.
    public static final int PAUSABLE_METHOD_FOUND = 1; // known to be pausable
    public static final int METHOD_NOT_PAUSABLE = 2; // known to be not pausable
    

    // Note that we don't have the kilim package itself in the following list.
    static final String[] STANDARD_DONT_CHECK_LIST = { "java.", "javax." };

    public static final Detector DEFAULT = new Detector(new RuntimeClassMirrors());

    public final Mirrors mirrors;

    public Detector(Mirrors mirrors) {
        this.mirrors = mirrors;

        NOT_PAUSABLE = mirrors.mirror(NotPausable.class);
        PAUSABLE = mirrors.mirror(Pausable.class);
        OBJECT = mirrors.mirror(Object.class);

    }

    ClassMirror NOT_PAUSABLE, PAUSABLE, OBJECT;

    public boolean isPausable(String className, String methodName, String desc) {
        return getPausableStatus(className, methodName, desc) == PAUSABLE_METHOD_FOUND;
    }

    /**
     * @return one of METHOD_NOT_FOUND, PAUSABLE_METHOD_FOUND, METHOD_NOT_PAUSABLE
     */

    static boolean isNonPausableClass(String className) {
        return className == null || className.charAt(0) == '[' || 
           className.startsWith("java.") || className.startsWith("javax.");
    }
    
    static boolean isNonPausableMethod(String methodName) {
        return methodName.endsWith("init>");
    }

    
    public int getPausableStatus(String className, String methodName, String desc) {
        int ret = METHOD_NOT_FOUND_OR_PAUSABLE;
        // array methods (essentially methods deferred to Object (clone, wait etc)
        // and constructor methods are not pausable
        if (isNonPausableClass(className) || isNonPausableMethod(methodName)) {
            return METHOD_NOT_FOUND_OR_PAUSABLE; 
        }
        className = className.replace('/', '.');
        try {
            MethodMirror m = findPausableMethod(className, methodName, desc);
            if (m != null) {
                for (String ex : m.getExceptionTypes()) {
                    if (isNonPausableClass(ex)) continue;
                    ClassMirror c = classForName(ex);
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

    public ClassMirror classForName(String className) throws ClassMirrorNotFoundException {
        className = className.replace('/', '.');
        return mirrors.classForName(className);
    }

    public ClassMirror[] classForNames(String[] classNames) throws ClassMirrorNotFoundException {
        if (classNames == null) {
            return new ClassMirror[0];
        }
        ClassMirror[] ret = new ClassMirror[classNames.length];
        int i = 0;
        for (String cn : classNames) {
            ret[i++] = classForName(cn);
        }
        return ret;
    }

    private MethodMirror findPausableMethod(String className, String methodName, String desc)
            throws ClassMirrorNotFoundException {
        
        if (isNonPausableClass(className) || isNonPausableMethod(methodName)) 
            return null;

        ClassMirror cl = classForName(className);
        if (cl == null) return null;
        
        for (MethodMirror om : cl.getDeclaredMethods()) {
            if (om.getName().equals(methodName) && om.getMethodDescriptor().equals(desc)) {
                if (om.isBridge())
                    continue;
                return om;
            }
        }

        if (OBJECT.equals(cl))
            return null;

        MethodMirror m = findPausableMethod(cl.getSuperclass(), methodName, desc);
        if (m != null)
            return m;
        
        for (String ifname : cl.getInterfaces()) {
            if (isNonPausableClass(ifname)) continue;
            m = findPausableMethod(ifname, methodName, desc);
            if (m != null)
                return m;
        }
        return null;
    }

    public static String D_FIBER_ = Constants.D_FIBER + ")";

    @SuppressWarnings("unused")
    private static String statusToStr(int st) {
        switch (st) {
        case METHOD_NOT_FOUND_OR_PAUSABLE:
            return "not found or pausable";
        case PAUSABLE_METHOD_FOUND:
            return "pausable";
        case METHOD_NOT_PAUSABLE:
            return "not pausable";
        default:
            throw new AssertionError("Unknown status");
        }
    }

    static private final ThreadLocal<Detector> DETECTOR = new ThreadLocal<Detector>();

    public static Detector getDetector() {
        Detector d = DETECTOR.get();
        if (d == null)
            return Detector.DEFAULT;
        return d;
    }

    public static Detector setDetector(Detector d) {
        Detector res = DETECTOR.get();
        DETECTOR.set(d);
        return res;
    }

    public String commonSuperType(String oa, String ob) throws ClassMirrorNotFoundException {
        String a = toClassName(oa);
        String b = toClassName(ob);

        try {
            ClassMirror ca = classForName(a);
            ClassMirror cb = classForName(b);
            if (ca.isAssignableFrom(cb))
                return oa;
            if (cb.isAssignableFrom(ca))
                return ob;
            if (ca.isInterface() && cb.isInterface()) {
                return D_OBJECT; // This is what the java bytecode verifier does
            }
        } catch (ClassMirrorNotFoundException e) {
            // try to see if the below works...
        }

        ArrayList<String> sca = getSuperClasses(a);
        ArrayList<String> scb = getSuperClasses(b);
        int lasta = sca.size() - 1;
        int lastb = scb.size() - 1;
        do {
            if (sca.get(lasta).equals(scb.get(lastb))) {
                lasta--;
                lastb--;
            } else {
                break;
            }
        } while (lasta >= 0 && lastb >= 0);
        return toDesc(sca.get(lasta + 1));
    }

    final private static ArrayList<String> EMPTY_STRINGS = new ArrayList<String>(0);
    public ArrayList<String> getSuperClasses(String name) throws ClassMirrorNotFoundException {
        if (name == null) {
            return EMPTY_STRINGS;
        }
        ArrayList<String> ret = new ArrayList<String>(3);
        while (name != null) {
            ret.add(name);
            ClassMirror c = classForName(name);
            name = c.getSuperclass();
        }
        return ret;

    }

    private static String toDesc(String name) {
        return (name.equals(JAVA_LANG_OBJECT)) ? D_OBJECT : "L" + name.replace('.', '/') + ';';
    }

    private static String toClassName(String s) {
        return s.replace('/', '.').substring(1, s.length() - 1);
    }

    static String JAVA_LANG_OBJECT = "java.lang.Object";

}
