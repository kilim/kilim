package kilim.mirrors;

import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class CachedClassMirrors extends Mirrors {

    Mirrors delegate = RuntimeClassMirrors.getRuntimeMirrors();
    ConcurrentHashMap<String,ClassMirror> cachedClasses = new ConcurrentHashMap<String, ClassMirror>();

    @Override
    public ClassMirror classForName(String className)
            throws ClassMirrorNotFoundException {
        // defer to loaded class objects first, then to cached class mirrors.
        ClassMirror ret = null;
        try {
            ret = delegate.classForName(className);
        } catch (ClassMirrorNotFoundException ignore) {}
        if (ret == null) {
            ret = cachedClasses.get(className);
        }
        if (ret == null) {
            throw new ClassMirrorNotFoundException(className);
        }
        return ret;
    }

    @Override
    public ClassMirror mirror(Class<?> clazz) {
        // param is already a class; use the get the appropriate runtime mirror
        return delegate.mirror(clazz);
    }
    
    @Override
    public ClassMirror mirror(ClassNode classNode) {
        // if it is loaded by the classLoader already, we will
        // not load the classNode, even if the bytes are different
        ClassMirror  ret = null;
        try {
            ret = delegate.classForName(classNode.name);
        } catch (ClassMirrorNotFoundException ignore) {
            ret = new CachedClassMirror(classNode);
            this.cachedClasses.put(classNode.name, ret);
        }
        return ret;
    }
}

class CachedMethodMirror implements MethodMirror {

    private String[] exceptions;
    private String desc;
    private String name;
    private boolean isBridge;
    
    public CachedMethodMirror(MethodNode method) {
        this.name = method.name;
        this.desc = method.desc;
        this.exceptions = new String[method.exceptions.size()];
        int i = 0;
        for (Object e: method.exceptions) {
            this.exceptions[i++] = (String) e;
        }
        isBridge = (method.access & Opcodes.ACC_BRIDGE) > 0;
    }
    
    public String getName() {
        return name;
    }
    
    public ClassMirror[] getExceptionTypes() throws ClassMirrorNotFoundException {
        Detector d = Detector.getDetector();
        return d.classForNames(exceptions);
    }

    public String getMethodDescriptor() {
        return desc;
    }

    public boolean isBridge() {
        return isBridge;
    }
}

class CachedClassMirror extends ClassMirror {

    final String name;
    final boolean isInterface;
    final MethodMirror[] declaredMethods;
    final String[] interfaceNames;
    final String superName;
    
    
    public CachedClassMirror(ClassNode classNode) {
        name = classNode.name;
        superName = classNode.superName;
        isInterface = (classNode.access & Opcodes.ACC_INTERFACE) > 0;
        this.declaredMethods = new MethodMirror[classNode.methods.size()];
        int i = 0;
        for (Object omn: classNode.methods) {
            this.declaredMethods[i++] = new CachedMethodMirror((MethodNode)omn);
        }
        i = 0;
        interfaceNames = new String[classNode.interfaces.size()];
    }

    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public boolean isInterface() {
        return isInterface;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CachedClassMirror) {
            CachedClassMirror mirr = (CachedClassMirror) obj;
            return mirr.name == this.name && mirr.isInterface == this.isInterface;
        }

        return false;
    }
    
    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public MethodMirror[] getDeclaredMethods() {
        return declaredMethods;
    }

    @Override
    public ClassMirror[] getInterfaces() throws ClassMirrorNotFoundException {
        return Detector.getDetector().classForNames(interfaceNames);
    }

    @Override
    public ClassMirror getSuperclass() throws ClassMirrorNotFoundException {
        return Detector.getDetector().classForName(this.superName);
    }

    @Override
    public boolean isAssignableFrom(ClassMirror c) throws ClassMirrorNotFoundException {
        if (this.equals(c)) return true;
        
        if (isAssignableFrom(c.getSuperclass())) return true;
        for (ClassMirror icl: c.getInterfaces()) {
            if (isAssignableFrom(icl))
                return true;
        }
        return false;
    }
}
