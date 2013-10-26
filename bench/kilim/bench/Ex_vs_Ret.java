/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench;

public class Ex_vs_Ret {

    /**
     * @param args
     */
    public static void main(String[] args) {
        final int ntimes = 1000000;
        final int depth = 10;

        // JIT Warmup ===========================================
        for (int i = 0; i < 1000; i++) ret(depth);
        for (int i = 0; i < 1000; i++) {
            try {
                ex(depth);
            } catch (FastEx ignore) {}
        }

        long start = System.currentTimeMillis();
        for (int i = 0; i < ntimes; i++) {
            ret(depth);
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Iterations = : " +ntimes + ", stack depth = " + depth);
        System.out.println("ret ms: " + elapsed);

        start = System.currentTimeMillis();
        for (int i = 0; i < ntimes; i++) {
            try {
                ex(depth);
            } catch (FastEx fe) {}
        }
        elapsed = System.currentTimeMillis() - start;
        System.out.println("ex : " + elapsed);
    }
    
    static void ret(int depth) {
        if (depth != 0) {
            ret(depth-1);
        }
    }
    
    static void ex(int depth) throws FastEx {
        if (depth == 0) {
            throw new FastEx();
        }
        ex(depth-1);
    }
}

final class FastEx extends Throwable {
    private static final long serialVersionUID = 1L; // Just suppressing warnings.

    @Override
    public synchronized Throwable fillInStackTrace() {
        return null; // The most time consuming part of throwing an exception.
    }
}
