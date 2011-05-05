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
    ClassReader           cr;
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
    private Detector      detector;

    public ClassFlow(InputStream is, Detector detector) throws IOException {
        cr = new ClassReader(is);
        this.detector = detector;
    }

    public ClassFlow(String aClassName, Detector detector) throws IOException {
        cr = new ClassReader(aClassName);
        this.detector = detector;
    }

    public ClassFlow(byte[] data, Detector detector) {
        cr = new ClassReader(data);
        this.detector = detector;
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
                exceptions, detector);
        super.methods.add(mn);
        return mn;
    }

    public ArrayList<MethodFlow> getMethodFlows() {
        assert (methodFlows != null) : "ClassFlow.analyze not called";
        return methodFlows;
    }

    public ArrayList<MethodFlow> analyze(boolean forceAnalysis) throws KilimException {
        // cr.accept(this, ClassReader.SKIP_DEBUG);

        Detector save = Detector.setDetector(detector);
        try {
            cr.accept(this, false);
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
                    if ((mf.isPausable() || forceAnalysis) && (!mf.isAbstract())) {
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
            Detector.setDetector(save);
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

    public Detector detector() {
        return detector;
    }
}
