/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

public class Pausable extends Exception {
    private static final long serialVersionUID = 1L;
    private Pausable() {}
    private Pausable(String msg) {}

    
    public interface Spawn<TT> {
        TT execute() throws Pausable, Exception;
    }
    public interface Fork {
        void execute() throws Pausable, Exception;
    }
    public interface Fork1<AA> {
        void execute(AA arg1) throws Pausable, Exception;
    }

    public interface Pfun<XX,YY,EE extends Throwable> { YY apply(XX obj) throws Pausable, EE; }
    public interface Psumer<XX,EE extends Throwable> { void apply(XX obj) throws Pausable, EE; }

    public static <XX,YY,EE extends Throwable>
        YY chain(XX obj,Pfun<XX,YY,EE> function) throws Pausable, EE {
        return function.apply(obj);
    }
    public static <X1,X2,ZZ,E1 extends Throwable,E2 extends Throwable>
        ZZ chain(X1 obj,
            Pfun<X1,X2,E1> function1,
            Pfun<X2,ZZ,E2> function2) throws Pausable, E1, E2 {
        X2 obj2 = function1.apply(obj);
        return function2.apply(obj2);
    }
    public static <X1,X2,X3,X4,E1 extends Throwable,E2 extends Throwable,E3 extends Throwable>
        X4 chain(X1 obj,
            Pfun<X1,X2,E1> function1,
            Pfun<X2,X3,E2> function2,
            Pfun<X3,X4,E3> function3) throws Pausable, E1, E2, E3 {
        X2 obj2 = function1.apply(obj);
        X3 obj3 = function2.apply(obj2);
        return function3.apply(obj3);
    }

    
    public static <XX,EE extends Throwable> XX apply(XX obj,Psumer<XX,EE> func) throws Pausable, EE {
        func.apply(obj);
        return obj;
    }
    public static <XX,E1 extends Throwable,E2 extends Throwable>
        XX apply(XX obj,Psumer<XX,E1> func1,Psumer<XX,E2> func2) throws Pausable, E1, E2 {
        func1.apply(obj);
        func2.apply(obj);
        return obj;
    }
    public static <XX,E1 extends Throwable,E2 extends Throwable,E3 extends Throwable>
        XX apply(XX obj,
                Psumer<XX,E1> func1,
                Psumer<XX,E2> func2,
                Psumer<XX,E3> func3)
                throws Pausable, E1, E2, E3 {
        func1.apply(obj);
        func2.apply(obj);
        return obj;
    }
    public static <XX,EE extends Throwable> XX applyAll(XX obj,Psumer<XX,EE> ... funcs) throws Pausable, EE {
        for (Psumer<XX,EE> func : funcs)
            func.apply(obj);
        return obj;
    }

}

