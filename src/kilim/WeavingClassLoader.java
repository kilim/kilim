package kilim;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import kilim.analysis.ClassInfo;
import kilim.analysis.FileLister;
import kilim.tools.Weaver;

/**
 * Classloader that loads classes from the classpath spec given by the system property
 * "kilim.class.path" and weaves them dynamically.
 */
public class WeavingClassLoader extends KilimClassLoader {
    public static final String KILIM_CLASSPATH = "kilim.class.path";
    /**
     * List of paths in kilim.class.path
     */
    ArrayList<FileLister> fileContainers;
    /**
     * Weaver instance. There is a mutually recursive dependency between the weaver and
     * this class loader. See {@link #findClass(String)}  
     */
    Weaver weaver;

    public WeavingClassLoader(ClassLoader parent) {
        super(parent);
        String classPath = System.getProperty(KILIM_CLASSPATH, "");
        String[] classPaths = classPath.split(":");
        fileContainers = new ArrayList<FileLister>(classPaths.length);
        for (String name : classPaths) {
            name = name.trim();
            if (name.equals(""))
                continue;
            try {
                fileContainers.add(new FileLister(name));
            } catch (IOException ioe) {
                // System.err.println( "'" + name + "' does not exist. See property " +
                // KILIM_CLASSPATH);
            }
        }
        weaver = new Weaver(this); // mutually recursive dependency.
    }

    
    /**
     * Check if class file exists in kilim.class.path. 
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> ret = null;
        for (FileLister container : fileContainers) {
            try {
                String classFileName = name.replace('.', File.separatorChar) + ".class"; 
                FileLister.Entry fe = container.open(classFileName);
                if (fe == null) continue;
                byte[] code = readFully(fe);
                List<ClassInfo> cis = weaver.weave(new ClassInfo(name, code));

                for (ClassInfo ci : cis) {
                    if (findLoadedClass(ci.className) != null)
                        continue;
                    Class<?> c = super.defineClass(ci.className, ci.bytes, 0, ci.bytes.length);
                    if (ci.className.equals(name)) {
                        ret = c;
                    } else {
                        // extra classes produced by the weaver. resolve them right away
                        // That way, when the given class name is resolved, it'll find its
                        // kilim related state object classes right away.
                        if (ci.className.startsWith("kilim.S")) {
                            super.resolveClass(c);
                        }
                    }
                }
                if (ret == null) {
                    // code exists, but didn't need to be woven
                    ret = super.defineClass(name, code, 0, code.length);
                }
            } catch (IOException ignore) {
                System.err.println(ignore.getMessage());
            }
        }
        if (ret == null) {
            throw new ClassNotFoundException(name);
        }
        return ret;
    }

    private static byte[] readFully(FileLister.Entry fe) throws IOException {
        DataInputStream in = new DataInputStream(fe.getInputStream());
        byte[] contents = new byte[(int)fe.getSize()];
        in.readFully(contents);
        in.close();
        return contents;
    }
}
