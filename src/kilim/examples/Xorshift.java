/* Copyright (c) 2016, nqzero
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;


import kilim.Fiber;
import kilim.Generator;
import kilim.Pausable;
import kilim.Continuation;
import kilim.tools.Kilim;

/**
 *  a demo and benchmark of kilim generators used to implement the xorshift PRNG
 *  runs xorshift a number of cycles, printing the nanos per cycle and the xor of the result
 *  the value is printed just to ensure that the JIT actually runs all the code
 *  https://en.wikipedia.org/wiki/Xorshift#xorshift.2B
 *  with modes to run the same algorithm as a Generator and as a raw Continuation (the default)
 */
public class Xorshift {

    public static void main(String[] args) {
        if (Kilim.trampoline(new Object() {},false,args)) return;
        long cycles = 5000000;
        int reps = 10;
        if (args.length == 0) {
            System.out.println("args: number of cycles, number of repeats, 'gen' for Generator");
            System.out.format("\t no args provided using defaults: %d %d pure\n",cycles,reps);
        }
        try { cycles = Long.parseLong(args[0]); } catch (Exception ex) {}
        try { reps = Integer.parseInt(args[1]); } catch (Exception ex) {}
        boolean pure = true;
        int dual = 1;
        if (args.length > 2 && args[2].equals("gen")) pure = false;
        if (args.length > 2 && args[2].equals("dual")) dual = 10;

        for (int kk=0; kk < dual; kk++, pure=!pure) {
            Loop primes = pure ? new X2() : new X1();

            for (int jj=0; jj < reps; jj++)
                cycle(primes,cycles);
        }
    }
    static void cycle(Loop looper,long num) {
        final long start = System.nanoTime();
        long val = looper.loop(num);
        long duration = System.nanoTime() - start;
        System.out.format("%-10.2f nanos/op, %30d %s\n", 1.0*duration/num, val, looper.getClass().getSimpleName());
    }

    interface Loop { long loop(long num); }
    
    public static class X1 extends Generator<Long> implements Loop {
        public void execute() throws Pausable,Exception {
            long x, y, s0=103, s1=17;
            while (true) {
                x = s0;
                y = s1;
                s0 = y;
                x ^= (x << 23);
                s1 = x ^ y ^ (x >> 17) ^ (y >> 26);
                yield(s1 + y);
            }
        }
        public long loop(long num) {
            long val = 0;
            for (int ii=0; ii < num; ii++)
                val = val ^ next();
            return val;
        }
    }

    public static class X2 extends Continuation implements Loop {
        long result;
        public void execute() throws Pausable {
            long x, y, s0=103, s1=17;
            while (true) {
                x = s0;
                y = s1;
                s0 = y;
                x ^= (x << 23);
                s1 = x ^ y ^ (x >> 17) ^ (y >> 26);
                result = (s1 + y);
                Fiber.yield();
            }
        }
        public long loop(long num) {
            long val = 0;
            for (int ii=0; ii < num && !run(); ii++)
                val = val ^ result;
            return val;
        }
    }
    /*
        from wikipedia:
        uint64_t x = s[0];
        uint64_t const y = s[1];
        s[0] = y;
        x ^= x << 23; // a
        s[1] = x ^ y ^ (x >> 17) ^ (y >> 26); // b, c
        return s[1] + y;            
    */
}
