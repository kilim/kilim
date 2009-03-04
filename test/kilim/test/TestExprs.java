/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;


/**
 * An attempt to exercise all the bytecode associated with primitive
 * types (loading/storing from registers, array operations, arithmetic operations etc.) 
 */
public class TestExprs extends Base {
    public void testInts() throws Exception {
        cache("kilim.test.ex.ExInts");
    }
    public void testLongs() throws Exception {
        cache("kilim.test.ex.ExLongs");
    }

    public void testFloats() throws Exception {
        cache("kilim.test.ex.ExFloats");
    }

    public void testDoubles() throws Exception {
        cache("kilim.test.ex.ExDoubles");
    }
}
