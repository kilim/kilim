// copyright 2016 seth lytle, 2014 sriram srinivasan
package kilim;

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
import java.util.regex.Pattern;

import kilim.analysis.ClassInfo;
import kilim.analysis.ClassWeaver;
import kilim.analysis.FileLister;
import kilim.analysis.KilimContext;
import kilim.mirrors.CachedClassMirrors;
import kilim.tools.Weaver;

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

    boolean useProxy = false;
    ClassLoader pcl;

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
        if (Weaver.dbg) Weaver.outputDir = "z1";
        String classPath = System.getProperty(KILIM_CLASSPATH, "");
        String[] classPaths = classPath.split(":");

        URL [] paths = getURLs(classPaths);

        ClassLoader current = getClass().getClassLoader();
        useProxy = paths.length > 0;
        proxy = useProxy ? new URLClassLoader(paths) : null;
        pcl = useProxy ? proxy : current;

        CachedClassMirrors ccm = new CachedClassMirrors(this,pcl);
        KilimContext kc = new KilimContext(ccm);
        weaver = new Weaver(kc);
    }

    public Pattern skip = Pattern.compile( "java.*|sun.*" );

    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class klass;
        if (skip.matcher( name ).matches())
            klass = pcl.loadClass(name);
        else synchronized (this) {
            klass = findLoadedClass(name);
            if (klass==null)
                klass = findClass( name );
        }
        if (resolve) resolveClass( klass );
        return klass;
    }
    private boolean skip(String cname) { 
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
    
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String cname = map(name);
        
        InputStream is = pcl.getResourceAsStream( cname );
        if (is==null) is = ClassLoader.getSystemResourceAsStream( cname );
        ClassWeaver cw;
        byte [] code;

        if (is==null) {}
        else if (skip(cname)) {
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

    public static String map(String name) { return name.replace('.', File.separatorChar) + ".class"; }
    
    public static byte [] findCode(ClassLoader loader,String name) {
        String cname = map(name);
        InputStream is = loader.getResourceAsStream( cname );
        if (is==null) is = ClassLoader.getSystemResourceAsStream( cname );
        if (is != null)
            return readFully(is);
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
