/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import java.util.ArrayList;

import kilim.analysis.Usage;
import junit.framework.TestCase;

public class TestUsage extends TestCase {
    /**
     * Tests whether a bunch of reads and writes produces the appropriate live-in 
     */
    public void testReadWrite() {
        Usage u = new Usage(4);
        u.read(1);
        u.read(2);
        u.write(2);
        u.write(3);
        u.evalLiveIn(new ArrayList<Usage>());
        assertFalse(u.isLiveIn(0));
        assertTrue(u.isLiveIn(1));
        assertTrue(u.isLiveIn(2));
        assertFalse(u.isLiveIn(3));
    }
    
    public void testChange() {
        Usage u = new Usage(31);
        Usage ufollow1 = new Usage(31);
        Usage ufollow2 = new Usage(31);
        //       29:R 
        //       30:W
        // Usage 1 and 2.
        //   28:in    28:not_in 
        //   29:in    29:not_in
        //   30:in    30:in
        // Expected usage.in :  28:in 29:in 30:not_in
        u.read(29); u.write(30);
        ufollow1.setLiveIn(28); ufollow1.setLiveIn(29); ufollow1.setLiveIn(30);
        ufollow2.setLiveIn(30);
        ArrayList<Usage> ua = new ArrayList<Usage>(2);
        ua.add(ufollow1); ua.add(ufollow2);
        assertTrue(u.evalLiveIn(ua)); // should return changed == true
        for (int i = 0; i < 28; i++) {
            assertFalse(u.isLiveIn(i));
        }
        assertTrue(u.isLiveIn(28));
        assertTrue(u.isLiveIn(29));
        assertFalse(u.isLiveIn(30));
    }
}
