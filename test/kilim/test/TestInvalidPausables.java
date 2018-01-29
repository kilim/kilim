/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import junit.framework.TestCase;
import kilim.KilimException;
import kilim.WeavingClassLoader;

public class TestInvalidPausables extends TestCase {
    private static boolean debug = false;
    private void ensureException(String className) {
        try {
            new WeavingClassLoader().weaveClass(className);
            fail("Expected weave exception while processing " + className);
        } catch (KilimException ke) {
            if (debug) System.out.println(ke);
        } catch (Exception e) {
            fail(e.toString());
        }
    }
    public void testWeaveConstructor() {
        ensureException("kilim.test.ex.ExInvalidConstructor");
        ensureException("kilim.test.ex.ExInvalidConstructor2");
        ensureException("kilim.test.ex.ExInvalidConstructor3");
    }
    public void testWeaveSynchronized() {
        ensureException("kilim.test.ex.ExInvalidSynchronized");
        ensureException("kilim.test.ex.ExInvalidSynchronized1");
    }
    public void testWeaveStatic() {
        ensureException("kilim.test.ex.ExInvalidStaticBlock");
    }
    
    public void testWeaveMethod() {
        ensureException("kilim.test.ex.ExInvalidCallP_NP");
    }

    public void testWeaveSuperPausable() {
        ensureException("kilim.test.ex.ExInvalidNPDerived");
        
    }
    
    public void testWeaveSuperNotPausable() {
        ensureException("kilim.test.ex.ExInvalidPDerived");
    }
    
    public void testWeaveInterfacePausable() {
        ensureException("kilim.test.ex.ExInvalidPImp");
        ensureException("kilim.test.ex.ExInvalidPFace");
    }
    
    public void testWeaveInterfaceNotPausable() {
        ensureException("kilim.test.ex.ExInvalidNPImp");
        ensureException("kilim.test.ex.ExInvalidNPFace");
    }
}
