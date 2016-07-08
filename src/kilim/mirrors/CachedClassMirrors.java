// copyright 2016 seth lytle, 2014 sriram srinivasan
package kilim.mirrors;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import kilim.KilimClassLoader;
import kilim.WeavingClassLoader;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import kilim.mirrors.RuntimeClassMirrors.RuntimeClassMirror;
import kilim.mirrors.RuntimeClassMirrors.RuntimeMethodMirror;


/**
 * CachedClassMirrors caches information about a set of classes that are loaded through byte arrays, and which 
 * are not already loaded by the classloader
 **/

public class CachedClassMirrors implements Mirrors {
    final static String[] EMPTY_SET = new String[0];
    
    ConcurrentHashMap<String,ClassMirror> cachedClasses = new ConcurrentHashMap<String, ClassMirror>();
    private final KilimClassLoader loader = new KilimClassLoader();

    public CachedClassMirrors() {
    }
    
    @Override
    public ClassMirror classForName(String className)
            throws ClassMirrorNotFoundException {
        // defer to loaded class objects first, then to cached class mirrors.
        ClassMirror ret = cachedClasses.get(className);
        if (ret != null) return ret;

        if (loader.isLoaded(className)) {
            try {
                Class clazz = loader.loadClass(className);
                return mirror(clazz);
            }
            catch (ClassNotFoundException ex) {
                throw new ClassMirrorNotFoundException(className,ex);
            }
        }
        
        byte [] code = WeavingClassLoader.findCode(getClass().getClassLoader(),className);
        if (code != null) return place(new CachedClassMirror(code));
        
        throw new ClassMirrorNotFoundException(className);
    }

    public ClassMirror mirror(byte[] bytecode) {
        return place(new CachedClassMirror(bytecode));
    }

    private ClassMirror place(ClassMirror r1) {
        ClassMirror r2 = cachedClasses.putIfAbsent(r1.getName(),r1);
        return r2==null ? r1:r2;
    }
    
    @Override
    public ClassMirror mirror(Class<?> clazz) {
        ClassMirror mirror = new CachedClassMirror(clazz);
        return place(mirror);
    }
    
    private static String map(String word) {
        return word==null ? null : word.replace("/",".");
    }
    private static String [] map(String [] words) {
        if (words==null) return words;
        String [] mod = new String[words.length];
        for (int ii = 0; ii < mod.length; ii++) mod[ii] = words[ii].replace("/",".");
        return mod;
    }

class CachedClassMirror extends ClassVisitor implements ClassMirror  {
    String name;
    boolean isInterface;
    MethodMirror[] declaredMethods;
    String[] interfaceNames;
    String superName;
    
    private List<CachedMethodMirror> tmpMethodList; //used only while processing bytecode. 
    private RuntimeClassMirror rm;
    
    public CachedClassMirror(byte []bytecode) {
        super(Opcodes.ASM5);
        ClassReader cr = new ClassReader(bytecode);
        cr.accept(this, /*flags*/0);
    }
    public CachedClassMirror(Class clazz) {
        super(Opcodes.ASM5);
        rm = new RuntimeClassMirror(clazz);
        name = rm.getName();
        isInterface = rm.isInterface();
        superName = rm.getSuperclass();
        // lazy evaluation for the rest
        interfaceNames = null;
        declaredMethods = null;
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
            String n1 = name, n2 = mirr.name;
            return n1.equals(n2) && mirr.isInterface == this.isInterface;
        }

        return false;
    }
    
    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public MethodMirror[] getDeclaredMethods() {
        if (declaredMethods != null)
            return declaredMethods;
        if (rm==null)
            return declaredMethods = new MethodMirror[0];
        Method[] jms = rm.clazz.getDeclaredMethods();
        declaredMethods = new MethodMirror[jms.length];
        for (int i = 0; i < jms.length; i++)
            declaredMethods[i] = new CachedMethodMirror(jms[i]);
        return declaredMethods;
    }

    @Override
    public String[] getInterfaces() throws ClassMirrorNotFoundException {
        if (interfaceNames==null && rm != null)
            interfaceNames = rm.getInterfaces();
        return interfaceNames;
    }

    @Override
    public String getSuperclass() throws ClassMirrorNotFoundException {
        return superName;
    }

    @Override
    public boolean isAssignableFrom(ClassMirror c) throws ClassMirrorNotFoundException {
        CachedClassMirrors mirrors = CachedClassMirrors.this;
        if (c==null) return false;
        if (this.equals(c)) return true;
        
        String sname = c.getSuperclass();
        ClassMirror supcl = sname==null ? null : mirrors.classForName(sname);
        if (isAssignableFrom(supcl)) return true;
        for (String icl: c.getInterfaces()) {
            supcl = mirrors.classForName(icl);
            if (isAssignableFrom(supcl))
                return true;
        }
        return false;
    }
    
    
    // ClassVisitor implementation
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        this.name = map(name);
        this.superName = map(superName);
        this.interfaceNames = interfaces == null ? CachedClassMirrors.EMPTY_SET : map(interfaces);
        this.isInterface = (access & Opcodes.ACC_INTERFACE) > 0;
        if (this.isInterface) this.superName = null;
    }

    
    
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        if (name.equals("<init>")) return null;
        if (name.equals("<clinit>")) return null;
        if (tmpMethodList == null) {
            tmpMethodList = new ArrayList<CachedMethodMirror>();
        }
        CachedMethodMirror mirror = new CachedMethodMirror(access, name, desc, map(exceptions));
        tmpMethodList.add(mirror);
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
}
    static class DummyAnnotationVisitor extends AnnotationVisitor {
        public DummyAnnotationVisitor() {
            super(Opcodes.ASM5);
        }
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
    private int    modifiers;
    private boolean isBridge;
    
    public CachedMethodMirror(int modifiers, String name, String desc, String[] exceptions) {
        this.modifiers = modifiers;
        this.name = name;
        this.desc = desc;
        this.exceptions = (exceptions == null) ? CachedClassMirrors.EMPTY_SET : exceptions;
        isBridge = (modifiers & Opcodes.ACC_BRIDGE) > 0;
    }
    public CachedMethodMirror(Method method) {
        RuntimeMethodMirror rm = new RuntimeMethodMirror(method);
        this.modifiers = rm.getModifiers();
        this.name = rm.getName();
        this.desc = rm.getMethodDescriptor();
        this.exceptions = rm.getExceptionTypes();
        isBridge = rm.isBridge();
    }

    public String getName() {
        return name;
    }
    
    public String[] getExceptionTypes() {
        return exceptions;
    }

    public String getMethodDescriptor() {
        return desc;
    }

    public boolean isBridge() {
        return isBridge;
    }

    public int getModifiers() {
        return modifiers;
    }
}


