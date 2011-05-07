package kilim.test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import junit.framework.TestCase;
import kilim.analysis.ClassInfo;
import kilim.tools.Javac;
import kilim.tools.Weaver;

public class TestDynamicWeaver extends TestCase {
    /**
     * Sample code to test a wide range of functionality: separate packages, import statements, 
     * mutually recursive classes across packages, Pausable methods, inner classes, public
     * and non-public classes, etc.
     */
    String code1 = 
        "package code1;" + 
        "import java.io.IOException;" + 
        "import kilim.*;" +
        "public class A {" + 
         "    code2.B bar;" +
         "    class Inner {" +
         "       void foo() throws Pausable, IOException {" +
         "           for (int i = 0; i < 10; i++) {" +
         "                Outer.xxx();" +
         "           }" +
         "       }" +
         "    }" +
        "}" +
        "class Outer { " + 
        "   static void xxx() throws Pausable, java.io.IOException {}" +
        "}";

    String code2 = 
        "package code2;" + 
        "public class B { " +
        "    code1.A foo;" + 
        "}";


    public List<ClassInfo> compile() throws Exception {
        List<ClassInfo> classes = Javac.compile(Arrays.asList(code1, code2));
        assertTrue(classes.size() == 4);
        HashSet<String> expectedClasses = new HashSet<String>(
                Arrays.asList("code1.A", "code1.A$Inner", "code1.Outer", "code2.B"));

        for (ClassInfo cl : classes) {
            assertTrue(expectedClasses.contains(cl.className));
            assertTrue(cl.bytes.length > 200);
        }
        return classes;
    }

    public void testWeave() throws Exception {
        List<ClassInfo> classes = compile();

        classes = new Weaver().weave(classes);
        

        HashSet<String> expectedClasses = new HashSet<String>(
                Arrays.asList("kilim.S_I", "code1.A$Inner", "code1.Outer"));

        assertTrue(expectedClasses.size() == classes.size());

        for (ClassInfo cl : classes) {
            assertTrue(expectedClasses.contains(cl.className));
            assertTrue(cl.bytes != null && cl.bytes.length > 0);
            // ensure classes are loadable
            TestClassLoader cll = new TestClassLoader();
            Class<?> c = null;
            try {
                c = cll.loadClass(cl.className);
                // The only class that should be loadable is "kilim.S_I"
                assertTrue(c.getName().startsWith("kilim"));
            } catch (ClassNotFoundException ignore) {
                // the new classes should not have been in the classpath, and 
                // ClassNotFoundException is thrown as expected
                assertTrue(cl.className.startsWith("code"));
                // define these classes
                try {
                    cll.load(cl);
                } catch (Throwable t) {
                    fail(t.getMessage());
                }
            }
        }
    }
    
    static class TestClassLoader extends ClassLoader {
        public void load(ClassInfo cl) {
            Class<?> c = super.defineClass(cl.className, cl.bytes, 0, cl.bytes.length);
            super.resolveClass(c);
        }
    }
}
