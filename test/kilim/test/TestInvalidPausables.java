/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import junit.framework.TestCase;
import kilim.KilimException;
import kilim.mirrors.Detector;
import kilim.tools.Weaver;

public class TestInvalidPausables extends TestCase {
    private void ensureException(String className) {
        try {
            Weaver.weaveClass2(className, Detector.DEFAULT);
            fail("Expected weave exception while processing " + className);
        } catch (KilimException ke) {
        } catch (Exception e) {
            fail(e.toString());
        }
    }
    public void testWeaveConstructor() {
        ensureException("kilim.test.ex.ExInvalidConstructor");
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
        
    }
    
    public void testWeaveInterfaceNotPausable() {
        ensureException("kilim.test.ex.ExInvalidNPImp");
        
    }
}
