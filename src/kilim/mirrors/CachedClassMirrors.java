// copyright 2016 nqzero, 2014 sriram srinivasan - offered under the terms of the MIT License

package kilim.mirrors;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import kilim.WeavingClassLoader;

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

public class CachedClassMirrors {
    final static String[] EMPTY_SET = new String[0];
    
    ConcurrentHashMap<String,ClassMirror> cachedClasses = new ConcurrentHashMap<String, ClassMirror>();
    final ClassLoader source;

    public CachedClassMirrors() {
        source = getClass().getClassLoader();
    }
    public CachedClassMirrors(ClassLoader $source) {
        source = $source;
    }
    
    public ClassMirror classForName(String className) throws ClassMirrorNotFoundException {
        // defer to loaded class objects first, then to cached class mirrors.
        ClassMirror ret = cachedClasses.get(className);
        if (ret != null) return ret;

        // even if a class is loaded, we can't tell if it's resolved, so querying it might trigger
        // loading of other classes, so use asm for everything
        
        byte [] code = WeavingClassLoader.findCode(source,className);
        if (code != null) return mirror(code);
        
        throw new ClassMirrorNotFoundException(className);
    }

    public ClassMirror mirror(byte[] bytecode) {
        ClassMirror mirror = new ClassMirror(bytecode);
        return place(mirror);
    }

    private ClassMirror place(ClassMirror r1) {
        r1.mirrors = this;
        ClassMirror r2 = cachedClasses.putIfAbsent(r1.getName(),r1);
        return r2==null ? r1:r2;
    }

    /** get the major version of klass by loading the bytecode from source */
    public static int getVersion(ClassLoader source,Class klass) {
        String cname = WeavingClassLoader.makeResourceName(klass.getName());
        DataInputStream in = new DataInputStream(source.getResourceAsStream(cname));
        try {
            int magic = in.readInt();
            int minor = in.readUnsignedShort();
            int major = in.readUnsignedShort();
            in.close();
            return major;
        }
        catch (IOException ex) { throw new RuntimeException(ex); }
    }
    
    public ClassMirror mirror(Class<?> clazz) {
        try {
            return classForName(clazz.getName());
        }
        catch (ClassMirrorNotFoundException ex) {
            throw new AssertionError("class-based lookup should never fail",ex);
        }
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

    
    public static class ClassMirror {
        private String name;
        private boolean isInterface;
        private MethodMirror[] declaredMethods;
        private String[] interfaceNames;
        private String superName;
        private int version = 0;
        CachedClassMirrors mirrors;

        private List<MethodMirror> tmpMethodList; //used only while processing bytecode. 

        public ClassMirror(byte []bytecode) {
            ClassReader cr = new ClassReader(bytecode);
            Visitor visitor = new Visitor();
            cr.accept(visitor, /*flags*/0);
        }

        // used by DualMirror (external package) for testing the mirrors
        ClassMirror() {}

        public String getName() {
            return name;
        }

        public boolean isInterface() {
            return isInterface;
        }

        public boolean equals(Object obj) {
            if (obj instanceof ClassMirror) {
                ClassMirror mirr = (ClassMirror) obj;
                String n1 = name, n2 = mirr.getName();
                return n1.equals(n2) && mirr.isInterface() == this.isInterface;
            }
            return false;
        }

        public int hashCode() {
            return this.name.hashCode();
        }

        public MethodMirror[] getDeclaredMethods() {
            if (declaredMethods != null)
                return declaredMethods;
            return declaredMethods = new MethodMirror[0];
        }

        public String[] getInterfaces() throws ClassMirrorNotFoundException {
            return interfaceNames;
        }

        public String getSuperclass() throws ClassMirrorNotFoundException {
            return superName;
        }



        public int version() {
            return (version & 0x00FF);
        }

        public boolean isAssignableFrom(ClassMirror c) throws ClassMirrorNotFoundException {
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

        public class Visitor extends ClassVisitor {
            Visitor() {
                super(Opcodes.ASM7_EXPERIMENTAL);
            }

            // ClassVisitor implementation
            public void visit(int $version, int access, String $name, String signature, String $superName,
                    String[] $interfaces) {
                version = $version;
                name = map($name);
                superName = map($superName);
                interfaceNames = $interfaces == null ? CachedClassMirrors.EMPTY_SET : map($interfaces);
                isInterface = (access & Opcodes.ACC_INTERFACE) > 0;
                if (isInterface) superName = null;
            }



            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                    String[] exceptions) {
                if (name.equals("<init>")) return null;
                if (name.equals("<clinit>")) return null;
                if (tmpMethodList == null) {
                    tmpMethodList = new ArrayList<CachedClassMirrors.MethodMirror>();
                }
                MethodMirror mirror = new MethodMirror(access, name, desc, map(exceptions));
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

            public void visitNestMemberExperimental(String nestMember) {}
            public void visitNestHostExperimental(String nestHost) {}
            
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
    }
    static class DummyAnnotationVisitor extends AnnotationVisitor {
        public DummyAnnotationVisitor() {
            super(Opcodes.ASM7_EXPERIMENTAL);
        }
        static DummyAnnotationVisitor singleton = new DummyAnnotationVisitor();
        public void visit(String name, Object value) {}
        public AnnotationVisitor visitAnnotation(String name, String desc) {return this;}
        public AnnotationVisitor visitArray(String name) {return DummyAnnotationVisitor.singleton;}
        public void visitEnd() {}
        public void visitEnum(String name, String desc, String value) {}
    }


    public static class MethodMirror {

        private String[] exceptions;
        private String desc;
        private String name;
        private int    modifiers;
        private boolean isBridge;

        public MethodMirror(int modifiers, String name, String desc, String[] exceptions) {
            this.modifiers = modifiers;
            this.name = name;
            this.desc = desc;
            this.exceptions = (exceptions == null) ? CachedClassMirrors.EMPTY_SET : exceptions;
            isBridge = (modifiers & Opcodes.ACC_BRIDGE) > 0;
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
}



