// copyright 2016 nqzero, 2014 sriram srinivasan - offered under the terms of the MIT License

package kilim.tools;

import java.lang.reflect.Method;
import kilim.Constants;

import kilim.WeavingClassLoader;



/**
 * runtime weaver
 * This class dynamically weaves kilim-related classes and runs "class". 
 * 
 * Invoke as java kilim.tools.Kilim  class args...
 * if -Dkilim.class.path="classDir1:classDir2:jar1.jar:..." is supplied then runtime weaving will be limited
 * to those paths
 * 
 * otherwise, everything on the classpath will be analyzed and woven if needed
 * 
 * a main method can also call the trampoline to trigger automatic weaving if needed
 */
public class Kilim {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
        }
        String className = args[0];
        args = processArgs(args);
        run(className,"main",args);
    }
    /** run static method className.method(args) using reflection and the WeavingClassLoader */
    public static void run(String className,String method,String ... args) throws Exception {
        WeavingClassLoader wcl = new WeavingClassLoader();
        Class<?> mainClass = wcl.loadClass(className);
        Method mainMethod = mainClass.getMethod(method, new Class[]{String[].class});
        mainMethod.invoke(null,new Object[] {args});
    }

    /**
     * run the calling (static) method in the WeavingClassLoader context
     * intended to be used from main
     * @param check if the class is already woven then just return
     * @param args to be passed to the target method
     * @return true if the trampoline was called and returned
     * @throws RuntimeException (wrapping for checked) Exceptions from the method call
     * @throws SecurityException cascaded from Class.getField
     */
    public static boolean trampoline(boolean check,String...args) {
        ClassLoader cl = Kilim.class.getClassLoader();
        if (cl.getClass().getName().equals(WeavingClassLoader.class.getName())) return false;
        StackTraceElement ste = new Exception().getStackTrace()[1];

        try {
            Class klass = cl.loadClass(ste.getClassName());
            if (check && isWoven(klass))
                return false;
            kilim.tools.Kilim.run(ste.getClassName(), ste.getMethodName(), args);
        }
        catch (RuntimeException ex) { throw ex; }
        catch (Exception ex) { throw new RuntimeException(ex); }
        return true;
    }

    /**
     * has klass been woven
     * @param klass the class to check
     * @return true if it has been woven
     * @throws SecurityException cascaded from Class.getField
     */
    public static boolean isWoven(Class klass) {
        try {
            klass.getField(Constants.WOVEN_FIELD);
            return true;
        }
        catch (NoSuchFieldException ex) { return false; }
    }

    private static void usage() {
        System.out.println("java [-Dkilim.class.path=...] kilim.tools.Kilim class [args ...]");
        System.exit(1);
    }

    private static String[] processArgs(String[] args) {
        String[] ret = new String[args.length-1];
        if (ret.length > 0) 
            System.arraycopy(args, 1, ret, 0, ret.length);
        return ret;
    }
}
