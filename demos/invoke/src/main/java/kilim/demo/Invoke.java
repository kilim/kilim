package kilim.demo;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import kilim.Task;
import kilim.WeavingClassLoader;
import kilim.analysis.ClassInfo;
import kilim.tools.Javac;
import kilim.tools.Weaver;

/**
 * this class demonstrates a number of high-level functions: it compiles java code to bytecode
 * using a javac wrapper (not intended for production), weaves the bytecode dynamically,
 * loads the woven bytecode in a custom classloader, and then uses Method.invoke to call a
 * method dynamically
 */
public class Invoke {
    public static String SUCCESS_MESSAGE =
            "\\n\\nPausable runtime-woven-and-compiled method successfully invoked\\n\\n";
    static String code1
            = "package code;"
            + "import java.io.IOException;"
            + "import kilim.*;"
            + "public class A implements B{"
            + "    public void doSome() throws Pausable, Exception {"
            +          "Task.sleep(1500); System.out.println(\"" + SUCCESS_MESSAGE + "\");"
            + "    }"
            + "}";

    static String code2 =
            "package code;" +
            "import kilim.Pausable;"+
            "public interface B { " +
                "    void doSome() throws Exception,Pausable;" +
            "}";


    public void weaveAndInvoke(String name,List<ClassInfo> classes) throws Exception {
        // this compile step is unreliable as the compiler classpath depends on the execution environment
        // so eg mvn in netbeans, maven from bash, and java from bash can give different values
        // if the classpath is incorrect, the codes won't compile
        // and of course, then not weave or load either
        Loader cll = new Loader();
        classes = new Weaver(null).weave(classes);

        cll.predefine(classes);
        
        Class<?> myclass = cll.loadClass(name);
        Method method = myclass.getMethod("doSome");
        Object obj = myclass.newInstance();
        System.out.println("...");
        Task.spawn(method,obj).joinb();
        System.out.println("---");
    }

    static class Loader extends ClassLoader {
        Loader() {
            // Note: different execution environments use different system classloaders
            // so it's important to delegate explicitly to the classloader that the code is running under
            // otherwise the custom classloader won't see related classes in many cases
            super(Loader.class.getClassLoader());
        }
        public void predefine(List<ClassInfo> classes) {
            synchronized (this) {
                for (ClassInfo cl : classes)
                    map.put(cl.className,tmp);
                for (ClassInfo cl : classes) {
                    Class<?> c = super.defineClass(cl.className, cl.bytes, 0, cl.bytes.length);
                    map.put(cl.className,c);
                    super.resolveClass(c);
                }
            }
        }
        private static class TmpClass {}
        Class tmp = TmpClass.class;

        public final HashMap<String,Class> map = new HashMap();

        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class klass;
            synchronized (this) {
                klass = map.get(name);
                if (klass==null)
                    klass = getParent().loadClass(name);
                if (klass==tmp | klass==null)
                    throw new ClassNotFoundException();
            }
            if (resolve) super.resolveClass(klass);
            return klass;
        }

        
    }
    static ClassInfo getBytecode(String name) {
        byte [] code = WeavingClassLoader.findCode(null,name);
        return new ClassInfo(name,code);
    }

    /** the output should print the SUCCESS_MESSAGE */
    public static void main(String[] args) throws Exception {
        String className = CodeA.class.getName();
        List<ClassInfo> 
                dynamic = Javac.compile(Arrays.asList(code1,code2)),
                classes = Arrays.asList(
                        getBytecode(CodeB.class.getName()),
                        getBytecode(className)
                );
        new Invoke().weaveAndInvoke(className, classes);
        new Invoke().weaveAndInvoke("code.A",dynamic);
        Task.idledown();
    }
}
