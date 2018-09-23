
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.Map;
import java.util.TreeMap;
import jdk.jshell.execution.DirectExecutionControl;
import jdk.jshell.execution.LoaderDelegate;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionControl.ClassBytecodes;
import jdk.jshell.spi.ExecutionControl.ClassInstallException;
import jdk.jshell.spi.ExecutionControl.InternalException;
import jdk.jshell.spi.ExecutionControlProvider;
import jdk.jshell.spi.ExecutionEnv;
import jdk.jshell.tool.JavaShellToolBuilder;
import kilim.WeavingClassLoader;



public class Kshell {
    public static class Prov implements ExecutionControlProvider {
        public String name() { return "kilim"; }

        public ExecutionControl generate(ExecutionEnv arg0,Map<String,String> arg1) throws Throwable {
            return new Control(new Loader().new Delegate());
        }
    }

    public static class Control extends DirectExecutionControl {
        Loader.Delegate delegate;
        public Control(Loader.Delegate delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        public void close() {
            super.close();
            try {
                delegate.wcl.loadClass(kilim.Task.class.getName())
                        .getMethod("idledown", new Class[]{})
                        .invoke(null);
            }
            catch (Exception ex) {}
        }
    }
    
    public static class Loader extends URLClassLoader {
        TreeMap<String,byte[]> map = new TreeMap();

        Loader() {
            super(new URL[0],Kshell.class.getClassLoader());
        }

        public InputStream getResourceAsStream(String name) {
            byte [] bytes = map.get(name);
            if (bytes != null) return new ByteArrayInputStream(bytes);
            return super.getResourceAsStream(name);
        }

        public class Delegate implements LoaderDelegate {
            WeavingClassLoader wcl = new WeavingClassLoader(Loader.this,null);

            public void load(ClassBytecodes[] codes) throws ClassInstallException {
                for (int ii=0; ii < codes.length; ii++) {
                    String name = codes[ii].name();
                    String cname = WeavingClassLoader.makeResourceName(name);
                    map.put(cname,codes[ii].bytecodes());
                }
                boolean [] installed = new boolean[codes.length];
                for (int ii=0; ii < codes.length; ii++) {
                    Class klass;
                    try { klass = wcl.loadClass(codes[ii].name()); } catch (ClassNotFoundException ex) {
                        throw new ClassInstallException("",installed);
                    }
                    installed[ii] = true;
                }
            }

            public void classesRedefined(ClassBytecodes[] arg0) {}

            public void addToClasspath(String cp) throws InternalException {
                try {
                    for (String path : cp.split(File.pathSeparator))
                        addURL(new File(path).toURI().toURL());
                }
                catch (Exception ex) { throw new InternalException(ex.toString()); }
            }

            public Class<?> findClass(String name) throws ClassNotFoundException {
                return wcl.loadClass(name);
            }
        }
    }
    
    public static String buildClassPath(Class ... klasses) {
        String cp = "";
        for (Class klass : klasses) {
            String name = klass.getName().replace(".","/") + ".class";
            URL url = klass.getClassLoader().getResource(name);
            String full = null;
            try {
                URLConnection open = url.openConnection();
                if (open instanceof JarURLConnection) {
                    JarURLConnection juc = (JarURLConnection) open;
                    full = juc.getJarFile().getName();
                }
            }
            catch (Exception ex) {}

            if (full==null) try {
                    String file = url.toURI().getPath();
                    if (file.endsWith(name))
                        full = file.substring(0,file.lastIndexOf(name));
                }
                catch (Exception ex) {
                    throw new RuntimeException("unable to convert resource to filename: "
                            + url);
                }
            cp += (cp.isEmpty() ? "":":") + full;
        }
        return cp;
    }

    static String mergePaths(String ... classpaths) {
        String result = "";
        for (String cp : classpaths) {
            for (String path : cp.split(":")) {
                if (path.endsWith(".so")) continue;
                if (path.endsWith(".dll")) continue;
                result = result.isEmpty() ? path : result + ":" + path;
            }
        }
        return result;
    }

    
    public static void main(String[] args) throws Exception {
        String cpx = System.getProperty("java.class.path");
        String cp1 = kilim.tools.Javac.getClassPath(null,null).join();
        String cp2 = buildClassPath(kilim.Task.class);
        
        String cp = mergePaths(cpx,cp1,cp2);
        String [] config = new String[]{ "-execution", "kilim", "--class-path", cp };
        JavaShellToolBuilder.builder().run(args.length > 0 ? args:config);
        
        // workaround: jshell leaves lingering threads
        //   Preferences.timer
        //   SourceCodeAnalysisImpl.INDEXER
        //   https://bugs.openjdk.java.net/browse/JDK-8210767
        System.exit(0);
    }
}
