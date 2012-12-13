/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.tools;
import static kilim.analysis.Utils.dedent;
import static kilim.analysis.Utils.indent;
import static kilim.analysis.Utils.p;
import static kilim.analysis.Utils.pn;
import static kilim.analysis.Utils.resetIndentation;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import kilim.analysis.TypeDesc;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Equivalent to javap -c -l -private, but the output is in jasmin's format 
 * meant to be parseable by Asm.
 * @author sriram
 */
public class DumpClass implements Opcodes, ClassVisitor {
    
    static boolean lineNumbers = true;
    
    public static void main(String[] args) throws IOException {
        String name = args.length == 2 ? args[1] : args[0];
        //int flags = args.length == 2 ? 0: ClassReader.SKIP_DEBUG;
        boolean flags = false; // skipdebug
        
        if (name.endsWith(".jar")) {
            try {
                Enumeration<JarEntry> e = new JarFile(name).entries();
                while (e.hasMoreElements()) {
                    ZipEntry en = (ZipEntry) e.nextElement();
                    String n = en.getName();
                    if (!n.endsWith(".class")) continue;
                    n = n.substring(0, n.length() - 6).replace('/','.');
                    new DumpClass(n, flags);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            new DumpClass(name, flags);
        }
    }
    

    public DumpClass(InputStream is, boolean flags) throws IOException {
        ClassReader cr = new ClassReader(is);
        cr.accept(this, flags);
    }

    public DumpClass(String className, boolean flags) throws IOException {
        ClassReader cr;
        if (className.endsWith(".class")) {
            FileInputStream fis = new FileInputStream(className);
            cr = new ClassReader(fis);
        } else {
            cr = new ClassReader(className);
        }
        cr.accept(this, flags);
    }

    public void visit(int version, int access, String name, String signature, 
            String superName, String[] interfaces) 
    {
        p(".class "); 
        p(Modifier.toString(access));
        p(" ");
        pn(name);
        if (superName != null) 
            pn(".super " + superName);
        
        if (interfaces != null) {
            for (int i = 0; i < interfaces.length; i++) {
                p(".implements ");
                pn(interfaces[i]);
            }
        }
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        pn(".annotation " + (visible ? "visible " : "") + desc);
        pn(".end annotation");
        return new DummyAnnotationVisitor();
    }

    public void visitAttribute(Attribute attr) {}

    public void visitEnd() {}

    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        p(".field ");
        p(Modifier.toString(access));
        p(" ");
        p(name);
        p(" ");
        p(desc);
        if (value != null) {
            p(" = ");
            if (value instanceof String) {
                pn("\"" + value + "\"");
            } else {
                pn(value.toString());
            }
        } else {
            pn();
        }
        return null;
    }

    public void visitInnerClass(String name, String outerName, String innerName, int access) {
    }

    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        pn("");
        pn("; -------------------------------------------------------------");
        p(".method ");
        p(Modifier.toString(access));
        p(" ");
        p(name);
        pn(desc);
        pn("; signature = " + signature);
        pn("; -------------------------------------------------------------\n");
        if (exceptions != null) {
            for (int i = 0; i < exceptions.length; i++) {
                p(".throws ");
                pn(exceptions[i]);
            }
        }
        return new DumpMethodVisitor();
    }

    public void visitOuterClass(String owner, String name, String desc) {
    }

    public void visitSource(String source, String debug) {}
}

class DummyAnnotationVisitor implements AnnotationVisitor {
    public void visit(String name, Object value) {
//        System.out.println("visit: name = " + name + ", value = "  + value);
    }
    public AnnotationVisitor visitAnnotation(String name, String desc) {
//        System.out.println("visitAnnotation: name = " + name + ", desc = " + desc);
        return this;
    }

    public AnnotationVisitor visitArray(String name) {
//        System.out.println("visitArray: name = " + name);
        return this;
    }

    public void visitEnd() {
//        System.out.println("visitEnd");
    }

    public void visitEnum(String name, String desc, String value) {
//        System.out.println("visitEnum: " + name + ", desc = "  + desc + ", value = " + value);
    }
}

class DumpMethodVisitor implements Opcodes, MethodVisitor {

    public DumpMethodVisitor() {
    }

    static String[] os = {
        "nop","aconst_null","iconst_m1","iconst_0","iconst_1","iconst_2",
        "iconst_3","iconst_4","iconst_5","lconst_0","lconst_1","fconst_0",
        "fconst_1","fconst_2","dconst_0","dconst_1","bipush","sipush",
        "ldc","ldc_w","ldc_w","iload","lload","fload","dload","aload",
        "iload_0","iload_1","iload_2","iload_3","lload_0","lload_1","lload_2",
        "lload_3","fload_0","fload_1","fload_2","fload_3","dload_0","dload_1",
        "dload_2","dload_3","aload_0","aload_1","aload_2","aload_3","iaload",
        "laload","faload","daload","aaload","baload","caload","saload",
        "istore","lstore","fstore","dstore","astore","istore_0","istore_1",
        "istore_2","istore_3","lstore_0","lstore_1","lstore_2","lstore_3",
        "fstore_0","fstore_1","fstore_2","fstore_3","dstore_0","dstore_1",
        "dstore_2","dstore_3","astore_0","astore_1","astore_2","astore_3",
        "iastore","lastore","fastore","dastore","aastore","bastore",
        "castore","sastore","pop","pop2","dup","dup_x1","dup_x2","dup2",
        "dup2_x1","dup2_x2","swap","iadd","ladd","fadd","dadd","isub",
        "lsub","fsub","dsub","imul","lmul","fmul","dmul","idiv","ldiv",
        "fdiv","ddiv","irem","lrem","frem","drem","ineg","lneg","fneg","dneg",
        "ishl","lshl","ishr","lshr","iushr","lushr","iand","land","ior","lor",
        "ixor","lxor","iinc","i2l","i2f","i2d","l2i","l2f","l2d","f2i","f2l",
        "f2d","d2i","d2l","d2f","i2b","i2c","i2s","lcmp","fcmpl","fcmpg",
        "dcmpl","dcmpg","ifeq","ifne","iflt","ifge","ifgt","ifle","if_icmpeq",
        "if_icmpne","if_icmplt","if_icmpge","if_icmpgt","if_icmple","if_acmpeq",
        "if_acmpne","goto","jsr","ret","tableswitch","lookupswitch","ireturn",
        "lreturn","freturn","dreturn","areturn","return","getstatic",
        "putstatic","getfield","putfield","invokevirtual","invokespecial",
        "invokestatic","invokeinterface","unused","new","newarray","anewarray",
        "arraylength","athrow","checkcast","instanceof","monitorenter",
        "monitorexit","wide","multianewarray","ifnull","ifnonnull","goto_w","jsr_w"
    };
    int line = 0;
    static StringBuilder fsb = new StringBuilder(100);
    static Formatter formatter = new Formatter(fsb);
    public void ppn(String s) {
        if (DumpClass.lineNumbers) {
            fsb.setLength(0);
            formatter.format("%-70s ; %d", s, (line++));
            pn(fsb.toString());
        } else {
            pn(s); 
        }
    }

    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        ppn(os[opcode] + " " + owner + "/" + name + " " + desc);
    }

    public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
        pn("; VISIT FRAME");
    }

    public void visitIincInsn(int var, int increment) {
        ppn("iinc " + var + " " + increment);
    }

    public void visitInsn(int opcode) {
        ppn(os[opcode]);
    }

    public void visitIntInsn(int opcode, int operand) {
        if (opcode == NEWARRAY) {
            String t = "UNDEFINED";
            switch (operand) {
                case T_BOOLEAN: t = " boolean"; break;
                case T_CHAR:    t = " char"; break;
                case T_FLOAT:   t = " float"; break;
                case T_DOUBLE:  t = " double"; break;
                case T_BYTE:    t = " byte"; break;
                case T_SHORT:   t = " short"; break;
                case T_INT:     t = " int"; break;
                case T_LONG:    t = " long"; break;
            }
            ppn(os[opcode] + t);
        } else {
            ppn(os[opcode] + " " +operand);
        }
    }

    public void visitJumpInsn(int opcode, Label label) {
        ppn(os[opcode] + " " + lab(label));
    }

    public void visitLabel(Label label) {
        dedent(2);
        pn(lab(label) + ":");
        indent(2);
    }

    public void visitLdcInsn(Object cst) {
        String op = (cst instanceof Double) || (cst instanceof Long) ? "ldc2_w " : "ldc ";
        String type = (cst instanceof String) ? "\"" + esc((String)cst) + "\"" : cst.toString();
        ppn(op  + type);
    }

    public void visitLineNumber(int line, Label start) {
        pn(".line " + line);
    }

    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
        pn(".var " + index + " is "+  name + " " + desc + " from " + lab(start) + " to " + lab(end));
    }

    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        ppn("lookupswitch");
        indent(4);
        for (int i= 0; i < keys.length; i++) {
            pn(keys[i] + ": " + lab(labels[i]));
        }
        pn("default: " + lab(dflt));
        dedent(4);
    }

    public void visitMethodInsn(int opcode, String owner, String name, String desc) {
        String str = os[opcode] + " " + owner + "/" + name + desc;
        if (opcode == INVOKEINTERFACE) {
            ppn(str + ", " + (TypeDesc.getNumArgumentTypes(desc)+1));
        } else {
            ppn(str);
        }
    }

    public void visitMultiANewArrayInsn(String desc, int dims) {
        ppn("multinewarray " + desc + " " + dims) ;
    }

    public void visitTableSwitchInsn(int min, int max, Label dflt, Label[] labels) {
        ppn("tableswitch  " + min);
        indent(4);
        for (int i = min; i <= max; i++) {
            pn(lab(labels[i - min]));
        }
        pn("default: " + lab(dflt));
        dedent(4);
    }

    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        pn(".catch " + type + " from " + lab(start) + " to " + lab(end) + 
                " using " + lab(handler));
    }

    public void visitTypeInsn(int opcode, String desc) {
        ppn(os[opcode] + " " +desc);
    }

    public void visitVarInsn(int opcode, int var) {
        ppn(os[opcode] + " " + var);
    }
    
    HashMap<Label,String> labels = new HashMap<Label, String>();
    int labCount = 1;
    private String lab(Label label) {
        String ret = labels.get(label);
        if (ret == null) {
            ret = "L"+ labCount++;
            labels.put(label, ret);
        }
        return ret;
    }
    public AnnotationVisitor visitAnnotationDefault() {
        return new DummyAnnotationVisitor();
    }
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        pn(".annotation " + (visible ? "visible " : "") + desc);
        pn(".end annotation");
        return new DummyAnnotationVisitor();
    }
    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
        return new DummyAnnotationVisitor();
    }
    public void visitAttribute(Attribute attr) {
    }
    public void visitCode() {
        indent(4);
    }
    public void visitMaxs(int maxStack, int maxLocals) {
        pn(".limit stack " + maxStack);
        pn(".limit locals " + maxLocals);
    }
    public void visitEnd() {
        resetIndentation();
        pn(".end method");
    }
    
    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
