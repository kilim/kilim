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

/**
 *  a demo and benchmark of kilim generators used to implement the xorshift PRNG
 *  runs xorshift a number of cycles, printing the nanos per cycle and the xor of the result
 *  the value is printed just to ensure that the JIT actually runs all the code
 *  https://en.wikipedia.org/wiki/Xorshift#xorshift.2B
 */
public class Xorshift2 extends Continuation {
    long result;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("arg1: number of cycles, arg2: number of repeats, both optional");
            System.exit(0);
        }
        long cycles = Long.parseLong(args[0]);
        int reps = 1;
        try { reps = Integer.parseInt(args[1]); } catch (Exception ex) {}

        Xorshift2 primes = new Xorshift2();

        for (int jj=0; jj < reps; jj++)
            primes.cycle(cycles);
    }
    public void cycle(long num) {
        final long start = System.nanoTime();
        long val = 0;
        for (int ii=0; ii < num && !run(); ii++)
            val = val ^ result;
        long duration = System.nanoTime() - start;
        System.out.format("%-10.2f nanos/op, %16d\n", 1.0*duration/num, val);
    }

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
    }
}
