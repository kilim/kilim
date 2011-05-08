package kilim.mirrors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


/**
 * CachedClassMirrors caches information about a set of classes that are loaded through byte arrays, and which 
 * are not already loaded by the classloader
 **/

public class CachedClassMirrors extends Mirrors {
    final static String[] EMPTY_SET = new String[0];
    
    final RuntimeClassMirrors delegate;
    ConcurrentHashMap<String,ClassMirror> cachedClasses = new ConcurrentHashMap<String, ClassMirror>();

    public CachedClassMirrors(ClassLoader cl) {
        delegate = new RuntimeClassMirrors(cl);
    }
    
    @Override
    public ClassMirror classForName(String className)
            throws ClassMirrorNotFoundException {
        // defer to loaded class objects first, then to cached class mirrors.
        ClassMirror ret = cachedClasses.get(className);

        if (ret == null) {
            ret = delegate.classForName(className);
        }
        if (ret == null) {
            throw new ClassMirrorNotFoundException(className);
        }
        return ret;
    }

    @Override
    public ClassMirror mirror(Class<?> clazz) {
        // param is already a class; use the delegate to get the appropriate runtime mirror
        return delegate.mirror(clazz);
    }
    
    @Override
    public ClassMirror mirror(String className, byte[] bytecode) {
        // if it is loaded by the classLoader already, we will
        // not load the classNode, even if the bytes are different
        ClassMirror  ret = null;
        if (!delegate.isLoaded(className)) {
            ret = new CachedClassMirror(bytecode);
            String name = ret.getName().replace('/', '.'); // Class.forName format
            this.cachedClasses.put(name, ret);
        }
        return ret;
    }
}

class CachedClassMirror extends ClassMirror implements ClassVisitor {

    String name;
    boolean isInterface;
    MethodMirror[] declaredMethods;
    String[] interfaceNames;
    String superName;
    
    private List<CachedMethodMirror> tmpMethodList; //used only while processing bytecode. 
    
    public CachedClassMirror(byte []bytecode) {
        ClassReader cr = new ClassReader(bytecode);
        cr.accept(this, true);
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
    public String[] getInterfaces() throws ClassMirrorNotFoundException {
        return interfaceNames;
    }

    @Override
    public String getSuperclass() throws ClassMirrorNotFoundException {
        return superName;
    }

    @Override
    public boolean isAssignableFrom(ClassMirror c) throws ClassMirrorNotFoundException {
        Detector d = Detector.getDetector();
        if (this.equals(c)) return true;
        
        ClassMirror supcl = d.classForName(c.getSuperclass());
        if (isAssignableFrom(supcl)) return true;
        for (String icl: c.getInterfaces()) {
            supcl = d.classForName(icl);
            if (isAssignableFrom(supcl))
                return true;
        }
        return false;
    }
    
    
    // ClassVisitor implementation
    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        this.name = name;
        this.superName = superName;
        this.interfaceNames = interfaces == null ? CachedClassMirrors.EMPTY_SET : interfaces;
        this.isInterface = (access & Opcodes.ACC_INTERFACE) > 0;
    }


    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        if (tmpMethodList == null) {
            tmpMethodList = new ArrayList<CachedMethodMirror>();
        }
        tmpMethodList.add(new CachedMethodMirror(access, name, desc, exceptions));
        return null; // null MethodVisitor to avoid examining the instructions.
    }
    
    public void visitEnd() {
        if (tmpMethodList != null) {
            declaredMethods = new MethodMirror[tmpMethodList.size()];
            int i = 0;
            for (MethodMirror mm: tmpMethodList) {
                declaredMethods[i++] = mm;
            }
            tmpMethodList = null;
        }
    }

    // Dummy methods
    
    public void visitSource(String source, String debug) {}
    public void visitOuterClass(String owner, String name, String desc) {}
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return DummyAnnotationVisitor.singleton;
    }
    public void visitAttribute(Attribute attr) {}
    public void visitInnerClass(String name, String outerName, String innerName, int access) {}
    public FieldVisitor visitField(int access, String name, String desc, String signature,
            Object value) {
        return null;
    }
    static class DummyAnnotationVisitor implements AnnotationVisitor {
        static DummyAnnotationVisitor singleton = new DummyAnnotationVisitor();
        public void visit(String name, Object value) {}
        public AnnotationVisitor visitAnnotation(String name, String desc) {return this;}
        public AnnotationVisitor visitArray(String name) {return DummyAnnotationVisitor.singleton;}
        public void visitEnd() {}
        public void visitEnum(String name, String desc, String value) {}
    }
}

class CachedMethodMirror implements MethodMirror {

    private String[] exceptions;
    private String desc;
    private String name;
    private boolean isBridge;
    
    public CachedMethodMirror(int access, String name, String desc, String[] exceptions) {
        this.name = name;
        this.desc = desc;
        this.exceptions = (exceptions == null) ? CachedClassMirrors.EMPTY_SET : exceptions;
        isBridge = (access & Opcodes.ACC_BRIDGE) > 0;
    }

    public String getName() {
        return name;
    }
    
    public String[] getExceptionTypes() throws ClassMirrorNotFoundException {
        return exceptions;
    }

    public String getMethodDescriptor() {
        return desc;
    }

    public boolean isBridge() {
        return isBridge;
    }
}


