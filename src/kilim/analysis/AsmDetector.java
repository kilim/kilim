package kilim.analysis;

import java.util.LinkedList;
import java.io.IOException;
import java.util.HashMap;

import kilim.mirrors.Detector;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * This class is called by Detector to parse signatures of classes
 * that may have verification errors. It uses asm to open the file instead
 * of trying to classload it.
 */
public class AsmDetector {
    static HashMap<String, ClassCache> classCacheMap= new HashMap<String, ClassCache>();
    public static int getPausableStatus(String className, String methodName,
            String desc, Detector detector) 
    {
        try {
            ClassCache classCache = classCacheMap.get(className);
            if (classCache == null) {
                ClassReader cr = new ClassReader(className);
                ClassNode cn = new ClassNode();
                cr.accept(cn, false);
                classCache = cache(className, cn);
            }
            int status = classCache.getPausableStatus(methodName, desc);
            if (status == Detector.METHOD_NOT_FOUND_OR_PAUSABLE) {
                // check super classes
                for (String superName: classCache.superClasses) {
                    status = detector.getPausableStatus(superName, methodName, desc);
                    if (status != Detector.METHOD_NOT_FOUND_OR_PAUSABLE) 
                        break;
                }
            }
            return status;
        } catch (IOException ioe) {
            System.err.println("***Error reading " + className + ": " + ioe.getMessage());
            return Detector.METHOD_NOT_FOUND_OR_PAUSABLE;
        }
    }
    private static ClassCache cache(String className, ClassNode cn) {
        ClassCache classCache = new ClassCache();
        classCache.className = className;
        classCacheMap.put(className, classCache);
        LOOP:
        for (Object m: cn.methods) {
            MethodNode mn = (MethodNode)m;
            for (Object exception: mn.exceptions) {
                if ("kilim/Pausable".equals(exception)) {
                    classCache.pausableMethods.add(mn.name + mn.desc);
                    continue LOOP;
                }
            }
            classCache.otherMethods.add(mn.name + mn.desc);
        }
        classCache.addSuper(cn.superName);
        for (Object interfaceName: cn.interfaces) {
            classCache.addSuper((String)interfaceName);
        }
//        System.out.println(classCache);
        return classCache;
    }
    public static void main(String[] args) {
        AsmDetector.getPausableStatus("com/sleepycat/je/Database", "putInternal", "Lcom/sleepycat/je/Transaction;Lcom/sleepycat/je/DatabaseEntry;Lcom/sleepycat/je/DatabaseEntry;Lcom/sleepycat/je/dbi/PutMode;Lkilim/Fiber;)Lcom/sleepycat/je/OperationStatus;)V", Detector.DEFAULT);
    }
    
    static class ClassCache {
        String className;
        LinkedList<String> pausableMethods = new LinkedList<String>();
        LinkedList<String> otherMethods = new LinkedList<String>();
        LinkedList<String> superClasses = new LinkedList<String>();
        public void addSuper(String superName) {
            if (superName.equals("java/lang/Object")) return;
            if (!superClasses.contains(superName)) {superClasses.add(superName);}
        }
        public int getPausableStatus(String methodName, String desc) {
            String md = methodName + desc;
            if (pausableMethods.contains(md)) {
                return Detector.PAUSABLE_METHOD_FOUND; 
            } else if (otherMethods.contains(md)) {
                return Detector.METHOD_NOT_PAUSABLE;
            } else { 
                return Detector.METHOD_NOT_FOUND_OR_PAUSABLE;
            }
        }
        @Override
        public String toString() {
            return className + "\nPausable Methods: " + pausableMethods + "\nOthers:" + otherMethods;
        }
    }
}


