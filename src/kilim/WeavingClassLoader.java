// copyright 2016 seth lytle, 2014 sriram srinivasan
package kilim;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import kilim.analysis.ClassInfo;
import kilim.analysis.FileLister;
import kilim.tools.Weaver;
import kilim.tools.WeaverBase;

/**
 * Classloader that loads classes from the classpath spec given by the system property
 * "kilim.class.path" and weaves them dynamically.
 */
public class WeavingClassLoader extends KilimClassLoader {
    public static final String KILIM_CLASSPATH = "kilim.class.path";
    WeaverBase weaver;
    
    /**
        load the kilim classes (except the parts that the WCL interfaces with)
        definitively to isolate the resulting classes from the application instances of the same classes
    */
    private static class ProxyLoader extends URLClassLoader {
        ProxyLoader(URL [] urls) { super(urls); }

        
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> c = null;
            boolean nope = name.equals(ClassInfo.class.getName())
                    || name.equals(WeaverBase.class.getName());
            if (nope || !name.startsWith("kilim."))
                return super.loadClass(name,resolve);
            synchronized (getClassLoadingLock(name)) {
                c = findLoadedClass(name);
                if (c==null)
                    try                  { c = findClassLocal( name ); }
                    catch (Exception ex) { super.loadClass( name ); }
            }
            if (resolve) resolveClass(c);
            return c;
        }


        public Class<?> findClassLocal(String name) throws ClassNotFoundException {
            byte [] code = findCode(this,name);
            Class c = defineClass(name,code,0,code.length);
            return c;
        }
    }
    ClassLoader proxy;

    public static byte [] readFully(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int num;
        byte[] data = new byte[1 << 12];
        while ((num = is.read( data, 0, data.length )) != -1)
            buffer.write(data, 0, num);
        buffer.flush();
        return buffer.toByteArray();
    }

    ClassLoader pcl;
    
    public WeavingClassLoader() {
        if (Weaver.dbg) Weaver.outputDir = "z1";
        String classPath = System.getProperty(KILIM_CLASSPATH, "");
        String[] classPaths = classPath.split(":");
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
        proxy = true ? new ProxyLoader(paths) : new URLClassLoader(paths);

        boolean useProxy = paths.length > 0;
        pcl = useProxy ? proxy : getClass().getClassLoader();
        try {
            if (useProxy)
                weaver = (WeaverBase) proxy.loadClass("kilim.tools.Weaver").newInstance();
            else weaver = new Weaver();
        }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    public Pattern skip = Pattern.compile( "java.*|sun.*" );

    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class klass;
        if (skip.matcher( name ).matches())
            klass = pcl.loadClass(name);
        else synchronized (getClassLoadingLock(name)) {
            klass = findLoadedClass(name);
            if (klass==null)
                try                  { klass = findClass( name ); }
                catch (Exception ex) { pcl.loadClass( name ); }
        }
        if (resolve) resolveClass( klass );
        return klass;
    }
    
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> ret = null;
        byte [] code = findCode(pcl,name);
        if (code==null)
            throw new ClassNotFoundException();
        InputStream is = new ByteArrayInputStream(code);
        List<ClassInfo> cis;
        try {
            cis = weaver.weave(is);
        }
        catch (IOException ex) { throw new ClassNotFoundException();  }

        for (ClassInfo ci : cis) {
            if (findLoadedClass(ci.className)==null) {
                Class<?> c = define(ci.className, ci.bytes);
                if      (ci.className.equals(name))          ret = c;
                else if (ci.className.startsWith("kilim.S")) resolveClass(c);
            }
        }

        if (ret==null) ret = findLoadedClass(name);
        if (ret==null) ret = define(name, code);
        return ret;
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
    
    public Class<?> define(String name,byte [] code) throws ClassNotFoundException {
        return defineClass(name, code, 0, code.length, get(name));
    }

    public static String map(String name) { return name.replace('.', File.separatorChar) + ".class"; }
    
    public static byte [] findCode(ClassLoader loader,String name) {
        String cname = map(name);
        InputStream is = loader.getResourceAsStream( cname );
        if (is==null) is = ClassLoader.getSystemResourceAsStream( cname );
        if (is != null)
            try { return readFully(is); } catch (Exception ex) {}
        return null;
    }
    public URL url(String name) {
        String cname = map(name);
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
