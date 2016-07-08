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

public class RuntimeClassMirrors implements Mirrors {
    // Weakly cache the mirror objects.
    Map<String, RuntimeClassMirror> cachedClasses = Collections
            .synchronizedMap(new WeakHashMap<String, RuntimeClassMirror>());

    private final KilimClassLoader classLoader;

    public RuntimeClassMirrors() {
        this.classLoader = new KilimClassLoader();
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
            throw new ClassMirrorNotFoundException(className, e);
        }
    }

    @Override
    public ClassMirror mirror(Class<?> clazz) {
        if (clazz == null)
            return null;
        return make(clazz);
    }

    public ClassMirror mirror(byte[] bytecode) {
        return null;
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

static class RuntimeMethodMirror implements MethodMirror {

    private final Method method;

    public RuntimeMethodMirror(Method method) {
        this.method = method;
    }

    public String getName() {
        return method.getName();
    }
    
    public int getModifiers() {
        return method.getModifiers();
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

static class RuntimeClassMirror implements ClassMirror {

    final Class<?> clazz;
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

}

