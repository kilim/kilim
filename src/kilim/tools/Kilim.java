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
        new WeavingClassLoader().run(className,method,args);
    }

    public static class Config {
        boolean check;
        Object example;
        int offset = 1;
        WeavingClassLoader.Excludable exclude;

        public Config(boolean check,Object example,WeavingClassLoader.Excludable exclude) {
            this.check = check;
            this.example = example;
            this.exclude = exclude;
        }
        Config offset(int offset) { this.offset = offset; return this; }
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
        return trampoline(new Config(check,null,null).offset(2),args);
    }
    /**
     * run the calling (static) method in the WeavingClassLoader context
     * using the classloader that was used to load example
     * intended to be used from main
     * @param example an object or a Class that has been loaded by the desired classloader
     * @param check if the class is already woven then just return
     * @param args to be passed to the target method
     * @return true if the trampoline was called and returned
     * @throws RuntimeException (wrapping for checked) Exceptions from the method call
     * @throws SecurityException cascaded from Class.getField
     */
    public static boolean trampoline(Object example,boolean check,String...args) {
        return trampoline(new Config(check,example,null).offset(2),args);
    }
    
    public static boolean trampoline(Config config,String...args) {
        ClassLoader cl = Kilim.class.getClassLoader();
        if (cl.getClass().getName().equals(WeavingClassLoader.class.getName())) return false;
        Object example = config.example;
        int offset = config.offset;
        StackTraceElement ste = new Exception().getStackTrace()[offset];
        boolean simple = example==null;

        try {
            if (config.check) {
                try {
                    Class klass = cl.loadClass(ste.getClassName());
                    if (isWoven(klass))
                        return false;
                }
                catch (ClassNotFoundException ex) {}
            }
            Class klass = simple ? null
                    : example instanceof Class ? (Class) example : example.getClass();
            ClassLoader ecl = simple ? null : klass.getClassLoader();
            WeavingClassLoader loader = new WeavingClassLoader(ecl,null);
            if (config.exclude != null)
                loader.exclude(config.exclude);
            loader.run(ste.getClassName(), ste.getMethodName(), args);
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
