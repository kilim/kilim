/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.tools;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import kilim.KilimException;
import kilim.analysis.ClassInfo;
import kilim.analysis.ClassWeaver;
import kilim.analysis.FileLister;
import kilim.mirrors.CachedClassMirrors;
import kilim.mirrors.Detector;

/**
 * This class supports both command-line and run time weaving of Kilim bytecode. 
 */

public class Weaver {
    public static String outputDir = null;
    public static boolean verbose = true;
    public static Pattern excludePattern = null;
    static int err = 0;

    /**
     * <pre>
     * Usage: java kilim.tools.Weaver -d &lt;output directory&gt; {source classe, jar, directory ...}
     * </pre>
     * 
     * If directory names or jar files are given, all classes in that container are processed. It is
     * perfectly fine to specify the same directory for source and output like this: 
     * <pre>
     *    java kilim.tools.Weaver -d ./classes ./classes
     * </pre>
     * Ensure that all classes to be woven are in the classpath. The output directory does not have to be 
     * in the classpath during weaving.
     *   
     * @see #weave(List) for run-time weaving.
     */
    public static void main(String[] args) throws IOException {
        // System.out.println(System.getProperty("java.class.path"));

        Detector detector = Detector.DEFAULT;

        String currentName = null;
        for (String name : parseArgs(args)) {
            try {
                if (name.endsWith(".class")) {
                    if (exclude(name))
                        continue;
                    currentName = name;
                    weaveFile(name, new BufferedInputStream(new FileInputStream(name)), detector);
                } else if (name.endsWith(".jar")) {
                    for (FileLister.Entry fe : new FileLister(name)) {
                        currentName = fe.getFileName();
                        if (currentName.endsWith(".class")) {
                            currentName = currentName.substring(0, currentName.length() - 6)
                                    .replace('/', '.');
                            if (exclude(currentName))
                                continue;
                            weaveFile(currentName, fe.getInputStream(), detector);
                        }
                    }
                } else if (new File(name).isDirectory()) {
                    for (FileLister.Entry fe : new FileLister(name)) {
                        currentName = fe.getFileName();
                        if (currentName.endsWith(".class")) {
                            if (exclude(currentName))
                                continue;
                            weaveFile(currentName, fe.getInputStream(), detector);
                        }
                    }
                } else {
                    weaveClass(name, detector);
                }
            } catch (KilimException ke) {
                System.err.println("Error weaving " + currentName + ". " + ke.getMessage());
                // ke.printStackTrace();
                System.exit(1);
            } catch (IOException ioe) {
                System.err.println("Unable to find/process '" + currentName + "'");
                System.exit(1);
            } catch (Throwable t) {
                System.err.println("Error weaving " + currentName);
                t.printStackTrace();
                System.exit(1);
            }
        }
        System.exit(err);
    }

    static boolean exclude(String name) {
        return excludePattern == null ? false : excludePattern.matcher(name).find();
    }

    static void weaveFile(String name, InputStream is, Detector detector) throws IOException {
        try {
            ClassWeaver cw = new ClassWeaver(is, detector);
            cw.weave();
            writeClasses(cw);
        } catch (KilimException ke) {
            System.err.println("***** Error weaving " + name + ". " + ke.getMessage());
            // ke.printStackTrace();
            err = 1;
        } catch (RuntimeException re) {
            System.err.println("***** Error weaving " + name + ". " + re.getMessage());
            re.printStackTrace();
            err = 1;
        } catch (IOException ioe) {
            err = 1;
            System.err.println("***** Unable to find/process '" + name + "'\n" + ioe.getMessage());
        }
    }

    static void weaveClass(String name, Detector detector) {
        try {
            ClassWeaver cw = new ClassWeaver(name, detector);
            writeClasses(cw);
        } catch (KilimException ke) {
            err = 1;
            System.err.println("***** Error weaving " + name + ". " + ke.getMessage());
            // ke.printStackTrace();

        } catch (IOException ioe) {
            err = 1;
            System.err.println("***** Unable to find/process '" + name + "'\n" + ioe.getMessage());
        }
    }

    /** public only for testing purposes */
    public static void weaveClass2(String name, Detector detector) throws IOException {
        try {
            ClassWeaver cw = new ClassWeaver(name, detector);
            cw.weave();
            writeClasses(cw);
        } catch (KilimException ke) {
            err = 1;
            System.err.println("***** Error weaving " + name + ". " + ke.getMessage());
            // ke.printStackTrace();
            throw ke;

        } catch (IOException ioe) {
            err = 1;
            System.err.println("***** Unable to find/process '" + name + "'\n" + ioe.getMessage());
            throw ioe;
        }
    }

    static void writeClasses(ClassWeaver cw) throws IOException {
        List<ClassInfo> cis = cw.getClassInfos();
        if (cis.size() > 0) {
            for (ClassInfo ci : cis) {
                writeClass(ci);
            }
        }
    }

    static void writeClass(ClassInfo ci) throws IOException {
        String className = ci.className.replace('.', File.separatorChar);
        String dir = outputDir + File.separatorChar + getDirName(className);
        mkdir(dir);
        // Convert name to fully qualified file name
        className = outputDir + File.separatorChar + className + ".class";
        if (ci.className.startsWith("kilim.S_")) {
            // Check if we already have that file
            if (new File(className).exists())
                return;
        }
        FileOutputStream fos = new FileOutputStream(className);
        fos.write(ci.bytes);
        fos.close();
        if (verbose) {
            System.out.println("Wrote: " + className);
        }
    }

    static void mkdir(String dir) throws IOException {
        File f = new File(dir);
        if (!f.exists()) {
            if (!f.mkdirs()) {
                throw new IOException("Unable to create directory: " + dir);
            }
        }
    }

    static String getDirName(String className) {
        int end = className.lastIndexOf(File.separatorChar);
        return (end == -1) ? "" : className.substring(0, end);
    }

    static void help() {
        System.err.println("java kilim.tools.Weaver opts -d <outputDir> (class/directory/jar)+");
        System.err.println("   where opts are   -q : quiet");
        System.err.println("                    -x <regex> : exclude all classes matching regex");
        System.exit(1);
    }

    static ArrayList<String> parseArgs(String[] args) throws IOException {
        if (args.length == 0)
            help();

        ArrayList<String> ret = new ArrayList<String>(args.length);
        String regex = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-d")) {
                outputDir = args[++i];
            } else if (arg.equals("-q")) {
                verbose = false;
            } else if (arg.equals("-h")) {
                help();
            } else if (arg.equals("-x")) {
                regex = args[++i];
                excludePattern = Pattern.compile(regex);
            } else {
                ret.add(arg);
            }
        }
        if (outputDir == null) {
            System.err.println("Specify output directory with -d option");
            System.exit(1);
        }
        mkdir(outputDir);
        return ret;
    }

    private Detector detector;
    private CachedClassMirrors mirrors;

    public Weaver() {
        this(Thread.currentThread().getContextClassLoader());
    }
    
    public Weaver(ClassLoader cl) {
        mirrors = new CachedClassMirrors(cl);
        detector = new Detector(mirrors);
    }

    /**
     * See #weave(List<ClassInfo>)
     */
    public List<ClassInfo> weave(ClassInfo cl) throws KilimException {
        List<ClassInfo> ret = new ArrayList<ClassInfo>(1);
        ret.add(cl);
        ret = weave(ret);
        return ret;
    }

    /**
     * Analyzes the list of supplied classes and inserts Kilim-related bytecode if necessary. If a
     * supplied class is dependent upon another class X, it is the caller's responsibility to ensure
     * that X is either in the classpath, or loaded by the context classloader, or has been seen in
     * an earlier invocation of weave().  
     * 
     * Since weave() remembers method signatures from earlier invocations, the woven classes do not
     * have to be classloaded to help future invocations of weave. 
     * 
     * If two classes A and B are not in the classpath, and are mutually recursive, they can be woven
     * only if supplied in the same input list.
     *  
     * This method is thread safe.
     * 
     * @param classes A list of (className, byte[]) pairs. The first part is a fully qualified class
     *            name, and the second part is the bytecode for the class.
     * 
     * @return A list of (className, byte[]) pairs. Some of the classes may or may not have been
     *         modified, and new ones may be added.
     * 
     * @throws KilimException
     */
    public List<ClassInfo> weave(List<ClassInfo> classes) throws KilimException {
        // save the detector attached to this thread, if any. It will be restored
        // later.
        ArrayList<ClassInfo> ret = new ArrayList<ClassInfo>(classes.size());
        Detector origDetector = Detector.getDetector();
        Detector.setDetector(detector); // / set thread local detector.
        try {
            // First cache all the method signatures from the supplied classes to allow
            // the weaver to lookup method signatures from mutually recursive classes.
            for (ClassInfo cl : classes) {
                detector.mirrors.mirror(cl.className, cl.bytes);
            }

            // Now weave them individually
            for (ClassInfo cl : classes) {
                ClassWeaver cw = new ClassWeaver(cl.bytes, detector);
                cw.weave();
                ret.addAll(cw.getClassInfos()); // one class file can result in multiple classes
            }
            return ret;
        } finally {
            Detector.setDetector(origDetector);
        }
    }
}
