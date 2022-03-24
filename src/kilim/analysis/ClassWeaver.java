/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.analysis;
import static kilim.Constants.STATE_CLASS;
import static kilim.Constants.WOVEN_FIELD;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import kilim.Constants;
import kilim.KilimException;
import kilim.mirrors.Detector;

import kilim.tools.Weaver;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;

/**
 * This class is the main entry point for the Weave tool. It uses
 * ClassFlow to parse and analyze a class file, and writes out a
 * CPS transformed file if needed
 */
public class ClassWeaver {
    public ClassFlow classFlow;
    List<ClassInfo> classInfoList = new LinkedList<ClassInfo>();
    static ThreadLocal<HashMap<String, ClassInfo>> stateClasses_ = 
    		new ThreadLocal<HashMap<String, ClassInfo>>() {
    	protected HashMap<String, ClassInfo> initialValue() {
    		return new HashMap<String, ClassInfo>();
    	}
    };
    
    public static void reset() {
    	stateClasses_.set(new HashMap<String, ClassInfo>() );
    }
    

    public KilimContext context;


    public InputStream getCode(Weaver weaver,InputStream is, String name){
        byte [] code = null;
        ClassWeaver cw = weaver.weave(is);
        for (ClassInfo ci : cw.getClassInfos())
            if (ci.className.equals(name)) code = ci.bytes;
        if (code==null) code = cw.classFlow.code;
        return code==null ? null : new ByteArrayInputStream(code);
    }
    
    
    public ClassWeaver(KilimContext context,InputStream is) throws IOException {
        this.context = context;
        classFlow = new ClassFlow(context,is);
    }
    
    
    public void weave() throws KilimException {
        classFlow.analyze(false);
        if (needsWeaving() && classFlow.isPausable()) {
            boolean computeFrames = (classFlow.version & 0x00FF) >= 50;
            ClassWriter cw = new kilim.analysis.ClassWriter(
                    computeFrames ? ClassWriter.COMPUTE_FRAMES : 0, context.detector);
            accept(cw);
            addClassInfo(new ClassInfo(classFlow.getClassName(), cw.toByteArray()));
        }
    }
    

    private void accept(final ClassVisitor cv) {
        ClassFlow cf = classFlow;
        // visits header
        String[] interfaces = toStringArray(cf.interfaces);
        cv.visit(cf.version, cf.access, cf.name, cf.signature, cf.superName, interfaces);
        // visits source
        if (cf.sourceFile != null || cf.sourceDebug != null) {
            cv.visitSource(cf.sourceFile, cf.sourceDebug);
        }
        if (cf.nestHostClass != null)
            cv.visitNestHost(cf.nestHostClass);
        // visits outer class
        if (cf.outerClass != null) {
            cv.visitOuterClass(cf.outerClass, cf.outerMethod, cf.outerMethodDesc);
        }
        if (cf.nestMembers != null)
            for (String member : cf.nestMembers)
                cv.visitNestMember(member);
        // visits attributes and annotations
        int i, n;
        AnnotationNode an;
        n = cf.visibleAnnotations == null ? 0 : cf.visibleAnnotations.size();
        for (i = 0; i < n; ++i) {
            an = (AnnotationNode) cf.visibleAnnotations.get(i);
            an.accept(cv.visitAnnotation(an.desc, true));
        }
        n = cf.invisibleAnnotations == null ? 0
                : cf.invisibleAnnotations.size();
        for (i = 0; i < n; ++i) {
            an = (AnnotationNode) cf.invisibleAnnotations.get(i);
            an.accept(cv.visitAnnotation(an.desc, false));
        }
        
        n = cf.attrs == null ? 0 : cf.attrs.size();
        for (i = 0; i < n; ++i) {
            cv.visitAttribute((Attribute) cf.attrs.get(i));
        }
        // visits inner classes
        for (i = 0; i < cf.innerClasses.size(); ++i) {
            ((InnerClassNode) cf.innerClasses.get(i)).accept(cv);
        }
        // visits fields
        for (i = 0; i < cf.fields.size(); ++i) {
            ((FieldNode) cf.fields.get(i)).accept(cv);
        }
        /*
         * Mark this class as "processed" by adding a dummy field, so that
         * we don't weave an already woven file
         */
        cv.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, WOVEN_FIELD, "Z", "Z", Boolean.TRUE);
        // visits methods
        
        MethodFlow sam = classFlow.getSAM(); 
        for (i = 0; i < cf.methods.size(); ++i) {
            MethodFlow m = (MethodFlow) cf.methods.get(i);
            if (needsWeaving(m)) {
                // For pausable methods, we generate two function signatures, one the original
                // and the other with a fiber as the last param. For a non-abstract method, the 
                // fiber'd version gets the original's body, and the original version throws
                // an error saying "Not Woven", in case it is called at run time. This latter part
                // is done in makeNotWovenMethod below.
                
                // However, if it is a single abstract method (SAM) of a functional interface,
                // then we invert the arrangement. We generate two method bodies as before,
                // but the fiber'd version gets the "not woven" message. This preserves the 
                // original method as the SAM. However, the weaver (see CallWeaver and SAMWeaver)
                // arranges it such that the invokedynamic instruction bridges the fiber'd version
                // of the method with the body of the lambda expression. 
                
                boolean isSAM = (sam == m);
                MethodWeaver mw = new MethodWeaver(this, context.detector, m, isSAM);
                mw.accept(cv);
                if (m.isPausable())
                    mw.makeNotWovenMethod(cv, m, isSAM);
            } else {
                m.restoreNonInstructionNodes();
                m.accept(cv);
            }
        }

        // create shim methods (if any) to wrap SAM interface methods
        for (SAMweaver sw: samWeavers) {
            sw.accept(cv);
        }
        
        // visits end
        cv.visitEnd();
    }

    @SuppressWarnings(value = { "unchecked" })
    static String[] toStringArray(List list) {
        String[] array = new String[list.size()];
        list.toArray(array);
        return array;
    }

    void addClassInfo(ClassInfo ci) {
        classInfoList.add(ci);
    }
    
    public List<ClassInfo>  getClassInfos() {
        return classInfoList;
    }
    
    /*
     * A method needs weaving ordinarily if it is marked pausable.
     * However, if there exists another method with the same name
     * and parameters and an additional Fiber parameter as the last
     * one, then this method doesn't need weaving. Examples are
     * kilim.Task.yield and kilim.Task.sleep
     */
    boolean needsWeaving(MethodFlow mf) {
        if (!mf.needsWeaving() || mf.desc.endsWith(Constants.D_FIBER_LAST_ARG)) 
            return false;
        String fdesc = mf.desc.replace(")", Constants.D_FIBER_LAST_ARG);
        for (MethodFlow omf: classFlow.getMethodFlows()) {
            if (omf == mf) continue;
            if (mf.name.equals(omf.name) && fdesc.equals(omf.desc)) {
                return false;
            }
        }
        return true;
    }
    
    boolean needsWeaving() {
        if (classFlow.isWoven) return false;
        for (MethodFlow mf: classFlow.getMethodFlows()) {
            if (needsWeaving(mf)) return true;
        }
        return false;
    }
    
    /**
     * Create a custom class (structure) to hold the state. The name of the
     * state reflects the numbers of the various VMtypes in valInfoList. class
     * kilim.SO2IJ3 reflects a class that stores two Objects one Integer and 3
     * longs.
     * 
     * <pre>
     *            class kilim.SO2IJ3 extends kilim.State {
     *               public Object f1, f2;
     *               public int f3;
     *               public long f4, f5, f6;
     *            } 
     * </pre>
     * 
     * If there's no data to store, we use the kilim.State class directly to
     * store the basic amount of data necessary to restore the stack.
     */

    String createStateClass(ValInfoList valInfoList) {
        int numByType[] = { 0, 0, 0, 0, 0 };
        for (ValInfo vi : valInfoList) {
            numByType[vi.vmt]++;
        }
        String className = makeClassName(numByType);
        ClassInfo classInfo= null;
            classInfo= stateClasses_.get().get(className);
            if (classInfo == null) {
                ClassWriter cw = new kilim.analysis.ClassWriter(ClassWriter.COMPUTE_FRAMES, context.detector);
                cw.visit(V1_1, ACC_PUBLIC | ACC_FINAL, className, null, "kilim/State", null);

                // Create default constructor
                // <init>() {
                // super(); // call java/lang/Object.<init>()
                // }
                MethodVisitor mw = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mw.visitVarInsn(ALOAD, 0);
                mw.visitMethodInsn(INVOKESPECIAL, STATE_CLASS, "<init>", "()V", false);
                mw.visitInsn(RETURN);
                // this code uses a maximum of one stack element and one local variable
                mw.visitMaxs(1, 1);
                mw.visitEnd();
                // create fields of the appropriate type.
                for (ValInfo vi : valInfoList) {
                    cw.visitField(ACC_PUBLIC, vi.fieldName, vi.fieldDesc(), null, null);
                }
                classInfo= new ClassInfo(className, cw.toByteArray());
                stateClasses_.get().put(className, classInfo);
            }
        if (!classInfoList.contains(classInfo))
          addClassInfo(classInfo);
        return className;
    }

    private String makeClassName(int[] numByType) {
        StringBuilder sb = new StringBuilder(30);
        sb.append("kilim/S_");
        for (int t = 0; t < 5; t++) {
            int c = numByType[t];
            if (c == 0)
                continue;
            sb.append(VMType.abbrev[t]);
            if (c > 1) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    boolean isInterface() {
        return classFlow.isInterface();
    }
    
    ArrayList<SAMweaver> samWeavers = new ArrayList<SAMweaver>();
    SAMweaver getSAMWeaver(String owner, String methodName, String desc, boolean itf) {
        SAMweaver sw = new SAMweaver(context,owner, methodName, desc, itf);
        // intern
        for (SAMweaver s: samWeavers) {
            if (s.equals(sw)) {
                return s; 
            }
        }
        samWeavers.add(sw);
        sw.setIndex(samWeavers.size());
        
        return sw;
    }

    String getName() {
        return classFlow.name;
    }

}


