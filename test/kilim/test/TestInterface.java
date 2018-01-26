/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import junit.framework.TestCase;
import kilim.ExitMsg;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.Task;
import kilim.test.ex.ExInterfaceGenericTask;

public class TestInterface extends TestCase {
    public void testIntCall() throws Exception {
        Task task = new kilim.test.ex.ExInterfaceImpl();
        Mailbox<ExitMsg> exitmb = new Mailbox<ExitMsg>();
        Scheduler s = new Scheduler(1);
        task.informOnExit(exitmb);
        task.setScheduler(s); 
        task.start();
        
        ExitMsg m = exitmb.getb();
        if (m == null) {
            fail("Timed Out");
        } else {
            Object res = m.result;
            if (res instanceof Throwable) {
                ((Throwable)res).printStackTrace();
                fail(m.toString());
            }
        }
        s.shutdown();
    }
    
    static class ExInterfaceGenericImpl implements kilim.test.ex.ExInterfaceGeneric<String> {
        @Override
        public String get() throws Pausable {
            return "foo";
        }
    }
    public void testGenericInterface() throws Exception {
        ExInterfaceGenericTask task = new ExInterfaceGenericTask(new ExInterfaceGenericImpl());
        Mailbox<ExitMsg> exitmb = new Mailbox<ExitMsg>();
        Scheduler s = new Scheduler(1);
        task.informOnExit(exitmb);
        task.setScheduler(s); 
        task.start();
        
        ExitMsg m = exitmb.getb();
        if (m == null) {
            fail("Timed Out");
        } else {
            Object res = m.result;
            if (res instanceof Throwable) {
                ((Throwable)res).printStackTrace();
                fail(m.toString());
            }
            if (task.getResponse != "foo") {
            	fail("Expected 'foo', got '" + res + "'");
            } 
        }
        s.shutdown();
    }

    // https://github.com/kilim/kilim/issues/53
    // runtime-only - others would require parsing bytecode which seems more problematic than valuable
    public void testRuntimeAnnotation() {
        checkRuntimeAnnotation(Anno.F1.class);
        checkRuntimeAnnotation(Anno.C1.class);
    }

    static void checkRuntimeAnnotation(Class klass) {
        Method method = null;
        try { method = klass.getDeclaredMethod("addEntry"); }
        catch (Exception ex) {}
        Annotation canno = klass.getAnnotation(Anno.A1.class),
                manno = method.getAnnotation(Anno.A1.class);
        assertNotNull(canno);
        // fixme:java7 - annotations are not preserved in the fiber-less method in java 7
        if (AllWoven.java8)
            assertNotNull(manno);
    }

    private static class Anno {
        @Retention(RetentionPolicy.RUNTIME)
        public @interface A1 {
            String value();
        }
        @A1("hello")
        public interface F1 {
            @A1(value = "Add")
            void addEntry() throws Pausable;
        }
        @A1("hello")
        public static abstract class C1 {
            @A1(value = "Add")
            abstract void addEntry() throws Pausable;
        }
    }
}
