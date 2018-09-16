// copyright 2016 nqzero, 2014 sriram srinivasan - offered under the terms of the MIT License

package kilim;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;

import kilim.analysis.ClassInfo;
import kilim.analysis.ClassWeaver;
import kilim.analysis.FileLister;
import kilim.analysis.KilimContext;
import kilim.mirrors.CachedClassMirrors;
import kilim.tools.Agent;
import kilim.tools.Weaver;

// TODO: this and related real-time-weaving classes could be moved to the analysis package
//       allowing the ant kilim-runtime.jar (see build.xml) to be smaller

/**
 * Classloader that loads classes from the classpath spec given by the system property
 * "kilim.class.path" and weaves them dynamically.
 */
public class WeavingClassLoader extends KilimClassLoader {
    public static final String KILIM_CLASSPATH = "kilim.class.path";
    Weaver weaver;
    
    URLClassLoader proxy;

    public static byte [] readFully(InputStream is) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int num;
            byte[] data = new byte[1 << 12];
            while ((num = is.read( data, 0, data.length )) != -1)
                buffer.write(data, 0, num);
            buffer.flush();
            return buffer.toByteArray();
        }
        catch (IOException ex) { return null; }
    }

    ClassLoader pcl;
    static ClassLoader platform;
    static {
        ClassLoader last, cl = WeavingClassLoader.class.getClassLoader();
        for (last = cl; cl != null; cl = cl.getParent())
            last = cl;
        platform = last;
    }

    public static URL [] getURLs(String [] classPaths) {
        ArrayList<URL> urls = new ArrayList<URL>();
        for (String name : classPaths) {
            name = name.trim();
            if (name.equals("")) continue;
            try { urls.add(new File(name).toURI().toURL()); }
            catch (IOException ioe) {
                // System.err.println( "'" + name + "' does not exist. See property " +
                // KILIM_CLASSPATH);
            }
        }

        URL [] paths = urls.toArray(new URL[0]);
        return paths;
    }

    
    public WeavingClassLoader() {
        this(null,getProxy());
    }
    private static URLClassLoader getProxy() {
        String classPath = System.getProperty(KILIM_CLASSPATH, "");
        String[] classPaths = classPath.split(":");
        URL [] paths = getURLs(classPaths);
        return paths.length > 0 ? new URLClassLoader(paths) : null;
    }
    public WeavingClassLoader(ClassLoader loader,URLClassLoader $proxy) {
        proxy = $proxy;
        pcl = proxy != null
                ? proxy
                : loader != null ? loader:getClass().getClassLoader();

        CachedClassMirrors ccm = new CachedClassMirrors(pcl);
        KilimContext kc = new KilimContext(ccm);
        weaver = new Weaver(kc);
    }
    /** run static method className.method(args) using reflection and this WeavingClassLoader */
    public void run(String className,String method,String ... args) throws Exception {
        Class<?> mainClass = loadClass(className);
        Method mainMethod = mainClass.getMethod(method, new Class[]{String[].class});
        mainMethod.invoke(null,new Object[] {args});
    }

    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class klass = null;
        try { klass = platform.loadClass(name); } catch (ClassNotFoundException ex) {}
        if (klass==null)
            klass = findLoadedClass(name);
        if (klass==null) synchronized (this) {
            klass = findLoadedClass(name);
            if (klass==null)
                klass = findClass( name );
        }
        if (resolve) resolveClass( klass );
        return klass;
    }
    private boolean proxy(String cname) { 
        return proxy != null && proxy.findResource(cname) == null;
    }
    
    private Class defineAll(String name,ClassWeaver cw) {
        Class ret = null;
        for (ClassInfo ci : cw.getClassInfos()) {
            if (findLoadedClass(ci.className)==null) {
                Class<?> c = define(ci.className, ci.bytes);
                if      (ci.className.equals(name))          ret = c;
                else if (ci.className.startsWith("kilim.S")) resolveClass(c);
            }
        }
        return ret;
    }

    public static InputStream getByteStream(ClassLoader cl,String name,String cname) {
        InputStream is = cl.getResourceAsStream( cname );
        if (is==null) is = ClassLoader.getSystemResourceAsStream( cname );
        if (is==null & Agent.map != null) {
            // force the nominal class loader to load the bytecode so that the agent sees it
            try { cl.loadClass(name); }
            catch (Exception ex) {}
            byte [] bytes = Agent.map.get(cname);
            if (bytes != null) is = new ByteArrayInputStream(bytes);
        }
        return is;
    }
    
    /**
     * load the bytecode for a class of a given name from the classpath and weave it
     * @param name the fully qualified class name
     * @return the weaver
     */    
    public ClassWeaver weaveClass(String name) {
        String cname = makeResourceName(name);
        InputStream is = getByteStream(pcl,name,cname);
        ClassWeaver cw = weaver.weave(is);
        return cw;
    }
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String cname = makeResourceName(name);
        
        InputStream is = getByteStream(pcl,name,cname);
        ClassWeaver cw;
        byte [] code;

        if (is==null) {}
        else if (proxy(cname)) {
            if ((code=readFully(is)) != null)
                return define(name,code);
        }
        else if ((cw = weaver.weave(is)) != null) {
            Class<?> ret = defineAll(name,cw);
            return ret==null ? define(name, cw.classFlow.code) : ret;
        }
        throw new ClassNotFoundException();
    }

    private final HashMap<URL, ProtectionDomain> cache = new HashMap<URL, ProtectionDomain>();
    private ProtectionDomain get(String name) {
        URL url = url(name);
        if (url==null) return null;

        ProtectionDomain pd = null;
        synchronized (cache) {
            pd = cache.get(url);
            if (pd == null) {
                CodeSource cs = new CodeSource(url,(CodeSigner []) null);
                pd = new ProtectionDomain(cs, null, this, null);
                cache.put(url, pd);
            }
        }
        return pd;
    }
    
    public Class<?> define(String name,byte [] code) {
        CachedClassMirrors.ClassMirror cm = null;
        return defineClass(name, code, 0, code.length, get(name));
    }

    /**
     * convert a fully qualified class name to a resource name. Note: the Class and ClassLoader
     * javadocs don't explicitly specify the string formats used for the various methods, so
     * this conversion is potentially fragile
     * @param name as returned by Class.getName
     * @return the name in a format suitable for use with the various ClassLoader.getResource methods
     */
    // https://docs.oracle.com/javase/8/docs/technotes/guides/lang/resources.html#res_names
    public static String makeResourceName(String name) { return name.replace('.','/') + ".class"; }
    
    /** 
     * read bytecode for the named class from a source classloader
     * @param loader the classloader to get the bytecode from, or null for the current classloader
     * @param name the internal name for the class as would be passed to loadClass
     * @return a new instance, or null if the bytecode is not found
     */
    public static byte [] findCode(ClassLoader loader,String name) {
        if (loader==null) loader = WeavingClassLoader.class.getClassLoader();
        String cname = makeResourceName(name);
        InputStream is = getByteStream(loader,name,cname);
        if (is != null)
            return readFully(is);
        return null;
    }
    public URL url(String name) {
        String cname = makeResourceName(name);
        URL url = pcl.getResource( cname ), ret = null;
        if (url==null) url = ClassLoader.getSystemResource( cname );
        if (url==null) return null;
        String path = url.getPath();

        boolean matches = path.endsWith(cname);
        assert matches : "url code source doesn't match expectation: " + name + ", " + path;
        if (! matches) return null;
        
        try {
            ret = new URL(url,path.replace(cname,""));
        }
        catch (Exception ex) {}
        return ret;
    }


    
    public static byte[] readFully(FileLister.Entry fe) throws IOException {
        DataInputStream in = new DataInputStream(fe.getInputStream());
        byte[] contents = new byte[(int)fe.getSize()];
        in.readFully(contents);
        in.close();
        return contents;
    }

}
