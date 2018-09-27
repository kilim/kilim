/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim.analysis;

import kilim.*;
import kilim.mirrors.Detector;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/**
 * This class reads a .class file (or stream), wraps each method with a MethodFlow object and optionally analyzes it.
 * 
 */
public class ClassFlow extends ClassNode {
    ArrayList<MethodFlow> methodFlows;
    public ClassReader           cr;
    String                classDesc;
    /**
     * true if any of the methods contained in the class file is pausable. ClassWeaver uses it later to avoid weaving if
     * isPausable isn't true.
     */
    private boolean       isPausable;

    /**
     * true if the .class being read is already woven.
     */
    public boolean        isWoven = false;
    public KilimContext context;

    /** the original bytecode associated with the class */
    public byte [] code;

    public ClassFlow(KilimContext context,InputStream is) throws IOException {
        super(Opcodes.ASM7_EXPERIMENTAL);
        this.context = context;
        cr = new ClassReader(is);
        code = cr.b;
    }

    public ClassFlow(KilimContext context,String aClassName) throws IOException {
        super(Opcodes.ASM7_EXPERIMENTAL);
        this.context = context;
        cr = new ClassReader(aClassName);
        code = cr.b;
    }



    @Override
    @SuppressWarnings( { "unchecked" })
    public MethodVisitor visitMethod(
            final int access,
            final String name,
            final String desc,
            final String signature,
            final String[] exceptions)
    {
        MethodFlow mn = new MethodFlow( this, access, name,  desc, signature,
                exceptions, context.detector);
        super.methods.add(mn);
        return mn;
    }

    public ArrayList<MethodFlow> getMethodFlows() {
        assert (methodFlows != null) : "ClassFlow.analyze not called";
        return methodFlows;
    }

    public ArrayList<MethodFlow> analyze(boolean forceAnalysis) throws KilimException {
        // cr.accept(this, ClassReader.SKIP_DEBUG);

        try {
            cr.accept(this, /*flags*/ClassReader.SKIP_FRAMES);
            for (Object o : this.fields) {
                FieldNode fn = (FieldNode) o;
                if (fn.name.equals(Constants.WOVEN_FIELD)) {
                    isWoven = true;
                    break;
                }
            }
            if (isWoven && !forceAnalysis) 
                return new ArrayList<MethodFlow>(); // This is a hack. 


            cr = null; // We don't need this any more.
            classDesc = TypeDesc.getInterned("L" + name + ';');
            ArrayList<MethodFlow> flows = new ArrayList<MethodFlow>(methods.size());
            String msg = "";
            for (Object o : methods) {
                try {
                    MethodFlow mf = (MethodFlow) o;
                    if (mf.isBridge()) {
                        MethodFlow mmf = getOrigWithSameSig(mf);
                        if (mmf != null)
                            mf.setPausable(mmf.isPausable());
                    }
                    mf.verifyPausables();
                    if (mf.isPausable())
                        isPausable = true;
                    if ((mf.needsWeaving() || forceAnalysis) && (!mf.isAbstract())) {
                        mf.analyze();
                    }
                    flows.add(mf);
                } catch (KilimException ke) {
                    msg = msg + ke.getMessage() + "\n-------------------------------------------------\n";
                }
            }
            if (msg.length() > 0) {
                throw new KilimException(msg);
            }
            methodFlows = flows;
            return flows;

        } finally {
        }
    }

    private MethodFlow getOrigWithSameSig(MethodFlow bridgeMethod) {
        for (Object o : methods) {
            MethodFlow mf = (MethodFlow) o;
            if (mf == bridgeMethod)
                continue;
            if (mf.name.equals(bridgeMethod.name)) {
                String mfArgs = mf.desc.substring(0, mf.desc.indexOf(')'));
                String bmArgs = bridgeMethod.desc.substring(0, bridgeMethod.desc.indexOf(')'));
                if (mfArgs.equals(bmArgs))
                    return mf;
            }
        }
        return null;
        // throw new AssertionError("Bridge method found, but original method does not exist\nBridge method:" +
        // this.name + "::" + bridgeMethod.name + bridgeMethod.desc);
    }

    public String getClassDescriptor() {
        return classDesc;
    }

    public String getClassName() {
        return super.name.replace('/', '.');
    }

    public boolean isPausable() {
        getMethodFlows(); // check analyze has been run.
        return isPausable;
    }

    boolean isInterface() {
        return (this.access & Opcodes.ACC_INTERFACE) != 0;
    }

    boolean isJava7() {
        return (version & 0x00FF) < 52;
    }
    
    /*
     * If this class is a functional interface, return the one "Single Abstract
     * Method". If there is more than one abstract method, return null.
     * SAM methods are given special treatment 
     */
    public MethodFlow getSAM() {
        if (!isInterface() || isJava7()) {
            return null;
        }
        MethodFlow sam = null;
        for (MethodFlow mf: methodFlows) {
            if (mf.isAbstract()) {
                if (sam != null) {
                    return null;
                }
                sam = mf;
            }
        }
        return sam;
    }
}
