/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import static kilim.Constants.D_ARRAY_BOOLEAN;
import static kilim.Constants.D_BOOLEAN;
import static kilim.Constants.D_DOUBLE;
import static kilim.Constants.D_INT;
import static kilim.Constants.D_LONG;
import static kilim.Constants.D_OBJECT;
import static kilim.Constants.D_RETURN_ADDRESS;
import static kilim.Constants.D_STRING;
import static kilim.Constants.D_THROWABLE;
import static kilim.Constants.D_UNDEFINED;
import kilim.analysis.BasicBlock;
import kilim.analysis.Frame;
import kilim.analysis.MethodFlow;
import kilim.analysis.Usage;
import kilim.analysis.Value;

public class TestFrame extends Base {
    protected void setUp() throws Exception {
        cache("kilim.test.ex.ExFrame");
    }

    public void testMethodFrame() {
        MethodFlow flow = getFlow("kitchensink");
        if (flow == null)
            return;
        for (BasicBlock bb : flow.getBasicBlocks()) {
            if (bb.startPos == 0) {
                Frame f = bb.startFrame;
                assertEquals("Lkilim/test/ex/ExFrame;", f.getLocal(0).getTypeDesc());
                assertSame(D_INT, f.getLocal(1).getTypeDesc());
                assertSame(D_LONG, f.getLocal(2).getTypeDesc());
                // Note LONG and BOOLEAN take up two words
                assertSame(D_BOOLEAN, f.getLocal(4).getTypeDesc());
                assertSame(D_DOUBLE, f.getLocal(5).getTypeDesc());
                assertEquals("[[Ljava/lang/String;", f.getLocal(7).getTypeDesc());
            }
        }
    }

    public void testStack() {
        Frame f = new Frame(1, 4);
        f.push(Value.make(0, D_LONG));
        f.push(Value.make(0, D_DOUBLE));
        f.push(Value.make(0, D_ARRAY_BOOLEAN));
        f.push(Value.make(0, D_RETURN_ADDRESS));
        f.pop();
        f.pop();
        f.pop();
        assertSame(D_LONG, f.pop().getTypeDesc());
    }

    public void testLocals() {
        Frame f = new Frame(4, 1);
        f.setLocal(0, Value.make(10, D_LONG));
        f.setLocal(2, Value.make(12, D_DOUBLE));
        f.setLocal(0, Value.make(20, D_INT));
        f.setLocal(1, Value.make(31, D_STRING));
        assertSame(D_INT, f.getLocal(0).getTypeDesc());
        assertSame(D_STRING, f.getLocal(1).getTypeDesc());
        assertSame(D_DOUBLE, f.getLocal(2).getTypeDesc());
    }

    public void testMergeUnchangedTypes() {
        Frame f = new Frame(4, 4);
        f.setLocal(1, Value.make(0, D_INT));
        f.setLocal(2, Value.make(0, "[Ljava/lang/Object;"));
        f.setLocal(3, Value.make(0, "Ljava/lang/reflect/AccessibleObject;"));
        f.push(Value.make(0, "Ljava/lang/Object;"));

        Frame g = new Frame(4, 4);
        g.setLocal(1, Value.make(0, D_INT));
        g.setLocal(2, Value.make(0, "[Ljava/lang/Object;"));
        g.setLocal(3, Value.make(0, "Ljava/lang/reflect/Field;"));
        g.push(Value.make(0, "Ljava/io/Serializable;"));
        Usage usage = new Usage(4);
        usage.setLiveIn(1);
        usage.setLiveIn(2);
        usage.setLiveIn(3);
        assertEquals(f, f.merge(g, /* localsOnly= */false, usage));
    }

    public void testMergeChangedTypes() {
        Frame f = new Frame(4, 4);
        f.setLocal(0, Value.make(0, D_INT));
        f.setLocal(1, Value.make(0, "Ljava/lang/reflect/Field;"));
        f.setLocal(2, Value.make(0, "[Ljava/lang/Object;"));
        f.push(Value.make(0, "Ljava/io/Serializable;"));

        Frame g = new Frame(4, 4);
        g.setLocal(0, Value.make(0, D_INT));
        g.setLocal(1, Value.make(0, "Ljava/lang/reflect/AccessibleObject;"));
        g.setLocal(2, Value.make(0, "[Ljava/lang/Object;"));
        g.push(Value.make(0, "Ljava/lang/Object;"));

        Usage usage = new Usage(4);
        for (int i = 0; i < 4; i++)
            usage.setLiveIn(i);
        Frame h = f.merge(g, /* localsOnly= */false, usage);
        assertNotSame(f, h);
        for (int i = 0; i < 4; i++) {
            assertEquals(g.getLocal(i), h.getLocal(i));
        }
    }

    public void testMergeUnchangedIfNoUsage() {
        Frame f = new Frame(4, 4);
        f.setLocal(0, Value.make(0, D_RETURN_ADDRESS));
        f.setLocal(1, Value.make(0, D_INT));
        f.setLocal(2, Value.make(0, D_DOUBLE));

        Frame g = new Frame(4, 4);
        g.setLocal(0, Value.make(0, D_INT));
        g.setLocal(1, Value.make(0, D_DOUBLE));
        g.setLocal(3, Value.make(0, D_THROWABLE));

        Usage noUsage = new Usage(4); // default, everything is untouched.
        assertSame(f, f.merge(g, /* localsOnly= */true, noUsage));

        for (int i = 0; i < 4; i++) {
            noUsage.write(i); // set everything to OVERWRITTEN
        }
        assertSame(f, f.merge(g, /* localsOnly= */true, noUsage));
    }

    public void testIncompatibleMerge() {
        Frame f = new Frame(4, 4);
        f.setLocal(0, Value.make(0, D_OBJECT));
        Frame g = new Frame(4, 4);
        g.setLocal(0, Value.make(0, D_INT));

        Usage usage = new Usage(4);
        for (int i = 0; i < 4; i++) {
            usage.setLiveIn(i); // set everything to READ
        }
        f = f.merge(g, true, usage);
        assertTrue(f.getLocal(0).getTypeDesc() == D_UNDEFINED);
    }
}
