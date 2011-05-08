package kilim.mirrors;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import kilim.KilimClassLoader;

import org.objectweb.asm.Type;

/**
 * This class provides the Mirrors facade over a set of Class objects
 * @see Mirrors 
 */

class RuntimeClassMirrors extends Mirrors {
    // Weakly cache the mirror objects.
    Map<String, RuntimeClassMirror> cachedClasses = Collections
            .synchronizedMap(new WeakHashMap<String, RuntimeClassMirror>());

    public final KilimClassLoader classLoader;

    public RuntimeClassMirrors() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public RuntimeClassMirrors(ClassLoader cl) {
        if (!(cl instanceof KilimClassLoader)) {
            cl = new KilimClassLoader(cl);
        }
        this.classLoader = (KilimClassLoader) cl;
    }

    @Override
    public ClassMirror classForName(String className) throws ClassMirrorNotFoundException {
        try {
            RuntimeClassMirror ret = cachedClasses.get(className);
            if (ret == null) {
                ret = make(classLoader.loadClass(className));
            }
            return ret;
        } catch (ClassNotFoundException e) {
            throw new ClassMirrorNotFoundException(e);
        }
    }

    @Override
    public ClassMirror mirror(Class<?> clazz) {
        if (clazz == null)
            return null;
        return make(clazz);
    }

    @Override
    public ClassMirror mirror(String className, byte[] bytecode) {
        try {
            return classForName(className);
        } catch (ClassMirrorNotFoundException ignore) {}
        return null;
    }

    /**
     * Like classForName, but only if the class is already loaded. This does not force loading of a
     * class.
     * 
     * @param className
     * @return null if className not loaded, else a RuntimeClassMirror to represent the loaded
     *         class.
     */
    public ClassMirror loadedClassForName(String className) {
        Class<?> c = classLoader.getLoadedClass(className);
        return (c == null) ? null : make(c);
    }

    public Class<?> getLoadedClass(String className) {
        return classLoader.getLoadedClass(className);
    }

    public boolean isLoaded(String className) {
        return classLoader.isLoaded(className);
    }

    private RuntimeClassMirror make(Class<?> c) {
        if (c == null) {
            throw new NullPointerException();
        }
        RuntimeClassMirror ret = new RuntimeClassMirror(c);
        cachedClasses.put(c.getName(), ret);
        return ret;
    }
}

class RuntimeMethodMirror implements MethodMirror {

    private final Method method;

    public RuntimeMethodMirror(Method method) {
        this.method = method;
    }

    public String getName() {
        return method.getName();
    }

    public String[] getExceptionTypes() {
        String[] ret = new String[method.getExceptionTypes().length];
        int i = 0;
        for (Class<?> excl : method.getExceptionTypes()) {
            ret[i++] = excl.getName();
        }
        return ret;
    }

    public String getMethodDescriptor() {
        return Type.getMethodDescriptor(method);
    }

    public boolean isBridge() {
        return method.isBridge();
    }
}

class RuntimeClassMirror extends ClassMirror {

    private final Class<?> clazz;
    private MethodMirror[] methods; 
    
    public RuntimeClassMirror(Class<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public String getName() {
        return clazz.getName();
    }

    @Override
    public boolean isInterface() {
        return clazz.isInterface();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ClassMirror) {
            return ((ClassMirror) obj).getName().equals(this.getName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return clazz.hashCode();
    }

    @Override
    public MethodMirror[] getDeclaredMethods() {
        if (methods == null) {
           Method[] declaredMethods = clazz.getDeclaredMethods();
           methods = new MethodMirror[declaredMethods.length];
           for (int i = 0; i < declaredMethods.length; i++) {
               methods[i] = new RuntimeMethodMirror(declaredMethods[i]);
           }
        }
        return methods;
    }

    @Override
    public String[] getInterfaces() {
        Class<?>[] ifs = clazz.getInterfaces(); 
        String[] result = new String[ifs.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = ifs[i].getName();
        }
        return result;
    }

    @Override
    public String getSuperclass() {
        Class<?> supcl = clazz.getSuperclass();
        return supcl != null ? supcl.getName() : null;
    }

    @Override
    public boolean isAssignableFrom(ClassMirror c) {
        if (c instanceof RuntimeClassMirror) {
            RuntimeClassMirror cc = (RuntimeClassMirror) c;
            return clazz.isAssignableFrom(cc.clazz);
        } else {
            return false;
        }
    }

}