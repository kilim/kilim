package kilim.mirrors;

import java.lang.reflect.Method;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

class RuntimeClassMirrors extends Mirrors {

    ClassLoader classLoader;

    @Override
    public ClassMirror classForName(String className) throws ClassMirrorNotFoundException {
        try {
            return new RuntimeClassMirror(classLoader.loadClass(className));
        } catch (ClassNotFoundException e) {
            throw new ClassMirrorNotFoundException(e);
        }
    }

    @Override
    public ClassMirror mirror(Class<?> clazz) {
        if (clazz == null)
            return null;
        return new RuntimeClassMirror(clazz);
    }

    @Override
    public ClassMirror mirror(ClassNode classNode) {
        try {
            return classForName(classNode.name);
        } catch (ClassMirrorNotFoundException ignore) {}
        return null;
    }

    public RuntimeClassMirrors() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public RuntimeClassMirrors(ClassLoader cl) {
        this.classLoader = cl;
    }
}

class RuntimeMethodMirror implements MethodMirror {

    private final Method method;

    public RuntimeMethodMirror(Method method) {
        this.method = method;
    }

    public static MethodMirror[] forMethods(Method[] declaredMethods) {
        MethodMirror[] result = new MethodMirror[declaredMethods.length];
        for (int i = 0; i < declaredMethods.length; i++) {
            result[i] = new RuntimeMethodMirror(declaredMethods[i]);
        }
        return result;
    }

    public String getName() {
        return method.getName();
    }

    public ClassMirror[] getExceptionTypes() {
        ClassMirror[] ret = new ClassMirror[method.getExceptionTypes().length];
        int i = 0;
        for (Class<?> excl : method.getExceptionTypes()) {
            ret[i++] = new RuntimeClassMirror(excl);
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
            return ((ClassMirror)obj).getName().equals(this.getName());
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return clazz.hashCode();
    }

    @Override
    public MethodMirror[] getDeclaredMethods() {
        return RuntimeMethodMirror.forMethods(clazz.getDeclaredMethods());
    }

    @Override
    public ClassMirror[] getInterfaces() {
        return forClasses(clazz.getInterfaces());
    }

    private static ClassMirror forClass(Class<?> clazz) {
        if (clazz == null) return null;
        return new RuntimeClassMirror(clazz);
    }

    private static ClassMirror[] forClasses(Class<?>[] classes) {
        ClassMirror[] result = new ClassMirror[classes.length];
        for (int i = 0; i < classes.length; i++) {
            result[i] = RuntimeClassMirror.forClass(classes[i]);
        }
        return result;
    }

    @Override
    public ClassMirror getSuperclass() {
        return forClass(clazz.getSuperclass());
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